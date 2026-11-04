package org.liberty.android.localreceiver

import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.DocumentsContract
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.liberty.android.localreceiver.databinding.ActivityShareReceiverBinding
import java.util.concurrent.atomic.AtomicBoolean

class ShareReceiverActivity : AppCompatActivity() {

    private lateinit var binding: ActivityShareReceiverBinding
    private lateinit var preferencesRepository: PreferencesRepository
    private var sharedFiles: List<SharedFileInfo> = emptyList()

    private var copyJob: Job? = null
    private val cancelRequested = AtomicBoolean(false)
    private var copiedItems: List<CopiedFileResult> = emptyList()
    private var currentState: DialogState = DialogState.CONFIRM

    private enum class DialogState {
        CONFIRM,
        COPYING,
        RESULT
    }

    private data class CopiedFileResult(
        val displayName: String,
        val destinationUri: Uri,
        val mimeType: String
    )

    private val openDocumentTreeLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            preferencesRepository.takePersistablePermission(treeUri)
            preferencesRepository.setCustomDirectoryUri(treeUri)
            refreshDestinationUi()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShareReceiverBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setFinishOnTouchOutside(false)

        preferencesRepository = PreferencesRepository(this)

        val uris = FileMetadataResolver.extractSharedUris(intent)
        if (uris.isEmpty()) {
            Toast.makeText(this, R.string.no_shared_files, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        sharedFiles = uris.map { uri ->
            FileMetadataResolver.resolve(contentResolver, uri)
        }

        populateSourceFilesUi()
        refreshDestinationUi()
        showState(DialogState.CONFIRM)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (currentState) {
                    DialogState.COPYING -> {
                        cancelRequested.set(true)
                        copyJob?.cancel()
                    }
                    else -> finish()
                }
            }
        })

        binding.btnConfirmChangeFolder.setOnClickListener {
            openDocumentTreeLauncher.launch(preferencesRepository.getCustomDirectoryUri())
        }

        binding.btnConfirmUseDownloads.setOnClickListener {
            preferencesRepository.clearCustomDirectory()
            refreshDestinationUi()
        }

        binding.btnCancelCopy.setOnClickListener {
            finish()
        }

        binding.btnConfirmCopy.setOnClickListener {
            val destInfo = preferencesRepository.getDestinationInfo()
            if (destInfo.isCustom && !destInfo.hasValidPermission) {
                Toast.makeText(
                    this,
                    "Please reselect the destination folder or tap Default.",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
            startForegroundCopy(destInfo)
        }

        binding.btnCancelProgress.setOnClickListener {
            cancelRequested.set(true)
            copyJob?.cancel()
        }

        binding.btnResultDone.setOnClickListener {
            finish()
        }

        binding.btnResultOpen.setOnClickListener {
            openCopiedResult()
        }
    }

    private fun startForegroundCopy(destInfo: DestinationInfo) {
        cancelRequested.set(false)
        showState(DialogState.COPYING)

        val totalFiles = sharedFiles.size
        binding.tvProgressDestination.text = "Destination: ${destInfo.displayName}"

        copyJob = lifecycleScope.launch {
            val succeeded = mutableListOf<CopiedFileResult>()
            val failed = mutableListOf<Pair<String, String>>()

            for ((index, fileInfo) in sharedFiles.withIndex()) {
                if (cancelRequested.get()) break

                val fileNumber = index + 1
                updateProgressUi(
                    fileName = fileInfo.displayName,
                    currentFileIndex = fileNumber,
                    totalFiles = totalFiles,
                    bytesCopied = 0L,
                    totalBytes = fileInfo.sizeBytes
                )

                var lastUiUpdateMs = 0L
                try {
                    val destUri = withContext(Dispatchers.IO) {
                        FileCopyEngine.copyFile(
                            contentResolver = contentResolver,
                            sourceFile = fileInfo,
                            customTreeUri = destInfo.customTreeUri,
                            isCancelled = { cancelRequested.get() },
                            onProgress = { bytesCopied, totalBytes ->
                                val now = SystemClock.elapsedRealtime()
                                val isDone = totalBytes > 0L && bytesCopied >= totalBytes
                                if (now - lastUiUpdateMs >= PROGRESS_UI_INTERVAL_MS || isDone) {
                                    lastUiUpdateMs = now
                                    runOnUiThread {
                                        if (currentState == DialogState.COPYING) {
                                            updateProgressUi(
                                                fileName = fileInfo.displayName,
                                                currentFileIndex = fileNumber,
                                                totalFiles = totalFiles,
                                                bytesCopied = bytesCopied,
                                                totalBytes = totalBytes
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }
                    succeeded.add(
                        CopiedFileResult(
                            displayName = fileInfo.displayName,
                            destinationUri = destUri,
                            mimeType = fileInfo.mimeType
                        )
                    )
                    withContext(Dispatchers.IO) {
                        CopyHistoryDatabase(this@ShareReceiverActivity).insertEntry(
                            CopyHistoryEntry(
                                timestamp = System.currentTimeMillis(),
                                sourceUri = fileInfo.uri.toString(),
                                sourceName = fileInfo.displayName,
                                destinationDir = destInfo.displayName,
                                destinationUri = destUri.toString(),
                                sizeBytes = fileInfo.sizeBytes,
                                mimeType = fileInfo.mimeType,
                                status = CopyHistoryEntry.Status.SUCCESS
                            )
                        )
                    }
                } catch (ce: CancellationException) {
                    cancelRequested.set(true)
                    withContext(Dispatchers.IO) {
                        val historyDb = CopyHistoryDatabase(this@ShareReceiverActivity)
                        historyDb.insertEntry(
                            CopyHistoryEntry(
                                timestamp = System.currentTimeMillis(),
                                sourceUri = fileInfo.uri.toString(),
                                sourceName = fileInfo.displayName,
                                destinationDir = destInfo.displayName,
                                destinationUri = null,
                                sizeBytes = fileInfo.sizeBytes,
                                mimeType = fileInfo.mimeType,
                                status = CopyHistoryEntry.Status.CANCELLED,
                                errorMessage = "Cancelled by user"
                            )
                        )
                        for (remIdx in (index + 1) until sharedFiles.size) {
                            val rem = sharedFiles[remIdx]
                            historyDb.insertEntry(
                                CopyHistoryEntry(
                                    timestamp = System.currentTimeMillis(),
                                    sourceUri = rem.uri.toString(),
                                    sourceName = rem.displayName,
                                    destinationDir = destInfo.displayName,
                                    destinationUri = null,
                                    sizeBytes = rem.sizeBytes,
                                    mimeType = rem.mimeType,
                                    status = CopyHistoryEntry.Status.CANCELLED,
                                    errorMessage = "Cancelled before start"
                                )
                            )
                        }
                    }
                    break
                } catch (t: Throwable) {
                    val errorMsg = t.message ?: t.javaClass.simpleName
                    failed.add(fileInfo.displayName to errorMsg)
                    withContext(Dispatchers.IO) {
                        CopyHistoryDatabase(this@ShareReceiverActivity).insertEntry(
                            CopyHistoryEntry(
                                timestamp = System.currentTimeMillis(),
                                sourceUri = fileInfo.uri.toString(),
                                sourceName = fileInfo.displayName,
                                destinationDir = destInfo.displayName,
                                destinationUri = null,
                                sizeBytes = fileInfo.sizeBytes,
                                mimeType = fileInfo.mimeType,
                                status = CopyHistoryEntry.Status.FAILURE,
                                errorMessage = errorMsg
                            )
                        )
                    }
                }
            }

            copiedItems = succeeded
            presentCopyResult(
                destInfo = destInfo,
                succeeded = succeeded,
                failed = failed,
                wasCancelled = cancelRequested.get()
            )
        }
    }

    private fun updateProgressUi(
        fileName: String,
        currentFileIndex: Int,
        totalFiles: Int,
        bytesCopied: Long,
        totalBytes: Long
    ) {
        binding.tvDialogTitle.text = if (totalFiles > 1) {
            getString(R.string.copying_multiple_title, currentFileIndex, totalFiles)
        } else {
            getString(R.string.copying_single_title)
        }
        binding.tvProgressFileName.text = fileName

        val hasKnownSize = totalBytes > 0L
        if (hasKnownSize) {
            val percent = ((bytesCopied * 100L) / totalBytes).toInt().coerceIn(0, 100)
            if (binding.progressBarCopy.isIndeterminate) {
                binding.progressBarCopy.isIndeterminate = false
            }
            binding.progressBarCopy.setProgressCompat(percent, true)
            binding.tvProgressDetails.text =
                "$percent% (${FileMetadataResolver.formatFileSize(bytesCopied)} / ${FileMetadataResolver.formatFileSize(totalBytes)})"
        } else {
            if (!binding.progressBarCopy.isIndeterminate) {
                binding.progressBarCopy.isIndeterminate = true
            }
            binding.tvProgressDetails.text =
                "${FileMetadataResolver.formatFileSize(bytesCopied)} copied"
        }
    }

    private fun presentCopyResult(
        destInfo: DestinationInfo,
        succeeded: List<CopiedFileResult>,
        failed: List<Pair<String, String>>,
        wasCancelled: Boolean
    ) {
        showState(DialogState.RESULT)

        val isSuccess = failed.isEmpty() && !wasCancelled && succeeded.isNotEmpty()
        binding.tvDialogTitle.text = when {
            wasCancelled -> getString(R.string.copy_cancelled_title)
            isSuccess -> getString(R.string.copy_success_title)
            else -> getString(R.string.copy_failure_title)
        }

        val sb = StringBuilder()
        sb.append("Destination: ").append(destInfo.displayName)
        if (destInfo.details.isNotBlank()) {
            sb.append(" (").append(destInfo.details).append(")")
        }

        if (wasCancelled) {
            sb.append("\n\nCopy operation was cancelled by user.")
        }

        if (succeeded.isNotEmpty()) {
            sb.append("\n\nCopied (${succeeded.size}):")
            succeeded.forEach { item ->
                sb.append("\n• ").append(item.displayName)
            }
        }

        if (failed.isNotEmpty()) {
            sb.append("\n\nError Details (${failed.size} failed):")
            failed.forEach { (name, reason) ->
                sb.append("\n• ").append(name).append(": ").append(reason)
            }
        }

        binding.tvResultDetails.text = sb.toString()
        binding.btnResultOpen.visibility = if (isSuccess) View.VISIBLE else View.GONE
    }

    private fun openCopiedResult() {
        val destInfo = preferencesRepository.getDestinationInfo()
        val firstCopied = copiedItems.firstOrNull() ?: return

        val opened = if (copiedItems.size == 1) {
            tryOpenSingleFile(firstCopied) || tryOpenDestinationFolder(destInfo)
        } else {
            tryOpenDestinationFolder(destInfo) || tryOpenSingleFile(firstCopied)
        }

        if (opened) {
            finish()
        } else {
            Toast.makeText(this, R.string.cannot_open_file, Toast.LENGTH_SHORT).show()
        }
    }

    private fun tryOpenSingleFile(item: CopiedFileResult): Boolean {
        val viewFileIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(item.destinationUri, item.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            startActivity(Intent.createChooser(viewFileIntent, getString(R.string.btn_open)))
            true
        }.getOrDefault(false)
    }

    private fun tryOpenDestinationFolder(destInfo: DestinationInfo): Boolean {
        val treeUri = destInfo.customTreeUri
        if (treeUri != null) {
            val docId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            if (docId != null) {
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                val folderIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(docUri, DocumentsContract.Document.MIME_TYPE_DIR)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (runCatching { startActivity(folderIntent); true }.getOrDefault(false)) {
                    return true
                }
            }
        }

        val downloadsIntent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            startActivity(downloadsIntent)
            true
        }.getOrDefault(false)
    }

    private fun showState(state: DialogState) {
        currentState = state
        binding.layoutConfirmSection.visibility =
            if (state == DialogState.CONFIRM) View.VISIBLE else View.GONE
        binding.layoutProgressSection.visibility =
            if (state == DialogState.COPYING) View.VISIBLE else View.GONE
        binding.layoutResultSection.visibility =
            if (state == DialogState.RESULT) View.VISIBLE else View.GONE

        if (state == DialogState.CONFIRM) {
            binding.tvDialogTitle.setText(R.string.confirm_copy_title)
        }
    }

    private fun populateSourceFilesUi() {
        val count = sharedFiles.size
        val knownSizes = sharedFiles.filter { it.sizeBytes >= 0L }
        val totalKnownBytes = knownSizes.sumOf { it.sizeBytes }
        val totalSizeLabel = if (knownSizes.size == count) {
            FileMetadataResolver.formatFileSize(totalKnownBytes)
        } else {
            "Unknown total size"
        }

        binding.tvSourceFilesLabel.text = if (count == 1) {
            "Source File (1 file • $totalSizeLabel)"
        } else {
            "Source Files ($count files • $totalSizeLabel)"
        }

        val detailsBuilder = StringBuilder()
        sharedFiles.forEachIndexed { index, info ->
            if (index > 0) detailsBuilder.append("\n\n")
            detailsBuilder.append("• ").append(info.displayName)
            detailsBuilder.append(" (").append(FileMetadataResolver.formatFileSize(info.sizeBytes)).append(")")
            detailsBuilder.append("\n  From: ").append(info.uri)
        }
        binding.tvSourceFilesList.text = detailsBuilder.toString()
    }

    private fun refreshDestinationUi() {
        val destInfo = preferencesRepository.getDestinationInfo()
        binding.tvConfirmDestinationName.text = destInfo.displayName
        binding.tvConfirmDestinationDetails.text = destInfo.details
        binding.btnConfirmUseDownloads.isEnabled = destInfo.isCustom
    }

    companion object {
        private const val PROGRESS_UI_INTERVAL_MS = 100L
    }
}

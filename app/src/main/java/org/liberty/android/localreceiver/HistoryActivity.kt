package org.liberty.android.localreceiver

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.liberty.android.localreceiver.databinding.ActivityHistoryBinding

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var historyDb: CopyHistoryDatabase
    private lateinit var adapter: CopyHistoryAdapter
    private var currentEntries: List<CopyHistoryEntry> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        historyDb = CopyHistoryDatabase(this)

        setupToolbar()
        setupRecyclerView()
        loadHistory()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_history, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clear_all -> {
                promptClearAllHistory()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupRecyclerView() {
        adapter = CopyHistoryAdapter(
            onItemClick = { entry ->
                showItemOptionsDialog(entry)
            },
            onQuickOpenClick = { entry ->
                openFile(entry)
            }
        )
        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = adapter
    }

    private fun loadHistory() {
        binding.progressBarLoading.visibility = View.VISIBLE
        lifecycleScope.launch {
            val entries = withContext(Dispatchers.IO) {
                historyDb.getAllEntries()
            }
            currentEntries = entries
            binding.progressBarLoading.visibility = View.GONE
            adapter.submitList(entries)

            if (entries.isEmpty()) {
                binding.rvHistory.visibility = View.GONE
                binding.layoutEmptyHistory.visibility = View.VISIBLE
            } else {
                binding.rvHistory.visibility = View.VISIBLE
                binding.layoutEmptyHistory.visibility = View.GONE
            }
        }
    }

    private fun showItemOptionsDialog(entry: CopyHistoryEntry) {
        val options = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        val canOpenFile = entry.status == CopyHistoryEntry.Status.SUCCESS && entry.destinationUri != null
        if (canOpenFile) {
            options.add(getString(R.string.history_action_open))
            actions.add { openFile(entry) }

            options.add(getString(R.string.history_action_delete_file))
            actions.add { confirmDeleteFile(entry) }
        }

        options.add(getString(R.string.history_action_remove_entry))
        actions.add { removeEntry(entry) }

        MaterialAlertDialogBuilder(this)
            .setTitle(entry.sourceName)
            .setItems(options.toTypedArray()) { _, which ->
                actions[which].invoke()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun openFile(entry: CopyHistoryEntry) {
        val destUriStr = entry.destinationUri
        if (destUriStr.isNullOrBlank()) {
            Toast.makeText(this, R.string.cannot_open_file, Toast.LENGTH_SHORT).show()
            return
        }

        val destUri = Uri.parse(destUriStr)
        val mimeType = entry.mimeType ?: "*/*"
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(destUri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val launched = runCatching {
            startActivity(Intent.createChooser(viewIntent, getString(R.string.btn_open)))
            true
        }.getOrDefault(false)

        if (!launched) {
            Toast.makeText(this, R.string.cannot_open_file, Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDeleteFile(entry: CopyHistoryEntry) {
        val destUriStr = entry.destinationUri ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_delete_file_title)
            .setMessage(getString(R.string.dialog_delete_file_message, entry.sourceName))
            .setPositiveButton(R.string.btn_delete) { _, _ ->
                lifecycleScope.launch {
                    val deleted = withContext(Dispatchers.IO) {
                        FileDeleter.deleteFile(this@HistoryActivity, destUriStr)
                    }
                    if (deleted) {
                        withContext(Dispatchers.IO) {
                            historyDb.updateDestinationUri(entry.id, null)
                        }
                        loadHistory()
                        Toast.makeText(
                            this@HistoryActivity,
                            R.string.file_deleted_success,
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            this@HistoryActivity,
                            R.string.file_delete_failed,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun removeEntry(entry: CopyHistoryEntry) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                historyDb.deleteEntry(entry.id)
            }
            loadHistory()
            Toast.makeText(
                this@HistoryActivity,
                R.string.history_entry_removed,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun promptClearAllHistory() {
        if (currentEntries.isEmpty()) {
            Toast.makeText(this, R.string.history_already_empty, Toast.LENGTH_SHORT).show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_clear_all_title)
            .setMessage(R.string.dialog_clear_all_message)
            .setPositiveButton(R.string.btn_clear_all) { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        historyDb.clearAll()
                    }
                    loadHistory()
                    Toast.makeText(
                        this@HistoryActivity,
                        R.string.history_cleared_success,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }
}

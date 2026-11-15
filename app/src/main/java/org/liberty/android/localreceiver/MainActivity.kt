package org.liberty.android.localreceiver

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.util.LinkifyCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.liberty.android.localreceiver.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var preferencesRepository: PreferencesRepository

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
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferencesRepository = PreferencesRepository(this)

        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_about -> {
                    showAboutDialog()
                    true
                }
                else -> false
            }
        }

        binding.btnSelectCustomDir.setOnClickListener {
            openDocumentTreeLauncher.launch(preferencesRepository.getCustomDirectoryUri())
        }

        binding.btnResetDefaultDir.setOnClickListener {
            preferencesRepository.clearCustomDirectory()
            refreshDestinationUi()
        }

        binding.btnViewHistory.setOnClickListener {
            navigateToHistory()
        }

        binding.cardHistory.setOnClickListener {
            navigateToHistory()
        }

        refreshDestinationUi()
    }

    override fun onResume() {
        super.onResume()
        refreshDestinationUi()
    }

    private fun navigateToHistory() {
        startActivity(Intent(this, HistoryActivity::class.java))
    }

    private fun showAboutDialog() {
        val versionText = runCatching {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            val versionName = packageInfo.versionName ?: "1.0"
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            "$versionName ($versionCode)"
        }.getOrDefault("1.0 (1)")

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.about_dialog_title)
            .setMessage(getString(R.string.about_dialog_message, versionText))
            .setPositiveButton(R.string.btn_ok, null)
            .show()

        dialog.findViewById<TextView>(android.R.id.message)?.apply {
            LinkifyCompat.addLinks(this, Linkify.WEB_URLS)
            movementMethod = LinkMovementMethod.getInstance()
        }
    }

    private fun refreshDestinationUi() {
        val destInfo = preferencesRepository.getDestinationInfo()
        binding.tvDestinationName.text = destInfo.displayName
        binding.tvDestinationDetails.text = destInfo.details
        binding.btnResetDefaultDir.isEnabled = destInfo.isCustom
    }
}

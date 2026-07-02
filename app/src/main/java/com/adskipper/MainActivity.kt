package com.adskipper

import android.content.*
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.adskipper.ui.screen.*
import com.adskipper.ui.theme.AdSkipperTheme
import com.adskipper.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var recordingReceiver: BroadcastReceiver? = null

    private val accessibilityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshServiceStatus()
    }

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportRulesToFile(uri)
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            showImportMergeDialog(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Register recording broadcast receiver
        registerRecordingReceiver()

        // Handle intent from file opening
        handleIntent(intent)

        setContent {
            AdSkipperTheme {
                AdSkipperApp(
                    viewModel = viewModel,
                    onOpenAccessibilitySettings = { openAccessibilitySettings() },
                    onExportRules = { exportLauncher.launch(generateExportFileName()) },
                    onImportRules = { importLauncher.launch(arrayOf("application/json")) },
                    onRequestOverlayPermission = { requestOverlayPermission() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW || intent?.action == Intent.ACTION_SEND) {
            val uri = when (intent.action) {
                Intent.ACTION_VIEW -> intent.data
                Intent.ACTION_SEND -> intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                else -> null
            }
            uri?.let {
                showImportMergeDialog(it)
            }
        }
    }

    private fun showImportMergeDialog(uri: Uri) {
        // This will be handled by a dialog in the Compose UI
        // For now, just import with merge
        viewModel.importRulesFromFile(uri, merge = true)
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        accessibilityLauncher.launch(intent)
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun generateExportFileName(): String {
        val dateFormat = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
        return "adskipper_rules_${dateFormat.format(java.util.Date())}.json"
    }

    private fun registerRecordingReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    "com.adskipper.ACTION_NODE_CAPTURED" -> {
                        val error = intent.getStringExtra("error")
                        if (error != null) {
                            android.widget.Toast.makeText(this@MainActivity, error, android.widget.Toast.LENGTH_LONG).show()
                            return
                        }
                        val text = intent.getStringExtra("node_text") ?: ""
                        val viewId = intent.getStringExtra("node_id") ?: ""
                        val className = intent.getStringExtra("node_class") ?: ""
                        val bounds = intent.getStringExtra("node_bounds") ?: ""
                        val desc = intent.getStringExtra("node_desc") ?: ""
                        val pkg = intent.getStringExtra("node_package") ?: ""

                        // Create a rule from captured node
                        val rule = com.adskipper.data.RuleEntity(
                            name = "录制规则 - ${pkg.split(".").lastOrNull() ?: pkg}",
                            packageName = pkg,
                            useText = text.isNotBlank(),
                            targetText = text,
                            useId = viewId.isNotBlank(),
                            targetId = viewId,
                            useClassName = className.isNotBlank(),
                            targetClassName = className,
                            useContentDesc = desc.isNotBlank(),
                            targetContentDesc = desc
                        )
                        viewModel.addRule(rule)
                        android.widget.Toast.makeText(this@MainActivity, "规则已创建！", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    "com.adskipper.ACTION_SNACKBAR" -> {
                        val msg = intent.getStringExtra("message") ?: return
                        android.widget.Toast.makeText(this@MainActivity, msg, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction("com.adskipper.ACTION_NODE_CAPTURED")
            addAction("com.adskipper.ACTION_SNACKBAR")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
        recordingReceiver = receiver
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            recordingReceiver?.let { unregisterReceiver(it) }
        } catch (_: Exception) {}
    }
}
package com.disktree.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File

class MainActivity : ComponentActivity() {
    private val viewModel: DiskTreeViewModel by viewModels()
    private var hasStorageAccess by mutableStateOf(false)
    private var pendingExport by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        hasStorageAccess = storageAccessGranted()
    }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        hasStorageAccess = storageAccessGranted()
    }

    private val exportPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        pendingExport = false
        if (granted) viewModel.exportReport()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hasStorageAccess = storageAccessGranted()
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            DiskTreeTheme {
                DiskTreeScreen(
                    state = state,
                    hasStorageAccess = hasStorageAccess,
                    selectedNode = viewModel.selectedNode(),
                    onSelectScope = viewModel::selectScope,
                    onSelectSizeMode = viewModel::selectSizeMode,
                    onScan = { viewModel.scan(hasStorageAccess) },
                    onCancelScan = viewModel::cancelScan,
                    onSelectNode = viewModel::selectNode,
                    onRequestStorageAccess = ::requestStorageAccess,
                    onCheckRootAccess = viewModel::checkRootAccess,
                    onListMode = viewModel::setListMode,
                    onSortMode = viewModel::setSortMode,
                    onQueryChange = viewModel::setQuery,
                    onRevealPath = viewModel::revealPath,
                    onOpenPath = ::openPath,
                    onCopyPath = ::copyPath,
                    onExport = ::requestExport,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hasStorageAccess = storageAccessGranted()
    }

    private fun requestStorageAccess() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                val appIntent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.fromParts("package", packageName, null),
                )
                val intent = if (appIntent.resolveActivity(packageManager) != null) {
                    appIntent
                } else {
                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                }
                settingsLauncher.launch(intent)
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    private fun requestExport() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            viewModel.exportReport()
            return
        }
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            viewModel.exportReport()
            return
        }
        pendingExport = true
        exportPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    private fun copyPath(path: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("DiskTree path", path))
    }

    private fun openPath(path: String) {
        val file = File(path)
        if (!file.exists() || file.isDirectory) return
        val uri = try {
            FileProvider.getUriForFile(this, "$packageName.files", file)
        } catch (_: IllegalArgumentException) {
            return
        }
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
            ?: "application/octet-stream"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(intent) }
    }

    private fun storageAccessGranted(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
}

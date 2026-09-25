package com.disktree.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val viewModel: DiskTreeViewModel by viewModels()
    private var hasStorageAccess by mutableStateOf(false)

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
                    onSelectScope = viewModel::selectScope,
                    onScan = { viewModel.scan(hasStorageAccess) },
                    onCancelScan = viewModel::cancelScan,
                    onSelectNode = viewModel::selectNode,
                    onRequestStorageAccess = ::requestStorageAccess,
                    onCheckRootAccess = viewModel::checkRootAccess,
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

    private fun storageAccessGranted(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
}

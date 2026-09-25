package com.disktree.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DiskTreeScreen(
    state: DiskTreeUiState,
    hasStorageAccess: Boolean,
    onSelectScope: (ScanScope) -> Unit,
    onSelectSizeMode: (SizeMode) -> Unit,
    onScan: () -> Unit,
    onCancelScan: () -> Unit,
    onSelectNode: (ScanNode) -> Unit,
    onRequestStorageAccess: () -> Unit,
    onCheckRootAccess: () -> Unit,
) {
    val scanning = state.phase == ScanPhase.Scanning
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("DiskTree", fontWeight = FontWeight.SemiBold)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            ScanActionBar(
                scope = state.scope,
                phase = state.phase,
                sizeMode = state.sizeMode,
                rootAccess = state.rootAccess,
                hasStorageAccess = hasStorageAccess,
                onScan = onScan,
                onCancelScan = onCancelScan,
                onRequestStorageAccess = onRequestStorageAccess,
                onCheckRootAccess = onCheckRootAccess,
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxSize(),
            ) {
                ScopeSelector(
                    selected = state.scope,
                    enabled = !scanning,
                    onSelect = onSelectScope,
                )
                SizeModeSelector(
                    selected = state.sizeMode,
                    enabled = !scanning,
                    onSelect = onSelectSizeMode,
                )
                StorageSummary(state.capacity)

                when {
                    scanning -> ScanningState(Modifier.weight(1f), state.progress, state.scope)
                    state.scope == ScanScope.ROOT_DEVICE && state.rootAccess != RootAccess.AVAILABLE -> RootAccessState(
                        modifier = Modifier.weight(1f),
                        access = state.rootAccess,
                    )
                    !hasStorageAccess && state.scope == ScanScope.SHARED_STORAGE -> AccessState(Modifier.weight(1f))
                    state.phase is ScanPhase.Failed -> ErrorState(
                        modifier = Modifier.weight(1f),
                        message = (state.phase as ScanPhase.Failed).message,
                        onUseSharedStorage = {
                            onSelectScope(ScanScope.SHARED_STORAGE)
                        },
                        showSharedStorageAction = state.scope == ScanScope.ROOT_DEVICE,
                    )

                    state.phase == ScanPhase.Complete && state.root != null -> ResultTree(
                        modifier = Modifier.weight(1f),
                        sizeMode = state.sizeMode,
                        root = state.root,
                        expandedPaths = state.expandedPaths,
                        selectedPath = state.selectedPath,
                        warning = state.warning,
                        onSelectNode = onSelectNode,
                    )

                    else -> IdleState(Modifier.weight(1f), state.scope)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScopeSelector(
    selected: ScanScope,
    enabled: Boolean,
    onSelect: (ScanScope) -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FilterChip(
            selected = selected == ScanScope.SHARED_STORAGE,
            onClick = { onSelect(ScanScope.SHARED_STORAGE) },
            enabled = enabled,
            label = { Text("Shared storage") },
            leadingIcon = { Icon(Icons.Outlined.Storage, contentDescription = null) },
        )
        FilterChip(
            selected = selected == ScanScope.ROOT_DEVICE,
            onClick = { onSelect(ScanScope.ROOT_DEVICE) },
            enabled = enabled,
            label = { Text("Root device") },
            leadingIcon = { Icon(Icons.Outlined.Shield, contentDescription = null) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SizeModeSelector(
    selected: SizeMode,
    enabled: Boolean,
    onSelect: (SizeMode) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text("Size view", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FilterChip(
                selected = selected == SizeMode.LOGICAL,
                onClick = { onSelect(SizeMode.LOGICAL) },
                enabled = enabled,
                label = { Text("File size") },
                leadingIcon = { Icon(Icons.Outlined.Description, contentDescription = null) },
            )
            FilterChip(
                selected = selected == SizeMode.ALLOCATED,
                onClick = { onSelect(SizeMode.ALLOCATED) },
                enabled = enabled,
                label = { Text("Disk usage") },
                leadingIcon = { Icon(Icons.Outlined.Storage, contentDescription = null) },
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "File size matches file managers. Disk usage shows allocated blocks.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StorageSummary(capacity: StorageCapacity?) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (capacity == null || capacity.totalBytes <= 0L) {
                Text("Storage volume unavailable", style = MaterialTheme.typography.titleMedium)
            } else {
                val usedFraction = capacity.usedBytes.toFloat() / capacity.totalBytes.toFloat()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    StorageStat("Used", formatBytes(capacity.usedBytes))
                    StorageStat("Free", formatBytes(capacity.freeBytes))
                }
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { usedFraction.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
                )
            }
        }
    }
}

@Composable
private fun StorageStat(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun IdleState(modifier: Modifier, scope: ScanScope) {
    StatePanel(
        modifier = modifier,
        title = "No scan yet",
        message = if (scope == ScanScope.ROOT_DEVICE) {
            "DiskTree will ask your root manager for permission, then scan the device data partition."
        } else {
            "Scan shared storage to sort folders and files by size."
        },
    )
}

@Composable
private fun AccessState(modifier: Modifier) {
    StatePanel(
        modifier = modifier,
        title = "File access needed",
        message = "Grant all files access to analyze shared storage. Android 11 and newer may hide some app folders from standard access.",
    )
}

@Composable
private fun RootAccessState(modifier: Modifier, access: RootAccess) {
    val title = when (access) {
        RootAccess.CHECKING -> "Checking root access"
        RootAccess.UNAVAILABLE -> "Root access unavailable"
        RootAccess.UNKNOWN -> "Root access not checked"
        RootAccess.AVAILABLE -> "Root access available"
    }
    val message = when (access) {
        RootAccess.CHECKING -> "Checking whether the root manager can provide su access."
        RootAccess.UNAVAILABLE -> "Open your root manager, enable DiskTree, then check again."
        RootAccess.UNKNOWN -> "Check root access before scanning the device data partition."
        RootAccess.AVAILABLE -> "Root access is available."
    }
    StatePanel(modifier = modifier, title = title, message = message)
}

@Composable
private fun ScanningState(modifier: Modifier, progress: ScanProgress, scope: ScanScope) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (scope == ScanScope.ROOT_DEVICE) "Scanning device data" else "Scanning shared storage",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Text(
            text = "${formatInteger(progress.entryCount)} entries, ${formatBytes(progress.scannedBytes)}",
            style = MaterialTheme.typography.titleMedium,
        )
        if (progress.currentPath.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = progress.currentPath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorState(
    modifier: Modifier,
    message: String,
    onUseSharedStorage: () -> Unit,
    showSharedStorageAction: Boolean,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Scan failed",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (showSharedStorageAction) {
            Spacer(Modifier.height(20.dp))
            OutlinedButton(
                onClick = onUseSharedStorage,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("Use shared storage")
            }
        }
    }
}

@Composable
private fun StatePanel(
    modifier: Modifier,
    title: String,
    message: String,
) {
    Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ResultTree(
    modifier: Modifier,
    sizeMode: SizeMode,
    root: ScanNode,
    expandedPaths: Set<String>,
    selectedPath: String?,
    warning: String?,
    onSelectNode: (ScanNode) -> Unit,
) {
    val visibleNodes = remember(root, expandedPaths) { flattenTree(root, expandedPaths) }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (sizeMode == SizeMode.LOGICAL) "Largest by file size" else "Largest by disk usage",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = root.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = formatBytes(root.sizeBytes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (warning != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    text = warning,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (selectedPath != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    text = "Selected path",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = selectedPath, style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
        ) {
            items(visibleNodes, key = { it.node.path }) { visible ->
                StorageTreeRow(
                    visible = visible,
                    expanded = expandedPaths.contains(visible.node.path),
                    selected = selectedPath == visible.node.path,
                    onClick = { onSelectNode(visible.node) },
                )
            }
        }
    }
}

@Composable
private fun StorageTreeRow(
    visible: VisibleNode,
    expanded: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val node = visible.node
    val containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val description = buildString {
        append(node.name)
        append(", ")
        append(if (node.isDirectory) "folder" else "file")
        append(", ")
        append(formatBytes(node.sizeBytes))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(containerColor)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                if (node.isDirectory) {
                    stateDescription = if (expanded) "Expanded" else "Collapsed"
                }
            }
            .padding(
                start = 12.dp + (visible.depth.coerceAtMost(5) * 14).dp,
                end = 16.dp,
                top = 10.dp,
                bottom = 10.dp,
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = when {
                    !node.isDirectory -> Icons.Outlined.Description
                    expanded -> Icons.Outlined.ExpandMore
                    else -> Icons.Outlined.ChevronRight
                },
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.size(10.dp))
            Text(
                text = node.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
                fontWeight = if (visible.depth == 0) FontWeight.SemiBold else FontWeight.Normal,
            )
            Spacer(Modifier.size(12.dp))
            Text(
                text = formatBytes(node.sizeBytes),
                modifier = Modifier.widthIn(max = 150.dp),
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                textAlign = TextAlign.End,
            )
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { visible.share.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.24f),
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
}

@Composable
private fun ScanActionBar(
    scope: ScanScope,
    phase: ScanPhase,
    sizeMode: SizeMode,
    rootAccess: RootAccess,
    hasStorageAccess: Boolean,
    onScan: () -> Unit,
    onCancelScan: () -> Unit,
    onRequestStorageAccess: () -> Unit,
    onCheckRootAccess: () -> Unit,
) {
    val scanning = phase == ScanPhase.Scanning
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (scanning) {
                OutlinedButton(
                    onClick = onCancelScan,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                ) {
                    Icon(Icons.Outlined.StopCircle, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Cancel scan")
                }
            } else {
                val needsPermission = scope == ScanScope.SHARED_STORAGE && !hasStorageAccess
                val needsRootCheck = scope == ScanScope.ROOT_DEVICE && rootAccess != RootAccess.AVAILABLE
                val checkingRoot = scope == ScanScope.ROOT_DEVICE && rootAccess == RootAccess.CHECKING
                val complete = phase == ScanPhase.Complete
                val label = when {
                    checkingRoot -> "Checking root access"
                    needsRootCheck -> "Check root access"
                    needsPermission -> "Grant file access"
                    complete -> "Scan again"
                    scope == ScanScope.ROOT_DEVICE -> "Scan with root"
                    sizeMode == SizeMode.LOGICAL -> "Scan file sizes"
                    else -> "Scan disk usage"
                }
                val icon = when {
                    needsRootCheck -> Icons.Outlined.Shield
                    needsPermission -> Icons.Outlined.LockOpen
                    complete -> Icons.Outlined.Refresh
                    scope == ScanScope.ROOT_DEVICE -> Icons.Outlined.Shield
                    else -> Icons.Outlined.Storage
                }
                val action = when {
                    needsRootCheck -> onCheckRootAccess
                    needsPermission -> onRequestStorageAccess
                    else -> onScan
                }
                Button(
                    onClick = action,
                    enabled = !checkingRoot,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                ) {
                    Icon(icon, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(label)
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1_000L) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB", "PB")
    var value = bytes.toDouble()
    var unitIndex = -1
    while (value >= 1_000.0 && unitIndex < units.lastIndex) {
        value /= 1_000.0
        unitIndex++
    }
    val pattern = if (value < 10.0) "%.1f %s" else "%.0f %s"
    return String.format(Locale.getDefault(), pattern, value, units[unitIndex])
}

private fun formatInteger(value: Long): String = NumberFormat.getIntegerInstance().format(value)

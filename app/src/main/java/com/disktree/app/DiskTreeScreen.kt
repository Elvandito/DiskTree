package com.disktree.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import kotlin.math.roundToInt

private val MeterTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontFeatureSettings = "tnum",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiskTreeScreen(
    state: DiskTreeUiState,
    hasStorageAccess: Boolean,
    selectedNode: ScanNode?,
    onSelectScope: (ScanScope) -> Unit,
    onSelectSizeMode: (SizeMode) -> Unit,
    onScan: () -> Unit,
    onCancelScan: () -> Unit,
    onSelectNode: (ScanNode) -> Unit,
    onRequestStorageAccess: () -> Unit,
    onCheckRootAccess: () -> Unit,
    onListMode: (ListMode) -> Unit,
    onSortMode: (SortMode) -> Unit,
    onQueryChange: (String) -> Unit,
    onRevealPath: (String) -> Unit,
    onOpenPath: (String) -> Unit,
    onCopyPath: (String) -> Unit,
    onExport: () -> Unit,
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
                scanCooldown = state.scanCooldown,
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
                StorageSummary(
                    capacity = state.capacity,
                    label = if (state.scope == ScanScope.ROOT_DEVICE) "/data" else "Shared storage",
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
                        onUseSharedStorage = { onSelectScope(ScanScope.SHARED_STORAGE) },
                        showSharedStorageAction = state.scope == ScanScope.ROOT_DEVICE,
                    )

                    state.phase == ScanPhase.Complete && state.root != null -> ResultTree(
                        modifier = Modifier.weight(1f),
                        sizeMode = state.sizeMode,
                        listMode = state.listMode,
                        sortMode = state.sortMode,
                        query = state.query,
                        root = state.root,
                        expandedPaths = state.expandedPaths,
                        selectedPath = state.selectedPath,
                        selectedNode = selectedNode,
                        warning = state.warning,
                        notice = state.notice,
                        onSelectNode = onSelectNode,
                        onListMode = onListMode,
                        onSortMode = onSortMode,
                        onQueryChange = onQueryChange,
                        onRevealPath = onRevealPath,
                        onOpenPath = onOpenPath,
                        onCopyPath = onCopyPath,
                        onExport = onExport,
                    )

                    else -> IdleState(Modifier.weight(1f), state.scope)
                }
            }
        }
    }
}

private data class SegmentOption<T>(
    val value: T,
    val label: String,
    val icon: ImageVector,
)

@Composable
private fun ScopeSelector(
    selected: ScanScope,
    enabled: Boolean,
    onSelect: (ScanScope) -> Unit,
) {
    SegmentedSelector(
        label = "Scan scope",
        options = listOf(
            SegmentOption(ScanScope.SHARED_STORAGE, "Shared storage", Icons.Outlined.Storage),
            SegmentOption(ScanScope.ROOT_DEVICE, "Root device", Icons.Outlined.Shield),
        ),
        selected = selected,
        enabled = enabled,
        onSelect = onSelect,
    )
}

@Composable
private fun SizeModeSelector(
    selected: SizeMode,
    enabled: Boolean,
    onSelect: (SizeMode) -> Unit,
) {
    SegmentedSelector(
        label = "Size view",
        supporting = "File size is logical bytes. Disk usage is allocated blocks.",
        options = listOf(
            SegmentOption(SizeMode.LOGICAL, "File size", Icons.Outlined.Description),
            SegmentOption(SizeMode.ALLOCATED, "Disk usage", Icons.Outlined.Storage),
        ),
        selected = selected,
        enabled = enabled,
        onSelect = onSelect,
    )
}

@Composable
private fun <T> SegmentedSelector(
    label: String?,
    options: List<SegmentOption<T>>,
    selected: T,
    enabled: Boolean,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    val shape = MaterialTheme.shapes.medium
    val idleColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.5f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp),
    ) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        ) {
            options.forEachIndexed { index, option ->
                val active = option.value == selected
                if (index > 0) {
                    Box(
                        Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                }
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RectangleShape,
                    color = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else idleColor,
                ) {
                    Row(
                        modifier = Modifier
                            .selectable(
                                selected = active,
                                enabled = enabled,
                                role = Role.RadioButton,
                                onClick = { onSelect(option.value) },
                            )
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = option.icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (supporting != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StorageSummary(capacity: StorageCapacity?, label: String) {
    val total = capacity?.totalBytes ?: 0L
    if (capacity == null || total <= 0L) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Storage volume unavailable",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        return
    }

    val used = (capacity.usedBytes.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column {
                Text(
                    text = "Used",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                MeterText(text = formatBytes(capacity.usedBytes), style = MaterialTheme.typography.titleLarge)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Free",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                MeterText(text = formatBytes(capacity.freeBytes), style = MaterialTheme.typography.titleLarge)
            }
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { used },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outlineVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "$label · ${formatPercent(used)} of ${formatBytes(total)} total",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IdleState(modifier: Modifier, scope: ScanScope) {
    StatePanel(
        modifier = modifier,
        icon = Icons.Outlined.Storage,
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
        icon = Icons.Outlined.LockOpen,
        title = "File access needed",
        message = "Grant all files access to analyze shared storage. Android 11 and newer hide app folders from standard access.",
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
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 420.dp),
        ) {
            if (access == RootAccess.CHECKING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                )
            } else {
                IconBadge(Icons.Outlined.Shield)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ScanningState(modifier: Modifier, progress: ScanProgress, scope: ScanScope) {
    val rootScan = scope == ScanScope.ROOT_DEVICE
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (rootScan) "Scanning device data" else "Scanning shared storage",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (rootScan) "/data" else "External storage",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outlineVariant,
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            StatCell(label = "Entries", value = formatInteger(progress.entryCount))
            if (progress.scannedBytes > 0L) {
                StatCell(label = "Scanned", value = formatBytes(progress.scannedBytes))
            }
        }
        if (progress.currentPath.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Current path",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            MeterText(
                text = progress.currentPath,
                style = MaterialTheme.typography.bodySmall,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StatCell(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        MeterText(text = value, style = MaterialTheme.typography.titleMedium)
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
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(52.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
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
            style = MaterialTheme.typography.bodyMedium,
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
    icon: ImageVector,
    title: String,
    message: String,
) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 420.dp),
        ) {
            IconBadge(icon)
            Spacer(Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun IconBadge(icon: ImageVector) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.size(52.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

@Composable
private fun ResultTree(
    modifier: Modifier,
    sizeMode: SizeMode,
    listMode: ListMode,
    sortMode: SortMode,
    query: String,
    root: ScanNode,
    expandedPaths: Set<String>,
    selectedPath: String?,
    selectedNode: ScanNode?,
    warning: String?,
    notice: String?,
    onSelectNode: (ScanNode) -> Unit,
    onListMode: (ListMode) -> Unit,
    onSortMode: (SortMode) -> Unit,
    onQueryChange: (String) -> Unit,
    onRevealPath: (String) -> Unit,
    onOpenPath: (String) -> Unit,
    onCopyPath: (String) -> Unit,
    onExport: () -> Unit,
) {
    val filtering = query.isNotBlank()
    val shownRoot = remember(root, query) {
        val filtered = filterTree(root, query)
        (filtered ?: root).copy(sizeBytes = root.sizeBytes)
    }
    val activeExpanded = remember(shownRoot, expandedPaths, filtering) {
        if (filtering) allExpandablePaths(shownRoot) else expandedPaths
    }
    val insights = remember(shownRoot) { buildInsights(shownRoot) }
    val matchCount = remember(shownRoot, filtering) {
        if (filtering) allNodes(shownRoot).count { it.path != shownRoot.path } else 0
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (sizeMode == SizeMode.LOGICAL) "Largest by file size" else "Largest by disk usage",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                MeterText(
                    text = root.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                MeterText(text = formatBytes(root.sizeBytes), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "total",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onExport, modifier = Modifier.heightIn(min = 44.dp)) {
                Text("Export CSV")
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ResultToolbar(
            listMode = listMode,
            sortMode = sortMode,
            onListMode = onListMode,
            onSortMode = onSortMode,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        SearchField(
            query = query,
            onQueryChange = onQueryChange,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        if (warning != null) {
            NoticeBand(text = warning)
        }
        if (notice != null) {
            NoticeBand(text = notice)
        }
        if (insights.isNotEmpty() && !filtering) {
            SuggestionsPanel(insights = insights, onInsightClick = onInsightClick(onListMode, onRevealPath))
        }
        if (selectedNode != null) {
            SelectionStrip(
                node = selectedNode,
                onCopyPath = { onCopyPath(selectedNode.path) },
                onOpen = { onOpenPath(selectedNode.path) },
            )
        }
        if (filtering) {
            Text(
                text = "$matchCount matches for \"$query\"",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 10.dp),
            )
        }
        when {
            filtering -> TreeList(
                modifier = Modifier.weight(1f),
                root = shownRoot,
                expandedPaths = activeExpanded,
                selectedPath = selectedPath,
                onSelectNode = onSelectNode,
            )

            listMode == ListMode.LARGEST -> LargestFilesList(
                modifier = Modifier.weight(1f),
                files = remember(shownRoot, sortMode) { largestFiles(shownRoot, sortMode) },
                rootTotal = shownRoot.sizeBytes,
                onSelectNode = onSelectNode,
            )

            else -> TreeList(
                modifier = Modifier.weight(1f),
                root = shownRoot,
                expandedPaths = activeExpanded,
                selectedPath = selectedPath,
                onSelectNode = onSelectNode,
            )
        }
    }
}

private fun onInsightClick(
    onListMode: (ListMode) -> Unit,
    onRevealPath: (String) -> Unit,
): (SpaceInsight) -> Unit = { insight ->
    when {
        insight.kind == InsightKind.BIG_FILES -> onListMode(ListMode.LARGEST)
        insight.targetPath != null -> onRevealPath(insight.targetPath)
    }
}

@Composable
private fun ResultToolbar(
    listMode: ListMode,
    sortMode: SortMode,
    onListMode: (ListMode) -> Unit,
    onSortMode: (SortMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sortMenuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SegmentedSelector(
            label = null,
            options = listOf(
                SegmentOption(ListMode.TREE, "Tree", Icons.Outlined.Storage),
                SegmentOption(ListMode.LARGEST, "Largest", Icons.Outlined.Sort),
            ),
            selected = listMode,
            enabled = true,
            onSelect = onListMode,
            modifier = Modifier.width(220.dp),
        )
        Spacer(Modifier.weight(1f))
        Box {
            OutlinedButton(
                onClick = { sortMenuOpen = true },
                contentPadding = ButtonDefaults.ContentPadding,
                modifier = Modifier.heightIn(min = 44.dp),
            ) {
                Icon(Icons.Outlined.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(sortLabel(sortMode), maxLines = 1)
            }
            DropdownMenu(
                expanded = sortMenuOpen,
                onDismissRequest = { sortMenuOpen = false },
            ) {
                SortMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(sortLabel(mode)) },
                        onClick = {
                            onSortMode(mode)
                            sortMenuOpen = false
                        },
                        leadingIcon = if (mode == sortMode) {
                            { Icon(Icons.Outlined.Check, contentDescription = null) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

private fun sortLabel(sortMode: SortMode): String = when (sortMode) {
    SortMode.SIZE -> "Size"
    SortMode.NAME -> "Name"
    SortMode.PATH -> "Path"
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        placeholder = { Text("Search names", style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                TextButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                }
            }
        },
        shape = MaterialTheme.shapes.medium,
    )
}

@Composable
private fun NoticeBand(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SuggestionsPanel(
    insights: List<SpaceInsight>,
    onInsightClick: (SpaceInsight) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Lightbulb,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Space suggestions",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        insights.forEach { insight ->
            SpaceInsightRow(insight = insight, onClick = { onInsightClick(insight) })
        }
    }
}

@Composable
private fun SpaceInsightRow(insight: SpaceInsight, onClick: () -> Unit) {
    val clickable = insight.kind == InsightKind.BIG_FILES || insight.targetPath != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (clickable) Modifier.selectable(selected = false, onClick = onClick) else Modifier)
            .heightIn(min = 44.dp)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = insight.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = insight.detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        MeterText(
            text = formatBytes(insight.sizeBytes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SelectionStrip(
    node: ScanNode,
    onCopyPath: () -> Unit,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = node.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Selected",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onCopyPath, modifier = Modifier.heightIn(min = 44.dp)) {
            Text("Copy path")
        }
        if (!node.isDirectory) {
            TextButton(onClick = onOpen, modifier = Modifier.heightIn(min = 44.dp)) {
                Icon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Open")
            }
        }
    }
}

@Composable
private fun TreeList(
    modifier: Modifier,
    root: ScanNode,
    expandedPaths: Set<String>,
    selectedPath: String?,
    onSelectNode: (ScanNode) -> Unit,
) {
    val visibleNodes = remember(root, expandedPaths) { flattenTree(root, expandedPaths) }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 12.dp),
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

@Composable
private fun LargestFilesList(
    modifier: Modifier,
    files: List<ScanNode>,
    rootTotal: Long,
    onSelectNode: (ScanNode) -> Unit,
) {
    if (files.isEmpty()) {
        StatePanel(
            modifier = modifier,
            icon = Icons.Outlined.Search,
            title = "No files found",
            message = "This scan found no files to list.",
        )
        return
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        items(files, key = { it.path }) { file ->
            FileResultRow(
                file = file,
                share = if (rootTotal > 0L) file.sizeBytes.toFloat() / rootTotal.toFloat() else 0f,
                onClick = { onSelectNode(file) },
            )
        }
    }
}

@Composable
private fun FileResultRow(
    file: ScanNode,
    share: Float,
    onClick: () -> Unit,
) {
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = false, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "${file.name}, file, ${formatBytes(file.sizeBytes)}"
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Description,
                contentDescription = null,
                tint = mutedColor,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = file.path,
                    style = MaterialTheme.typography.labelSmall,
                    color = mutedColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                MeterText(text = formatBytes(file.sizeBytes), style = MaterialTheme.typography.labelLarge)
                Text(
                    text = formatPercent(share),
                    style = MaterialTheme.typography.labelSmall,
                    color = mutedColor,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
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
    val guideColor = MaterialTheme.colorScheme.outlineVariant
    val accent = MaterialTheme.colorScheme.primary
    val contentColor = MaterialTheme.colorScheme.onSurface
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val description = buildString {
        append(node.name)
        append(", ")
        append(if (node.isDirectory) "folder" else "file")
        append(", ")
        append(formatBytes(node.sizeBytes))
        if (node.isDirectory && node.children.isNotEmpty()) {
            append(", ${itemLabel(node.children.size)}")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                if (selected) {
                    drawRect(accent, size = Size(3.dp.toPx(), this.size.height))
                }
                val stroke = 1.dp.toPx()
                val step = 16.dp.toPx()
                val first = 12.dp.toPx() + 11.dp.toPx()
                repeat(visible.depth.coerceAtMost(5)) { level ->
                    val x = first + level * step
                    drawLine(
                        color = guideColor,
                        start = Offset(x, 0f),
                        end = Offset(x, this.size.height),
                        strokeWidth = stroke,
                    )
                }
            }
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent,
            )
            .selectable(
                selected = selected,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = description
                if (node.isDirectory) {
                    stateDescription = if (expanded) "Expanded" else "Collapsed"
                }
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(
                    start = 12.dp + (visible.depth.coerceAtMost(5) * 16).dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 8.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = when {
                    !node.isDirectory -> Icons.Outlined.Description
                    expanded -> Icons.Outlined.ExpandMore
                    else -> Icons.Outlined.ChevronRight
                },
                contentDescription = null,
                tint = if (selected) accent else mutedColor,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor,
                    fontWeight = if (visible.depth == 0) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (node.isDirectory && node.children.isNotEmpty()) {
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = itemLabel(node.children.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = mutedColor,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                MeterText(text = formatBytes(node.sizeBytes), style = MaterialTheme.typography.labelLarge)
                if (visible.depth > 0 && visible.share > 0f) {
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = formatPercent(visible.share),
                        style = MaterialTheme.typography.labelSmall,
                        color = mutedColor,
                        maxLines = 1,
                    )
                }
            }
        }
        HorizontalDivider(color = guideColor.copy(alpha = 0.6f))
    }
}

@Composable
private fun ScanActionBar(
    scope: ScanScope,
    phase: ScanPhase,
    sizeMode: SizeMode,
    rootAccess: RootAccess,
    hasStorageAccess: Boolean,
    scanCooldown: Boolean,
    onScan: () -> Unit,
    onCancelScan: () -> Unit,
    onRequestStorageAccess: () -> Unit,
    onCheckRootAccess: () -> Unit,
) {
    val scanning = phase == ScanPhase.Scanning
    val needsPermission = scope == ScanScope.SHARED_STORAGE && !hasStorageAccess
    val needsRootCheck = scope == ScanScope.ROOT_DEVICE && rootAccess != RootAccess.AVAILABLE
    val checkingRoot = scope == ScanScope.ROOT_DEVICE && rootAccess == RootAccess.CHECKING
    val complete = phase == ScanPhase.Complete

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
                    Spacer(Modifier.width(8.dp))
                    Text("Cancel scan")
                }
                return@Column
            }

            val context = when {
                needsRootCheck -> "Root access is required before scanning /data"
                needsPermission -> "Storage permission is required before scanning shared storage"
                scanCooldown -> "Storage is resting briefly after the last scan"
                else -> {
                    val target = if (scope == ScanScope.ROOT_DEVICE) "/data" else "Shared storage"
                    val measurement = if (sizeMode == SizeMode.LOGICAL) "File size" else "Disk usage"
                    "$target · $measurement"
                }
            }
            val label = when {
                checkingRoot -> "Checking root access"
                needsRootCheck -> "Check root access"
                needsPermission -> "Grant file access"
                scanCooldown -> "Just scanned"
                complete -> "Scan again"
                scope == ScanScope.ROOT_DEVICE -> "Scan with root"
                sizeMode == SizeMode.LOGICAL -> "Scan file sizes"
                else -> "Scan disk usage"
            }
            val icon = when {
                needsRootCheck -> Icons.Outlined.Shield
                needsPermission -> Icons.Outlined.LockOpen
                scanCooldown -> Icons.Outlined.Refresh
                complete -> Icons.Outlined.Refresh
                scope == ScanScope.ROOT_DEVICE -> Icons.Outlined.Shield
                else -> Icons.Outlined.Storage
            }
            val action = when {
                needsRootCheck -> onCheckRootAccess
                needsPermission -> onRequestStorageAccess
                else -> onScan
            }

            Text(
                text = context,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = action,
                enabled = !checkingRoot && !scanCooldown,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) {
                Icon(icon, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(label)
            }
        }
    }
}

@Composable
private fun MeterText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Text(
        text = text,
        modifier = modifier,
        style = style.merge(MeterTextStyle),
        color = color,
        maxLines = maxLines,
        overflow = overflow,
    )
}

private fun itemLabel(count: Int): String = if (count == 1) "1 item" else "$count items"

private fun formatPercent(share: Float): String {
    val percent = (share * 100f).roundToInt()
    return if (percent >= 1) "$percent%" else "<1%"
}

private fun formatInteger(value: Long): String = NumberFormat.getIntegerInstance().format(value)

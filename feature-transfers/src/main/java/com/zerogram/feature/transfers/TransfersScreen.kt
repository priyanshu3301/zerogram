package com.zerogram.feature.transfers

import android.app.DownloadManager
import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import com.zerogram.core.ui.components.AppDropdownMenu
import com.zerogram.core.ui.components.AppDropdownMenuItem
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import com.zerogram.feature.folder.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.zerogram.core.ui.utils.FileOpener
import com.zerogram.data.local.dao.TransferJobWithFile
import kotlinx.coroutines.launch
import java.io.File

private val BackgroundColor = Color(0xFF000000)
private val SurfaceColor = Color(0xFF1E1E1E)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFFA0A0A0)
private val PrimaryBlue = Color(0xFF2B65F6)
private val SuccessGreen = Color(0xFF22C55E)
private val ErrorRed = Color(0xFFEF4444)
private val SuccessGreenBg = Color(0xFF1B3320)
private val ErrorRedBg = Color(0xFF3F1919)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransfersScreen(
    viewModel: TransfersViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToFolder: (String) -> Unit = {}
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Uploads", "Downloads")
    val activeType = if (selectedTabIndex == 0) "upload" else "download"
    
    val uploads by viewModel.uploads.collectAsState(initial = emptyList())
    val downloads by viewModel.downloads.collectAsState(initial = emptyList())
    val currentJobs = if (selectedTabIndex == 0) uploads else downloads

    var selectedJobIds by remember { mutableStateOf(setOf<String>()) }
    var showBulkRemoveDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundColor)
        ) {
            if (selectedJobIds.isNotEmpty()) {
                val allSelected = selectedJobIds.size == currentJobs.size && currentJobs.isNotEmpty()
                TopAppBar(
                    title = { Text("${selectedJobIds.size} Selected", color = TextPrimary, fontSize = 18.sp) },
                    navigationIcon = {
                        IconButton(onClick = { selectedJobIds = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Selection", tint = TextPrimary)
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.pauseJobs(selectedJobIds) }) {
                            Icon(Icons.Default.Pause, contentDescription = "Pause Selected", tint = TextPrimary)
                        }
                        IconButton(onClick = { viewModel.resumeJobs(selectedJobIds) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Resume Selected", tint = PrimaryBlue)
                        }
                        IconButton(onClick = { showBulkRemoveDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove Selected", tint = ErrorRed)
                        }
                        TextButton(onClick = {
                            selectedJobIds = if (allSelected) emptySet() else currentJobs.map { it.job.id }.toSet()
                        }) {
                            Text(if (allSelected) "Deselect" else "All", color = PrimaryBlue, fontSize = 14.sp)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = BackgroundColor,
                        scrolledContainerColor = BackgroundColor
                    )
                )
            } else {
                TopAppBar(
                    title = { Text("Transfers", color = TextPrimary, fontSize = 20.sp) },
                    navigationIcon = {
                        // Only show navigation icon if we actually want to navigate back (e.g. from standalone usage)
                        // If it's embedded in HomeScreen, onNavigateBack will scroll pager to 0
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }
                    },
                    actions = {
                        if (currentJobs.isNotEmpty()) {
                            IconButton(onClick = { viewModel.pauseAll(activeType) }) {
                                Icon(Icons.Default.Pause, contentDescription = "Pause All", tint = TextPrimary)
                            }
                            IconButton(onClick = { viewModel.resumeAll(activeType) }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Resume All", tint = PrimaryBlue)
                            }
                            var showMoreMenu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { showMoreMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "More Options", tint = TextPrimary)
                                }
                                AppDropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false },
                                    modifier = Modifier.width(180.dp).background(SurfaceColor),
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    AppDropdownMenuItem(
                                        text = { Text("Clear completed", color = TextPrimary, fontSize = 15.sp) },
                                        onClick = {
                                            showMoreMenu = false
                                            viewModel.clearCompleted(activeType)
                                        }
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = BackgroundColor,
                        scrolledContainerColor = BackgroundColor
                    )
                )
            }
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = BackgroundColor,
                contentColor = TextPrimary,
                indicator = { tabPositions ->
                    if (selectedTabIndex < tabPositions.size) {
                        TabRowDefaults.Indicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = PrimaryBlue
                        )
                    }
                },
                divider = {
                    Divider(color = SurfaceColor)
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { 
                            selectedTabIndex = index
                            selectedJobIds = emptySet()
                        },
                        text = {
                            val count = if (index == 0) uploads.size else downloads.size
                            Text(
                                text = "$title ($count)",
                                color = if (selectedTabIndex == index) TextPrimary else TextSecondary,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            TransferListSection(
                jobs = currentJobs,
                emptyMessage = if (selectedTabIndex == 0) "No active uploads" else "No active downloads",
                selectedJobIds = selectedJobIds,
                onToggleSelection = { id ->
                    selectedJobIds = if (selectedJobIds.contains(id)) {
                        selectedJobIds - id
                    } else {
                        selectedJobIds + id
                    }
                },
                onPauseJob = { viewModel.pauseJob(it) },
                onResumeJob = { viewModel.resumeJob(it) },
                onCancelJob = { viewModel.cancelJob(it) },
                onRemoveJobs = { ids, delVault, delStorage -> viewModel.removeJobs(ids, delVault, delStorage) },
                onGetFileFolderId = { viewModel.getFileFolderId(it) },
                onGetLocationPath = { fileId, type -> viewModel.getLocationPath(fileId, type) },
                onNavigateToFolder = onNavigateToFolder
            )
        }
        
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            SnackbarHost(snackbarHostState)
        }
    }

    if (showBulkRemoveDialog) {
        RemoveTransferDialog(
            isUpload = activeType == "upload",
            isDownload = activeType == "download",
            itemCount = selectedJobIds.size,
            onDismiss = { showBulkRemoveDialog = false },
            onConfirm = { deleteFromVault, deleteFromStorage ->
                viewModel.removeJobs(selectedJobIds, deleteFromVault, deleteFromStorage)
                selectedJobIds = emptySet()
                showBulkRemoveDialog = false
            }
        )
    }
}

@Composable
fun TransferListSection(
    jobs: List<TransferJobWithFile>,
    emptyMessage: String,
    selectedJobIds: Set<String>,
    onToggleSelection: (String) -> Unit,
    onPauseJob: (String) -> Unit,
    onResumeJob: (String) -> Unit,
    onCancelJob: (String) -> Unit,
    onRemoveJobs: (Set<String>, Boolean, Boolean) -> Unit,
    onGetFileFolderId: suspend (String) -> String?,
    onGetLocationPath: suspend (String, String) -> String,
    onNavigateToFolder: (String) -> Unit
) {
    if (jobs.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(emptyMessage, color = TextSecondary, fontSize = 16.sp)
        }
    } else {
        val inProgress = jobs.filter { it.job.status == "uploading" || it.job.status == "downloading" || it.job.status == "queued" }
        val paused = jobs.filter { it.job.status == "paused" }
        val completedAndOthers = jobs.filter { it.job.status == "completed" || it.job.status == "failed" || it.job.status == "canceled" }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            if (inProgress.isNotEmpty()) {
                item {
                    SectionHeader("IN PROGRESS (${inProgress.size})")
                }
                items(inProgress, key = { it.job.id }, contentType = { "transfer_item" }) { item ->
                    TransferJobItem(
                        item = item, 
                        isSelected = selectedJobIds.contains(item.job.id),
                        selectionMode = selectedJobIds.isNotEmpty(),
                        onToggleSelection = { onToggleSelection(item.job.id) },
                        onPauseJob = onPauseJob,
                        onResumeJob = onResumeJob,
                        onCancelJob = onCancelJob,
                        onRemoveJobs = onRemoveJobs,
                        onGetFileFolderId = onGetFileFolderId,
                        onGetLocationPath = onGetLocationPath,
                        onNavigateToFolder = onNavigateToFolder
                    )
                }
            }

            if (paused.isNotEmpty()) {
                item {
                    SectionHeader("PAUSED (${paused.size})")
                }
                items(paused, key = { it.job.id }, contentType = { "transfer_item" }) { item ->
                    TransferJobItem(
                        item = item, 
                        isSelected = selectedJobIds.contains(item.job.id),
                        selectionMode = selectedJobIds.isNotEmpty(),
                        onToggleSelection = { onToggleSelection(item.job.id) },
                        onPauseJob = onPauseJob,
                        onResumeJob = onResumeJob,
                        onCancelJob = onCancelJob,
                        onRemoveJobs = onRemoveJobs,
                        onGetFileFolderId = onGetFileFolderId,
                        onGetLocationPath = onGetLocationPath,
                        onNavigateToFolder = onNavigateToFolder
                    )
                }
            }

            if (completedAndOthers.isNotEmpty()) {
                item {
                    SectionHeader("COMPLETED & OTHERS (${completedAndOthers.size})")
                }
                items(completedAndOthers, key = { it.job.id }, contentType = { "transfer_item" }) { item ->
                    TransferJobItem(
                        item = item, 
                        isSelected = selectedJobIds.contains(item.job.id),
                        selectionMode = selectedJobIds.isNotEmpty(),
                        onToggleSelection = { onToggleSelection(item.job.id) },
                        onPauseJob = onPauseJob,
                        onResumeJob = onResumeJob,
                        onCancelJob = onCancelJob,
                        onRemoveJobs = onRemoveJobs,
                        onGetFileFolderId = onGetFileFolderId,
                        onGetLocationPath = onGetLocationPath,
                        onNavigateToFolder = onNavigateToFolder
                    )
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        color = PrimaryBlue,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransferJobItem(
    item: TransferJobWithFile,
    isSelected: Boolean,
    selectionMode: Boolean,
    onToggleSelection: () -> Unit,
    onPauseJob: (String) -> Unit,
    onResumeJob: (String) -> Unit,
    onCancelJob: (String) -> Unit,
    onRemoveJobs: (Set<String>, Boolean, Boolean) -> Unit,
    onGetFileFolderId: suspend (String) -> String?,
    onGetLocationPath: suspend (String, String) -> String,
    onNavigateToFolder: (String) -> Unit
) {
    val job = item.job
    
    val bgColor = when {
        isSelected -> SurfaceColor
        job.status == "completed" -> SuccessGreenBg
        job.status == "failed" || job.status == "canceled" -> ErrorRedBg
        else -> Color.Transparent
    }

    val iconVector = when {
        item.mimeType.startsWith("image/") -> Icons.Default.Image
        item.mimeType.startsWith("video/") -> Icons.Default.Movie
        item.mimeType.startsWith("audio/") -> Icons.Default.AudioFile
        else -> Icons.Default.InsertDriveFile
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(bgColor)
            .combinedClickable(
                onLongClick = { onToggleSelection() },
                onClick = { 
                    if (selectionMode) {
                        onToggleSelection()
                    }
                }
            )
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = iconVector,
            contentDescription = "File Type",
            tint = TextSecondary,
            modifier = Modifier
                .size(48.dp)
                .background(SurfaceColor, MaterialTheme.shapes.medium)
                .padding(12.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.displayName,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            val statusColor = when (job.status) {
                "completed" -> SuccessGreen
                "failed", "canceled" -> ErrorRed
                else -> TextSecondary
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = job.status.replaceFirstChar { it.uppercase() },
                    color = statusColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                if (job.totalBytes > 0) {
                    val progressText = if (job.status == "completed") {
                        formatBytes(job.totalBytes)
                    } else {
                        formatBytes(job.progressBytes) + " / " + formatBytes(job.totalBytes)
                    }
                    Text(
                        text = progressText,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            if (job.status == "uploading" || job.status == "downloading" || job.status == "paused" || job.status == "queued") {
                Spacer(modifier = Modifier.height(8.dp))
                val progress = if (job.totalBytes > 0) job.progressBytes.toFloat() / job.totalBytes else 0f
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(MaterialTheme.shapes.medium),
                    color = PrimaryBlue,
                    trackColor = SurfaceColor
                )
            }
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Actions (Only 3-dot overflow menu, no exposed standalone buttons)
        Row(verticalAlignment = Alignment.CenterVertically) {
            var menuExpanded by remember { mutableStateOf(false) }
            val context = LocalContext.current
            val coroutineScope = rememberCoroutineScope()
            
            var showRemoveDialog by remember { mutableStateOf(false) }
            var showLocationDialog by remember { mutableStateOf(false) }
            var locationPath by remember { mutableStateOf("") }
            
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More Options", tint = TextSecondary)
                }
                
                AppDropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.width(220.dp).background(SurfaceColor),
                    shape = MaterialTheme.shapes.medium
                ) {
                    if (job.status == "completed") {
                        AppDropdownMenuItem(
                            text = { Text("Open file", color = TextPrimary, fontSize = 16.sp) },
                            onClick = {
                                menuExpanded = false
                                if (job.type == "download") {
                                    val downloadsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Zerogram")
                                    val file = File(downloadsDir, item.displayName)
                                    if (file.exists()) {
                                        FileOpener.openFile(context, file, item.mimeType)
                                    } else {
                                        Toast.makeText(context, "File not found", Toast.LENGTH_SHORT).show()
                                    }
                                } else if (job.type == "upload") {
                                    Toast.makeText(context, "Cannot directly open uploaded file unless downloaded.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        HorizontalDivider(color = Color(0xFF333333), thickness = 1.dp)
                        AppDropdownMenuItem(
                            text = { Text("Open folder", color = TextPrimary, fontSize = 16.sp) },
                            onClick = {
                                menuExpanded = false
                                if (job.type == "upload") {
                                    coroutineScope.launch {
                                        val folderId = onGetFileFolderId(job.fileId)
                                        if (folderId != null) {
                                            onNavigateToFolder(folderId)
                                        } else {
                                            onNavigateToFolder("")
                                        }
                                    }
                                } else {
                                    try {
                                        val downloadsDir = File(
                                            Environment.getExternalStoragePublicDirectory(
                                                Environment.DIRECTORY_DOWNLOADS), "Zerogram")
                                        if (!downloadsDir.exists()) downloadsDir.mkdirs()
                                        
                                        val uri = FileProvider.getUriForFile(
                                            context, 
                                            "com.zerogram.fileprovider", 
                                            downloadsDir
                                        )
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, "resource/folder")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        try {
                                            context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            })
                                        } catch (e2: Exception) {
                                            Toast.makeText(context, "No file manager found", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        )
                        HorizontalDivider(color = Color(0xFF333333), thickness = 1.dp)
                    }

                    if (job.status == "uploading" || job.status == "downloading" || job.status == "queued") {
                        AppDropdownMenuItem(
                            text = { Text("Pause", color = TextPrimary, fontSize = 16.sp) },
                            onClick = {
                                menuExpanded = false
                                onPauseJob(job.id)
                            }
                        )
                        HorizontalDivider(color = Color(0xFF333333), thickness = 1.dp)
                        AppDropdownMenuItem(
                            text = { Text("Cancel transfer", color = ErrorRed, fontSize = 16.sp) },
                            onClick = {
                                menuExpanded = false
                                onCancelJob(job.id)
                            }
                        )
                        HorizontalDivider(color = Color(0xFF333333), thickness = 1.dp)
                    } else if (job.status == "paused" || job.status == "failed" || job.status == "canceled") {
                        AppDropdownMenuItem(
                            text = { Text(if (job.status == "paused") "Resume" else "Retry", color = PrimaryBlue, fontSize = 16.sp) },
                            onClick = {
                                menuExpanded = false
                                onResumeJob(job.id)
                            }
                        )
                        HorizontalDivider(color = Color(0xFF333333), thickness = 1.dp)
                    }

                    AppDropdownMenuItem(
                        text = { Text("Remove from list", color = TextPrimary, fontSize = 16.sp) },
                        onClick = {
                            menuExpanded = false
                            showRemoveDialog = true
                        }
                    )
                    
                    if (job.status == "completed") {
                        HorizontalDivider(color = Color(0xFF333333), thickness = 1.dp)
                        AppDropdownMenuItem(
                            text = { Text("Show ${if(job.type == "upload") "upload" else "download"} location", color = TextPrimary, fontSize = 16.sp) },
                            onClick = {
                                menuExpanded = false
                                coroutineScope.launch {
                                    locationPath = onGetLocationPath(job.fileId, job.type)
                                    showLocationDialog = true
                                }
                            }
                        )
                    }
                }
                
                if (showRemoveDialog) {
                    RemoveTransferDialog(
                        isUpload = job.type == "upload",
                        isDownload = job.type == "download",
                        itemCount = 1,
                        onDismiss = { showRemoveDialog = false },
                        onConfirm = { deleteFromVault, deleteFromStorage ->
                            onRemoveJobs(setOf(job.id), deleteFromVault, deleteFromStorage)
                            showRemoveDialog = false
                        }
                    )
                }

                if (showLocationDialog) {
                    AlertDialog(
                        onDismissRequest = { showLocationDialog = false },
                        title = { Text("Location", color = TextPrimary) },
                        text = { Text(locationPath, color = TextPrimary) },
                        confirmButton = {
                            TextButton(onClick = { showLocationDialog = false }) {
                                Text("OK", color = PrimaryBlue)
                            }
                        },
                        containerColor = SurfaceColor,
                        titleContentColor = TextPrimary,
                        textContentColor = TextPrimary
                    )
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format("%.2f GB", gb)
        mb >= 1.0 -> String.format("%.2f MB", mb)
        kb >= 1.0 -> String.format("%.2f KB", kb)
        else -> "$bytes B"
    }
}

@Composable
fun RemoveTransferDialog(
    isUpload: Boolean,
    isDownload: Boolean,
    itemCount: Int = 1,
    onDismiss: () -> Unit,
    onConfirm: (deleteFromVault: Boolean, deleteFromStorage: Boolean) -> Unit
) {
    var deletePermanently by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (itemCount > 1) "Remove $itemCount Transfers" else "Remove Transfer", color = Color.White) },
        text = {
            Column {
                Text(
                    if (itemCount > 1) "Are you sure you want to remove these $itemCount items from the list?"
                    else "Are you sure you want to remove this from the list?", 
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                if (isUpload || isDownload) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { deletePermanently = !deletePermanently }
                            .padding(vertical = 8.dp)
                    ) {
                        Checkbox(
                            checked = deletePermanently,
                            onCheckedChange = { deletePermanently = it },
                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF2B65F6), checkmarkColor = Color.White)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val textStr = if (isUpload) "Also delete from vault" else "Also delete from storage"
                        Text(textStr, color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(isUpload && deletePermanently, isDownload && deletePermanently) }) {
                Text("Remove", color = Color(0xFFEF4444))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFFA0A0A0))
            }
        },
        containerColor = Color(0xFF1E1E1E),
        titleContentColor = Color.White,
        textContentColor = Color.White
    )
}

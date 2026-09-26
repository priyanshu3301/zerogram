package com.example.zerogram.ui.folder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zerogram.util.ScanOptions
import java.util.Locale

private val BackgroundColor = Color(0xFF1E1E1E)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFFA0A0A0)
private val PrimaryBlue = Color(0xFF2B65F6)
private val ErrorRed = Color(0xFFEF4444)

@Composable
fun FolderUploadDialog(
    state: FolderUploadState,
    onUpdateOptions: (ScanOptions) -> Unit,
    onStartScan: () -> Unit,
    onCancel: () -> Unit,
    onUpload: () -> Unit,
    onDismissError: () -> Unit
) {
    if (state is FolderUploadState.Idle) return

    AlertDialog(
        onDismissRequest = {
            if (state !is FolderUploadState.Scanning && state !is FolderUploadState.PreparingUpload) {
                onCancel()
            }
        },
        containerColor = BackgroundColor,
        titleContentColor = TextPrimary,
        textContentColor = TextPrimary,
        title = {
            Text(
                text = when (state) {
                    is FolderUploadState.ScanOptionsSelection -> "Upload Folder"
                    is FolderUploadState.Scanning -> "Scanning Folder..."
                    is FolderUploadState.ScanComplete -> "Scan Complete"
                    is FolderUploadState.PreparingUpload -> "Creating Upload..."
                    is FolderUploadState.Error -> "Error"
                    else -> ""
                },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (state) {
                    is FolderUploadState.ScanOptionsSelection -> {
                        var options by remember { mutableStateOf(state.options) }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Upload hidden folders", modifier = Modifier.weight(1f))
                            Switch(
                                checked = options.uploadHiddenFolders,
                                onCheckedChange = { 
                                    options = options.copy(uploadHiddenFolders = it)
                                    onUpdateOptions(options)
                                }
                            )
                        }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Upload hidden files", modifier = Modifier.weight(1f))
                            Switch(
                                checked = options.uploadHiddenFiles,
                                onCheckedChange = { 
                                    options = options.copy(uploadHiddenFiles = it)
                                    onUpdateOptions(options)
                                }
                            )
                        }
                    }
                    
                    is FolderUploadState.Scanning -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatsRow("Folders scanned", state.foldersScanned.toString())
                            StatsRow("Empty folders", state.emptyFolders.toString())
                            StatsRow("Files found", state.filesFound.toString())
                            StatsRow("Total size", formatBytes(state.totalSize))
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp),
                                color = PrimaryBlue
                            )
                        }
                    }
                    
                    is FolderUploadState.ScanComplete -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatsRow("Folders scanned", state.result.folders.size.toString())
                            StatsRow("Empty folders", state.result.emptyFolders.toString())
                            StatsRow("Files found", state.result.files.size.toString())
                            StatsRow("Total size", formatBytes(state.result.totalSize))
                            
                            if (state.result.skippedItems.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Skipped items (${state.result.skippedItems.size})",
                                    color = ErrorRed,
                                    fontWeight = FontWeight.Bold
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 120.dp)
                                        .background(Color(0xFF2D2D2D), MaterialTheme.shapes.medium)
                                        .padding(8.dp)
                                ) {
                                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                        state.result.skippedItems.forEach { item ->
                                            Text(item.relativePath, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("Reason: ${item.reason}", fontSize = 10.sp, color = TextSecondary)
                                            Spacer(modifier = Modifier.height(4.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    is FolderUploadState.PreparingUpload -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(color = PrimaryBlue)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Preparing database mapping...")
                        }
                    }
                    
                    is FolderUploadState.Error -> {
                        Text(state.message, color = ErrorRed)
                    }
                    
                    else -> {}
                }
            }
        },
        confirmButton = {
            when (state) {
                is FolderUploadState.ScanOptionsSelection -> {
                    TextButton(onClick = onStartScan) { Text("Start Scan", color = PrimaryBlue) }
                }
                is FolderUploadState.ScanComplete -> {
                    TextButton(onClick = onUpload) { Text("Upload", color = PrimaryBlue) }
                }
                is FolderUploadState.Error -> {
                    TextButton(onClick = onDismissError) { Text("OK", color = PrimaryBlue) }
                }
                else -> {}
            }
        },
        dismissButton = {
            if (state !is FolderUploadState.PreparingUpload && state !is FolderUploadState.Error) {
                TextButton(onClick = onCancel) { Text("Cancel", color = TextSecondary) }
            }
        }
    )
}

@Composable
private fun StatsRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSecondary)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

private fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return Locale.getDefault().let { locale ->
        when {
            gb >= 1.0 -> String.format(locale, "%.2f GB", gb)
            mb >= 1.0 -> String.format(locale, "%.2f MB", mb)
            kb >= 1.0 -> String.format(locale, "%.2f KB", kb)
            else -> "$bytes B"
        }
    }
}

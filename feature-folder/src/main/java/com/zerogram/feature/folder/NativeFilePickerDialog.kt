package com.example.zerogram.ui.folder

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File

@Composable
fun NativeFilePickerDialog(
    onDismiss: () -> Unit,
    onFileSelected: (File) -> Unit,
    onFolderSelected: (File) -> Unit,
    selectFolder: Boolean = false
) {
    var currentDir by remember { mutableStateOf(Environment.getExternalStorageDirectory()) }
    var files by remember { mutableStateOf(currentDir.listFiles()?.toList()?.sortedBy { !it.isDirectory } ?: emptyList()) }

    LaunchedEffect(currentDir) {
        val list = currentDir.listFiles()
        if (list != null) {
            files = list.filter { !it.isHidden }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        } else {
            files = emptyList()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF1E1E1E)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Bar
                Surface(color = Color(0xFF1E1E1E), shadowElevation = 4.dp) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (selectFolder) "Select Folder" else "Select File",
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge
                            )
                            TextButton(onClick = onDismiss) {
                                Text("Cancel", color = Color(0xFF64B5F6))
                            }
                        }
                        Text(
                            text = currentDir.absolutePath,
                            color = Color.Gray,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Back navigation
                if (currentDir.absolutePath != Environment.getExternalStorageDirectory().absolutePath && currentDir.parentFile != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { currentDir = currentDir.parentFile!! }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFF64B5F6))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(".. (Go up)", color = Color.White)
                    }
                    HorizontalDivider(color = Color(0xFF333333))
                }

                // File List
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(files) { file ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (file.isDirectory) {
                                        currentDir = file
                                    } else if (!selectFolder) {
                                        onFileSelected(file)
                                    }
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                                contentDescription = null,
                                tint = if (file.isDirectory) Color(0xFF64B5F6) else Color.Gray
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = file.name,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        HorizontalDivider(color = Color(0xFF333333))
                    }
                }

                // Select Folder Button
                if (selectFolder) {
                    Button(
                        onClick = { onFolderSelected(currentDir) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF64B5F6))
                    ) {
                        Text("Select Current Folder", color = Color.Black)
                    }
                }
            }
        }
    }
}

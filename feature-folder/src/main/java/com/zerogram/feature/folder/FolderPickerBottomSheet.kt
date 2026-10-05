package com.zerogram.feature.folder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zerogram.core.ui.R
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerBottomSheet(
    action: String, // "move" or "copy"
    onDismiss: () -> Unit,
    onConfirm: (targetFolderId: String?) -> Unit,
    viewModel: FolderPickerViewModel = hiltViewModel()
) {
    val folders by viewModel.folders.collectAsState()
    val breadcrumbs by viewModel.breadcrumbs.collectAsState()
    val currentFolderId by viewModel.currentFolderId.collectAsState()
    val currentFolderName = breadcrumbs.lastOrNull()?.name ?: "All files"

    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    val BackgroundColor = Color(0xFF1E1E1E)
    val SurfaceColor = Color(0xFF2C2C2C)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFA0A0A0)
    val PrimaryBlue = Color(0xFF1E88E5)
    val DividerColor = Color(0xFF333333)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = BackgroundColor,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = TextSecondary) }
    ) {
        Column(modifier = Modifier.fillMaxHeight(0.75f)) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss, contentPadding = PaddingValues(0.dp)) {
                    Text("Close", color = PrimaryBlue, fontSize = 16.sp)
                }
                
                Text(
                    text = currentFolderName,
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
                
                TextButton(onClick = { showNewFolderDialog = true }, contentPadding = PaddingValues(0.dp)) {
                    Text("Create", color = PrimaryBlue, fontSize = 16.sp)
                }
            }

            // Breadcrumbs Row
            if (breadcrumbs.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { viewModel.navigateBack() },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(breadcrumbs[breadcrumbs.size - 2].name, color = PrimaryBlue, fontSize = 14.sp)
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("All files", color = PrimaryBlue, fontSize = 14.sp)
                }
            }

            // Folder List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(folders) { folder ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.navigateToFolder(folder.id, folder.name) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Folder Icon
                        Icon(
                            painter = painterResource(id = R.drawable.ic_file_folder_icon),
                            contentDescription = folder.name,
                            tint = Color.Unspecified, 
                            modifier = Modifier.size(48.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = folder.name,
                                color = TextPrimary,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val itemText = if (folder.itemCount == 1) "1 item" else "${folder.itemCount} items"
                            val dateText = com.zerogram.util.FormatUtils.formatDate(folder.createdAt)
                            Text(
                                text = "$itemText | $dateText",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Navigate",
                            tint = TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(start = 80.dp), color = DividerColor, thickness = 1.dp)
                }
            }

            // Bottom Action Button
            val buttonText = if (action == "move") "Move here" else "Copy here"
            Button(
                onClick = { onConfirm(currentFolderId) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .height(56.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text(buttonText, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }
        }
    }

    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("New folder", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { if (it.length <= 50) newFolderName = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = TextSecondary,
                        cursorColor = PrimaryBlue
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            viewModel.createNewFolder(newFolderName.trim())
                            newFolderName = ""
                            showNewFolderDialog = false
                        }
                    }
                ) {
                    Text("OK", color = PrimaryBlue)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        newFolderName = ""
                        showNewFolderDialog = false
                    }
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceColor,
            titleContentColor = TextPrimary
        )
    }
}

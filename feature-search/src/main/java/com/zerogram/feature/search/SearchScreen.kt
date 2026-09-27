package com.zerogram.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import com.zerogram.core.ui.components.AppDropdownMenu
import com.zerogram.core.ui.components.AppDropdownMenuItem
import com.zerogram.core.ui.components.AppList
import com.zerogram.core.ui.components.AppListItem
import com.zerogram.core.ui.components.ImmutableListWrapper
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.zerogram.core.ui.components.SortOrder
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import com.zerogram.core.ui.navigation.SharedBoundsAnimSpec
import com.zerogram.core.ui.navigation.LocalAnimatedVisibilityScope
import com.zerogram.core.ui.navigation.LocalSharedTransitionScope
import com.zerogram.core.ui.components.SelectionDetails
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zerogram.core.ui.R
import com.zerogram.feature.folder.*

private val BackgroundColor = Color(0xFF000000)
private val SurfaceColor = Color(0xFF1E1E1E)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFFA0A0A0)
private val DividerColor = Color(0xFF2C2C2C)
private val PrimaryBlue = Color(0xFF2B65F6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val filesAndFolders by viewModel.filesAndFolders.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val selectedItems by viewModel.selectedItems.collectAsState()
    
    val isSelectionMode = selectedItems.isNotEmpty()

    var pickerAction by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    
    var showDetailsDialog by remember { mutableStateOf(false) }
    var detailsData by remember { mutableStateOf<SelectionDetails?>(null) }
    
    var showRenameDialog by remember { mutableStateOf(false) }
    var itemToRename by remember { mutableStateOf<AppListItem?>(null) }

    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val renderer = remember(
        isSelectionMode, selectedItems, searchQuery, sortOrder
    ) {
        object : com.zerogram.core.ui.scaffold.ScaffoldRenderer {
            @Composable
            override fun TopBar() {
            Column(modifier = Modifier.fillMaxWidth().background(BackgroundColor)) {
                if (isSelectionMode) {
                    TopAppBar(
                        title = { Text("${selectedItems.size} selected", color = TextPrimary) },
                        navigationIcon = {
                            IconButton(onClick = { viewModel.clearSelection() }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Clear Selection", tint = TextPrimary)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = BackgroundColor,
                            titleContentColor = TextPrimary
                        )
                    )
                } else {
                    TopAppBar(
                        title = {
                            TextField(
                                value = searchQuery,
                                onValueChange = { viewModel.updateSearchQuery(it) },
                                placeholder = { Text("Search files and folders...", color = TextSecondary) },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                            }
                        },
                        actions = {
                            var showSortMenu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { showSortMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Sort Options", tint = TextPrimary, modifier = Modifier.size(24.dp))
                                }
                                AppDropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false },
                                    modifier = Modifier.width(200.dp),
                                    shape = MaterialTheme.shapes.medium,
                                    containerColor = SurfaceColor
                                ) {
                                    AppDropdownMenuItem(
                                        text = { Text("Newest first", color = TextPrimary, fontSize = 16.sp) },
                                        onClick = { 
                                            showSortMenu = false
                                            viewModel.setSortOrder(com.zerogram.core.ui.components.SortOrder.NEWEST_FIRST)
                                        },
                                        trailingIcon = if (sortOrder == com.zerogram.core.ui.components.SortOrder.NEWEST_FIRST) { @androidx.compose.runtime.Composable { Icon(Icons.Default.Check, contentDescription = null, tint = PrimaryBlue) } } else null
                                    )
                                    AppDropdownMenuItem(
                                        text = { Text("Name A-Z", color = TextPrimary, fontSize = 16.sp) },
                                        onClick = { 
                                            showSortMenu = false
                                            viewModel.setSortOrder(com.zerogram.core.ui.components.SortOrder.NAME_A_Z)
                                        },
                                        trailingIcon = if (sortOrder == com.zerogram.core.ui.components.SortOrder.NAME_A_Z) { @androidx.compose.runtime.Composable { Icon(Icons.Default.Check, contentDescription = null, tint = PrimaryBlue) } } else null
                                    )
                                    AppDropdownMenuItem(
                                        text = { Text("Name Z-A", color = TextPrimary, fontSize = 16.sp) },
                                        onClick = { 
                                            showSortMenu = false
                                            viewModel.setSortOrder(com.zerogram.core.ui.components.SortOrder.NAME_Z_A)
                                        },
                                        trailingIcon = if (sortOrder == com.zerogram.core.ui.components.SortOrder.NAME_Z_A) { @androidx.compose.runtime.Composable { Icon(Icons.Default.Check, contentDescription = null, tint = PrimaryBlue) } } else null
                                    )
                                    AppDropdownMenuItem(
                                        text = { Text("Largest first", color = TextPrimary, fontSize = 16.sp) },
                                        onClick = { 
                                            showSortMenu = false
                                            viewModel.setSortOrder(com.zerogram.core.ui.components.SortOrder.LARGEST_FIRST)
                                        },
                                        trailingIcon = if (sortOrder == com.zerogram.core.ui.components.SortOrder.LARGEST_FIRST) { @androidx.compose.runtime.Composable { Icon(Icons.Default.Check, contentDescription = null, tint = PrimaryBlue) } } else null
                                    )
                                    AppDropdownMenuItem(
                                        text = { Text("Smallest first", color = TextPrimary, fontSize = 16.sp) },
                                        onClick = { 
                                            showSortMenu = false
                                            viewModel.setSortOrder(com.zerogram.core.ui.components.SortOrder.SMALLEST_FIRST)
                                        },
                                        trailingIcon = if (sortOrder == com.zerogram.core.ui.components.SortOrder.SMALLEST_FIRST) { @androidx.compose.runtime.Composable { Icon(Icons.Default.Check, contentDescription = null, tint = PrimaryBlue) } } else null
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = BackgroundColor,
                            titleContentColor = TextPrimary
                        )
                    )
                }
            }
            } // Close TopBar

            @Composable
            override fun BottomBar() {
            if (isSelectionMode) {
                var showMoreMenu by remember { mutableStateOf(false) }
                BottomAppBar(
                    containerColor = SurfaceColor,
                    contentColor = TextPrimary
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val hasSelection = selectedItems.isNotEmpty()
                        BottomBarAction(R.drawable.ic_thin_share, "Share", enabled = hasSelection)
                        BottomBarAction(R.drawable.ic_thin_tag, "Tag", enabled = hasSelection)
                        BottomBarAction(R.drawable.ic_thin_move, "Move", enabled = hasSelection) {
                            pickerAction = "move"
                        }
                        BottomBarAction(R.drawable.ic_thin_delete, "Delete", enabled = hasSelection) {
                            viewModel.moveToTrash()
                        }
                        Box {
                            BottomBarAction(R.drawable.ic_thin_more, "More", enabled = hasSelection) {
                                showMoreMenu = true
                            }
                            AppDropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false },
                                modifier = Modifier.width(160.dp),
                                shape = MaterialTheme.shapes.medium,
                                containerColor = SurfaceColor
                            ) {
                                AppDropdownMenuItem(
                                    text = { Text("Copy", color = TextPrimary, fontSize = 18.sp) },
                                    onClick = { 
                                        showMoreMenu = false
                                        pickerAction = "copy"
                                    },
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                                )
                                HorizontalDivider(color = DividerColor, thickness = 1.dp)
                                AppDropdownMenuItem(
                                    text = { Text("Details", color = TextPrimary, fontSize = 18.sp) },
                                    onClick = { 
                                        showMoreMenu = false
                                        viewModel.getSelectionDetails { details ->
                                            detailsData = details
                                            showDetailsDialog = true
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                                )
                                if (selectedItems.size == 1) {
                                    HorizontalDivider(color = DividerColor, thickness = 1.dp)
                                    AppDropdownMenuItem(
                                        text = { Text("Rename", color = TextPrimary, fontSize = 18.sp) },
                                        onClick = { 
                                            showMoreMenu = false
                                            itemToRename = filesAndFolders.find { it.id == selectedItems.first() }
                                            if (itemToRename != null) {
                                                showRenameDialog = true
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    }

    com.zerogram.core.ui.scaffold.ScreenScaffoldConfig(renderer)

    Box(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        if (searchQuery.isBlank()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Type to search", color = TextSecondary, fontSize = 16.sp)
            }
        } else if (filesAndFolders.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No matching items found", color = TextSecondary, fontSize = 16.sp)
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                AppList(
                    items = ImmutableListWrapper(filesAndFolders),
                    selectedItems = selectedItems,
                    isSelectionMode = isSelectionMode,
                    onItemClick = { item ->
                        if (isSelectionMode) {
                            viewModel.toggleSelection(item.id)
                        } else {
                            if (item is AppListItem.File) {
                                viewModel.onFileClicked(item.id)
                            }
                        }
                    },
                    onItemLongClick = { item ->
                        viewModel.toggleSelection(item.id)
                    }
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            SnackbarHost(hostState = snackbarHostState)
        }
    }

    if (pickerAction != null) {
        FolderPickerBottomSheet(
            action = pickerAction!!,
            onDismiss = { pickerAction = null },
            onConfirm = { targetFolderId ->
                viewModel.performAction(pickerAction!!, targetFolderId)
                pickerAction = null
            }
        )
    }
    
    val currentDetails = detailsData
    if (showDetailsDialog && currentDetails != null) {
        DetailsDialog(
            details = currentDetails,
            onDismiss = { 
                showDetailsDialog = false
                detailsData = null
            }
        )
    }
    
    if (showRenameDialog && itemToRename != null) {
        var newName by remember { mutableStateOf(itemToRename!!.name) }
        AlertDialog(
            onDismissRequest = { 
                showRenameDialog = false
                itemToRename = null
            },
            containerColor = SurfaceColor,
            title = { Text("Rename", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = DividerColor
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank() && newName != itemToRename!!.name) {
                        viewModel.renameItem(itemToRename!!.id, newName)
                    }
                    showRenameDialog = false
                    itemToRename = null
                }) {
                    Text("Rename", color = PrimaryBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRenameDialog = false
                    itemToRename = null
                }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

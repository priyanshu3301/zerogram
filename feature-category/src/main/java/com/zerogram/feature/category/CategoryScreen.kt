package com.example.zerogram.ui.category

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import com.example.zerogram.ui.components.AppDropdownMenu
import com.example.zerogram.ui.components.AppDropdownMenuItem
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.zerogram.R
import com.example.zerogram.ui.folder.FileItemData
import com.example.zerogram.ui.folder.FileListItem
import com.example.zerogram.ui.folder.SelectionDetails
import com.example.zerogram.ui.folder.DetailsDialog
import com.example.zerogram.ui.folder.FolderPickerBottomSheet
import com.example.zerogram.ui.folder.BottomBarAction
import com.example.zerogram.ui.search.SortOrder
import com.example.zerogram.LocalSharedTransitionScope
import com.example.zerogram.LocalAnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import com.example.zerogram.SharedBoundsAnimSpec

private val BackgroundColor = Color(0xFF000000)
private val SurfaceColor = Color(0xFF1E1E1E)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFFA0A0A0)
private val DividerColor = Color(0xFF333333)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    categoryName: String,
    viewModel: CategoryViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    LaunchedEffect(categoryName) {
        viewModel.setCategory(categoryName)
    }

    val filesAndFolders by viewModel.filesAndFolders.collectAsState()
    
    var pickerAction by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val selectedItems by viewModel.selectedItems.collectAsState()
    
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    
    var isSelectionMode by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    
    var showDetailsDialog by remember { mutableStateOf(false) }
    var detailsData by remember { mutableStateOf<SelectionDetails?>(null) }
    
    var showRenameDialog by remember { mutableStateOf(false) }
    var itemToRename by remember { mutableStateOf<FileItemData?>(null) }

    if (isSelectionMode) {
        BackHandler {
            isSelectionMode = false
            viewModel.clearSelection()
        }
    }

    if (isSearching) {
        BackHandler {
            isSearching = false
            viewModel.updateSearchQuery("")
        }
    }

    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }
    
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current

    val modifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "category_card_to_screen_$categoryName"),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ -> SharedBoundsAnimSpec },
                resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds()
            )
        }
    } else Modifier

    val listModifier = if (sharedTransitionScope != null) {
        with(sharedTransitionScope) {
            Modifier.skipToLookaheadSize()
        }
    } else Modifier

    val transition = animatedVisibilityScope?.transition
    val isTransitionFinished by remember(transition) {
        derivedStateOf {
            transition == null || transition.currentState == transition.targetState
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .then(modifier)
            .background(BackgroundColor),
        containerColor = BackgroundColor,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (isSelectionMode) {
                val allSelected = selectedItems.size == filesAndFolders.size && filesAndFolders.isNotEmpty()
                TopAppBar(
                    title = {
                        Text(
                            "${selectedItems.size} selected", 
                            color = TextPrimary,
                            fontSize = 18.sp,
                            modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally)
                        )
                    },
                    navigationIcon = {
                        TextButton(onClick = { 
                            isSelectionMode = false
                            viewModel.clearSelection() 
                        }) {
                            Text("Cancel", color = Color(0xFF64B5F6), fontSize = 18.sp)
                        }
                    },
                    actions = {
                        TextButton(onClick = { 
                            if (allSelected) {
                                viewModel.clearSelection()
                            } else {
                                viewModel.selectAll(filesAndFolders.map { it.id }) 
                            }
                        }) {
                            Text(if (allSelected) "Deselect all" else "Select all", color = Color(0xFF64B5F6), fontSize = 18.sp)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = BackgroundColor,
                        titleContentColor = TextPrimary
                    )
                )
            } else if (isSearching) {
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            placeholder = { Text("Search in $categoryName...", color = TextSecondary) },
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
                        IconButton(onClick = { 
                            isSearching = false
                            viewModel.updateSearchQuery("")
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }
                    },
                    actions = {
                        var showSortMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Sort Options", tint = TextPrimary, modifier = Modifier.size(24.dp))
                            }
                            CategorySortMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                                sortOrder = sortOrder,
                                onSortSelected = { order ->
                                    showSortMenu = false
                                    viewModel.setSortOrder(order)
                                }
                            )
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
                        Column {
                            Text(
                                categoryName, 
                                fontWeight = FontWeight.Normal, 
                                color = TextPrimary,
                                fontSize = 20.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            ) 
                            Text(
                                "${filesAndFolders.size} items in total",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearching = true }) {
                            Icon(painterResource(id = R.drawable.ic_thin_search), contentDescription = "Search", tint = TextPrimary, modifier = Modifier.size(24.dp))
                        }
                        var showSortMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More", tint = TextPrimary, modifier = Modifier.size(24.dp))
                            }
                            CategorySortMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                                sortOrder = sortOrder,
                                onSortSelected = { order ->
                                    showSortMenu = false
                                    viewModel.setSortOrder(order)
                                }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = BackgroundColor,
                        titleContentColor = TextPrimary
                    )
                )
            }
        },
        bottomBar = {
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
                            isSelectionMode = false
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
                                            val itemId = selectedItems.first()
                                            itemToRename = filesAndFolders.find { it.id == itemId }
                                            showRenameDialog = true
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
    ) { paddingValues ->
        val displayItems = if (isTransitionFinished) filesAndFolders else emptyList()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .then(listModifier)
        ) {
            items(
                items = displayItems,
                key = { it.id },
                contentType = { if (it.isFolder) "folder" else "file" }
            ) { file ->
                FileListItem(
                    file = file,
                    modifier = Modifier.animateItem(),
                    isSelected = selectedItems.contains(file.id),
                    isSelectionMode = isSelectionMode,
                    onClick = {
                        if (isSelectionMode) {
                            viewModel.toggleSelection(file.id)
                        } else {
                            viewModel.onFileClicked(file.id)
                        }
                    },
                    onLongClick = {
                        if (!isSelectionMode) {
                            isSelectionMode = true
                            viewModel.toggleSelection(file.id)
                        }
                    }
                )
            }
        }
    }

    if (pickerAction != null) {
        FolderPickerBottomSheet(
            action = pickerAction!!,
            onDismiss = { pickerAction = null },
            onConfirm = { targetFolderId ->
                viewModel.performAction(pickerAction!!, targetFolderId)
                pickerAction = null
                isSelectionMode = false
            }
        )
    }

    if (showDetailsDialog && detailsData != null) {
        DetailsDialog(details = detailsData!!, onDismiss = { showDetailsDialog = false })
    }

    if (showRenameDialog && itemToRename != null) {
        var newName by remember { mutableStateOf(itemToRename!!.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { if (it.length <= 50) newName = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = Color(0xFF64B5F6),
                        unfocusedBorderColor = TextSecondary,
                        cursorColor = Color(0xFF64B5F6)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank() && newName != itemToRename!!.name) {
                            viewModel.renameItem(itemToRename!!.id, newName.trim())
                            isSelectionMode = false
                            viewModel.clearSelection()
                        }
                        showRenameDialog = false
                    }
                ) {
                    Text("OK", color = Color(0xFF64B5F6))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRenameDialog = false }
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceColor,
            titleContentColor = TextPrimary
        )
    }
}

@Composable
fun CategorySortMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    sortOrder: SortOrder,
    onSortSelected: (SortOrder) -> Unit
) {
    AppDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.width(220.dp),
        shape = MaterialTheme.shapes.medium,
        containerColor = SurfaceColor
    ) {
        AppDropdownMenuItem(
            text = { Text("Newest first", color = TextPrimary, fontSize = 18.sp) },
            onClick = { onSortSelected(SortOrder.NEWEST_FIRST) },
            trailingIcon = if (sortOrder == SortOrder.NEWEST_FIRST) { { Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF64B5F6)) } } else null,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        )
        HorizontalDivider(color = DividerColor, thickness = 1.dp)
        AppDropdownMenuItem(
            text = { Text("Name A-Z", color = TextPrimary, fontSize = 18.sp) },
            onClick = { onSortSelected(SortOrder.NAME_A_Z) },
            trailingIcon = if (sortOrder == SortOrder.NAME_A_Z) { { Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF64B5F6)) } } else null,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        )
        HorizontalDivider(color = DividerColor, thickness = 1.dp)
        AppDropdownMenuItem(
            text = { Text("Name Z-A", color = TextPrimary, fontSize = 18.sp) },
            onClick = { onSortSelected(SortOrder.NAME_Z_A) },
            trailingIcon = if (sortOrder == SortOrder.NAME_Z_A) { { Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF64B5F6)) } } else null,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        )
        HorizontalDivider(color = DividerColor, thickness = 1.dp)
        AppDropdownMenuItem(
            text = { Text("Largest first", color = TextPrimary, fontSize = 18.sp) },
            onClick = { onSortSelected(SortOrder.LARGEST_FIRST) },
            trailingIcon = if (sortOrder == SortOrder.LARGEST_FIRST) { { Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF64B5F6)) } } else null,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        )
        HorizontalDivider(color = DividerColor, thickness = 1.dp)
        AppDropdownMenuItem(
            text = { Text("Smallest first", color = TextPrimary, fontSize = 18.sp) },
            onClick = { onSortSelected(SortOrder.SMALLEST_FIRST) },
            trailingIcon = if (sortOrder == SortOrder.SMALLEST_FIRST) { { Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF64B5F6)) } } else null,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

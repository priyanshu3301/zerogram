package com.example.zerogram.ui.folder
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ripple
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.LocalIndication
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import com.example.zerogram.ui.components.AppDropdownMenu
import com.example.zerogram.ui.components.AppDropdownMenuItem
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.zerogram.R
import com.example.zerogram.ui.search.SortOrder
import com.example.zerogram.LocalSharedTransitionScope
import com.example.zerogram.LocalAnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import com.example.zerogram.SharedBoundsAnimSpec

// Dark Theme Colors matched to HomeScreen
private val BackgroundColor = Color(0xFF000000)
private val SurfaceColor = Color(0xFF1E1E1E)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFFA0A0A0)
private val DividerColor = Color(0xFF333333)

@Immutable
data class FileItemData(
    val id: String,
    val name: String,
    val date: String,
    val size: String,
    val iconRes: Int,
    val isFolder: Boolean = false,
    val timestamp: Long = 0L,
    val sizeBytes: Long = 0L
)

// dummyFiles removed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(
    initialFolderId: String? = null,
    folderName: String = "Internal Storage",
    viewModel: FolderViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    LaunchedEffect(initialFolderId) {
        if (initialFolderId != null) {
            viewModel.jumpToFolder(initialFolderId)
        }
    }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri ->
            viewModel.handleFileSelection(uri)
        }
    }

    val folderUploadViewModel: FolderUploadViewModel = hiltViewModel()
    val folderUploadState by folderUploadViewModel.uiState.collectAsStateWithLifecycle()

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            folderUploadViewModel.onFolderSelected(uri, initialFolderId)
        }
    }

    val filesAndFolders by viewModel.filesAndFolders.collectAsStateWithLifecycle()
    val breadcrumbs by viewModel.breadcrumbs.collectAsStateWithLifecycle()
    
    var pickerAction by remember { mutableStateOf<String?>(null) }
    val currentFolderName = breadcrumbs.lastOrNull()?.name ?: folderName
    val snackbarHostState = remember { SnackbarHostState() }
    val selectedItems by viewModel.selectedItems.collectAsStateWithLifecycle()
    
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    
    var isSelectionMode by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }

    var showAddMenu by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    
    var showDetailsDialog by remember { mutableStateOf(false) }
    var detailsData by remember { mutableStateOf<SelectionDetails?>(null) }
    
    var showRenameDialog by remember { mutableStateOf(false) }
    var itemToRename by remember { mutableStateOf<FileItemData?>(null) }

    var showNativeFilePicker by remember { mutableStateOf(false) }
    var nativeFilePickerSelectFolder by remember { mutableStateOf(false) }

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

    BackHandler(enabled = !isSelectionMode && !isSearching && breadcrumbs.size > 1) {
        viewModel.navigateBack()
    }

    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }
    
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current

    val modifier = if (sharedTransitionScope != null && animatedVisibilityScope != null && initialFolderId == null) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "storage_card_to_folder"),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ -> SharedBoundsAnimSpec },
                resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds()
            )
        }
    } else Modifier

    val listModifier = if (sharedTransitionScope != null && initialFolderId == null) {
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
            Column {
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
                                placeholder = { Text("Search in ${currentFolderName}...", color = TextSecondary) },
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
                                AppDropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false },
                                    modifier = Modifier.width(220.dp),
                                    shape = MaterialTheme.shapes.medium,
                                    containerColor = SurfaceColor
                                ) {
                                    AppDropdownMenuItem(
                                        text = { Text("Newest first", color = TextPrimary, fontSize = 18.sp) },
                                        onClick = { 
                                            showSortMenu = false
                                            viewModel.setSortOrder(com.example.zerogram.ui.search.SortOrder.NEWEST_FIRST)
                                        },
                                        trailingIcon = if (sortOrder == com.example.zerogram.ui.search.SortOrder.NEWEST_FIRST) { { Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF64B5F6)) } } else null,
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                                    )
                                    HorizontalDivider(color = DividerColor, thickness = 1.dp)
                                    AppDropdownMenuItem(
                                        text = { Text("Name A-Z", color = TextPrimary, fontSize = 18.sp) },
                                        onClick = { 
                                            showSortMenu = false
                                            viewModel.setSortOrder(com.example.zerogram.ui.search.SortOrder.NAME_A_Z)
                                        },
                                        trailingIcon = if (sortOrder == com.example.zerogram.ui.search.SortOrder.NAME_A_Z) { { Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF64B5F6)) } } else null,
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                                    )
                                    HorizontalDivider(color = DividerColor, thickness = 1.dp)
                                    AppDropdownMenuItem(
                                        text = { Text("Name Z-A", color = TextPrimary, fontSize = 18.sp) },
                                        onClick = { 
                                            showSortMenu = false
                                            viewModel.setSortOrder(com.example.zerogram.ui.search.SortOrder.NAME_Z_A)
                                        },
                                        trailingIcon = if (sortOrder == com.example.zerogram.ui.search.SortOrder.NAME_Z_A) { { Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF64B5F6)) } } else null,
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                                    )
                                    HorizontalDivider(color = DividerColor, thickness = 1.dp)
                                    AppDropdownMenuItem(
                                        text = { Text("Largest first", color = TextPrimary, fontSize = 18.sp) },
                                        onClick = { 
                                            showSortMenu = false
                                            viewModel.setSortOrder(com.example.zerogram.ui.search.SortOrder.LARGEST_FIRST)
                                        },
                                        trailingIcon = if (sortOrder == com.example.zerogram.ui.search.SortOrder.LARGEST_FIRST) { { Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF64B5F6)) } } else null,
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                                    )
                                    HorizontalDivider(color = DividerColor, thickness = 1.dp)
                                    AppDropdownMenuItem(
                                        text = { Text("Smallest first", color = TextPrimary, fontSize = 18.sp) },
                                        onClick = { 
                                            showSortMenu = false
                                            viewModel.setSortOrder(com.example.zerogram.ui.search.SortOrder.SMALLEST_FIRST)
                                        },
                                        trailingIcon = if (sortOrder == com.example.zerogram.ui.search.SortOrder.SMALLEST_FIRST) { { Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF64B5F6)) } } else null,
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                                    )
                                }
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
                                    currentFolderName, 
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
                            IconButton(onClick = {
                                if (!viewModel.navigateBack()) {
                                    onNavigateBack()
                                }
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                            }
                        },
                        actions = {
                            Box {
                                IconButton(onClick = { showAddMenu = true }) {
                                    Icon(painterResource(id = R.drawable.ic_thin_add), contentDescription = "Add", tint = TextPrimary, modifier = Modifier.size(24.dp))
                                }
                                AppDropdownMenu(
                                    expanded = showAddMenu,
                                    onDismissRequest = { showAddMenu = false },
                                    modifier = Modifier.width(160.dp),
                                    shape = MaterialTheme.shapes.medium,
                                    containerColor = SurfaceColor
                                ) {
                                    AppDropdownMenuItem(
                                        text = { Text("New folder", color = TextPrimary, fontSize = 18.sp) },
                                        onClick = { 
                                            showAddMenu = false
                                            showNewFolderDialog = true 
                                        },
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                                    )
                                    HorizontalDivider(color = DividerColor, thickness = 1.dp)
                                    AppDropdownMenuItem(
                                        text = { Text("Add files", color = TextPrimary, fontSize = 18.sp) },
                                        onClick = { 
                                            showAddMenu = false
                                            nativeFilePickerSelectFolder = false
                                            showNativeFilePicker = true
                                        },
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                                    )
                                    HorizontalDivider(color = DividerColor, thickness = 1.dp)
                                    AppDropdownMenuItem(
                                        text = { Text("Upload folder", color = TextPrimary, fontSize = 18.sp) },
                                        onClick = { 
                                            showAddMenu = false
                                            nativeFilePickerSelectFolder = true
                                            showNativeFilePicker = true
                                        },
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                                    )
                                }
                            }
                            IconButton(onClick = { isSearching = true }) {
                                Icon(painterResource(id = R.drawable.ic_thin_search), contentDescription = "Search", tint = TextPrimary, modifier = Modifier.size(24.dp))
                            }
                            var showSortMenuNormal by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { showSortMenuNormal = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "More", tint = TextPrimary, modifier = Modifier.size(24.dp))
                                }
                                AppDropdownMenu(
                                    expanded = showSortMenuNormal,
                                    onDismissRequest = { showSortMenuNormal = false },
                                    modifier = Modifier.width(220.dp),
                                    shape = MaterialTheme.shapes.medium,
                                    containerColor = SurfaceColor
                                ) {
                                    AppDropdownMenuItem(
                                        text = { Text("Newest first", color = TextPrimary, fontSize = 18.sp) },
                                        onClick = { 
                                            showSortMenuNormal = false
                                            viewModel.setSortOrder(SortOrder.NEWEST_FIRST)
                                        },
                                        trailingIcon = if (sortOrder == SortOrder.NEWEST_FIRST) { { Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF64B5F6)) } } else null,
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                                    )
                                    HorizontalDivider(color = DividerColor, thickness = 1.dp)
                                    AppDropdownMenuItem(
                                        text = { Text("Name A-Z", color = TextPrimary, fontSize = 18.sp) },
                                        onClick = { 
                                            showSortMenuNormal = false
                                            viewModel.setSortOrder(SortOrder.NAME_A_Z)
                                        },
                                        trailingIcon = if (sortOrder == SortOrder.NAME_A_Z) { { Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF64B5F6)) } } else null,
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                                    )
                                    HorizontalDivider(color = DividerColor, thickness = 1.dp)
                                    AppDropdownMenuItem(
                                        text = { Text("Name Z-A", color = TextPrimary, fontSize = 18.sp) },
                                        onClick = { 
                                            showSortMenuNormal = false
                                            viewModel.setSortOrder(SortOrder.NAME_Z_A)
                                        },
                                        trailingIcon = if (sortOrder == SortOrder.NAME_Z_A) { { Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF64B5F6)) } } else null,
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                                    )
                                    HorizontalDivider(color = DividerColor, thickness = 1.dp)
                                    AppDropdownMenuItem(
                                        text = { Text("Largest first", color = TextPrimary, fontSize = 18.sp) },
                                        onClick = { 
                                            showSortMenuNormal = false
                                            viewModel.setSortOrder(SortOrder.LARGEST_FIRST)
                                        },
                                        trailingIcon = if (sortOrder == SortOrder.LARGEST_FIRST) { { Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF64B5F6)) } } else null,
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                                    )
                                    HorizontalDivider(color = DividerColor, thickness = 1.dp)
                                    AppDropdownMenuItem(
                                        text = { Text("Smallest first", color = TextPrimary, fontSize = 18.sp) },
                                        onClick = { 
                                            showSortMenuNormal = false
                                            viewModel.setSortOrder(SortOrder.SMALLEST_FIRST)
                                        },
                                        trailingIcon = if (sortOrder == SortOrder.SMALLEST_FIRST) { { Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF64B5F6)) } } else null,
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
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

                // Breadcrumbs Header
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(breadcrumbs.size) { index ->
                        val breadcrumb = breadcrumbs[index]
                        val isLast = index == breadcrumbs.size - 1
                        val color = if (isLast) Color(0xFF64B5F6) else TextSecondary
                        Text(
                            text = breadcrumb.name,
                            color = color,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!isLast) {
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.padding(horizontal = 4.dp).size(16.dp)
                            )
                        }
                    }
                }
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
                            if (!file.isFolder) {
                                viewModel.onFileClicked(file.id)
                            } else {
                                viewModel.navigateToFolder(file.id, file.name)
                            }
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

    FolderUploadDialog(
        state = folderUploadState,
        onUpdateOptions = { folderUploadViewModel.updateScanOptions(it) },
        onStartScan = { folderUploadViewModel.startScan(initialFolderId) },
        onCancel = { folderUploadViewModel.cancelScan() },
        onUpload = { folderUploadViewModel.commitUpload() },
        onDismissError = { folderUploadViewModel.dismissError() }
    )

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

    if (showNativeFilePicker) {
        NativeFilePickerDialog(
            onDismiss = { showNativeFilePicker = false },
            onFileSelected = { file ->
                showNativeFilePicker = false
                viewModel.handleNativeFileSelection(file)
            },
            onFolderSelected = { file ->
                showNativeFilePicker = false
                folderUploadViewModel.onNativeFolderSelected(file, initialFolderId)
            },
            selectFolder = nativeFilePickerSelectFolder
        )
    }

    if (showDetailsDialog && detailsData != null) {
        DetailsDialog(details = detailsData!!, onDismiss = { showDetailsDialog = false })
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
                        if (newFolderName.isNotBlank()) {
                            viewModel.createNewFolder(newFolderName.trim())
                            newFolderName = ""
                            showNewFolderDialog = false
                        }
                    }
                ) {
                    Text("OK", color = Color(0xFF64B5F6))
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
fun BottomBarAction(iconRes: Int, label: String, enabled: Boolean = true, onClick: () -> Unit = {}) {
    val alpha = if (enabled) 1f else 0.2f
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(8.dp)
            .alpha(alpha)
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = label,
            tint = TextPrimary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, color = TextPrimary, fontSize = 12.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    file: FileItemData,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(if (isSelected) Color(0xFF1A1A1A) else Color.Transparent)
            .combinedClickable(
                indication = ripple(color = TextSecondary),
                interactionSource = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // File Icon
            Icon(
                painter = painterResource(id = file.iconRes),
                contentDescription = file.name,
                tint = Color.Unspecified,
                modifier = Modifier.size(44.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // File Details
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = file.name, 
                    color = TextPrimary, 
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2, // Allow wrapping to 2 lines per audit
                    // No Ellipsis per audit, let it truncate naturally or fit within 2 lines
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (file.isFolder) file.date else "${file.size}  •  ${file.date}", 
                    color = TextSecondary, 
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // Trailing icon/checkbox reserved container
            Box(
                modifier = Modifier.width(32.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF1E88E5),
                            uncheckedColor = TextSecondary,
                            checkmarkColor = Color.White
                        ),
                        modifier = Modifier.size(24.dp)
                    )
                } else if (file.isFolder) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }


        // Divider
        HorizontalDivider(
            modifier = Modifier.padding(start = 80.dp),
            thickness = 1.dp,
            color = DividerColor
        )
    }
}

@Composable
fun DetailsDialog(details: SelectionDetails, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF262626), MaterialTheme.shapes.medium)
                    .padding(24.dp)
            ) {
                Text(
                    text = details.title,
                    color = Color.White,
                    fontSize = 20.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                if (!details.isMultiple) {
                    Text("Name", color = Color(0xFFA0A0A0), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(details.name ?: "", color = Color.White, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text("Date modified", color = Color(0xFFA0A0A0), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(details.dateModified ?: "", color = Color.White, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                Text("Size", color = Color(0xFFA0A0A0), fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(details.sizeText, color = Color.White, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(16.dp))
                
                if (details.itemsText != null) {
                    Text("Items", color = Color(0xFFA0A0A0), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(details.itemsText, color = Color.White, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                if (!details.isMultiple && details.location != null) {
                    Text("Location", color = Color(0xFFA0A0A0), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(details.location, color = Color(0xFF1E88E5), fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(24.dp))
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Cancel", color = Color(0xFF1E88E5), fontSize = 18.sp)
                }
            }
        }
    }
}


package com.zerogram.feature.folder
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.*
import com.zerogram.core.ui.components.AppDropdownMenu
import com.zerogram.core.ui.components.AppDropdownMenuItem
import com.zerogram.core.ui.components.AppList
import com.zerogram.core.ui.components.ImmutableListWrapper
import com.zerogram.core.ui.components.AppListItem
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.hilt.navigation.compose.hiltViewModel
import com.zerogram.core.ui.R
import com.zerogram.core.ui.components.SortOrder
import com.zerogram.core.ui.components.SelectionDetails
import com.zerogram.core.ui.animation.ZerogramTransitionPhysics.bottomBarEnter
import com.zerogram.core.ui.animation.ZerogramTransitionPhysics.bottomBarExit
import com.zerogram.core.ui.animation.ZerogramTransitionPhysics.topBarEnter
import com.zerogram.core.ui.animation.ZerogramTransitionPhysics.topBarExit
import androidx.activity.compose.BackHandler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentlyDeletedScreen(
    onNavigateBack: () -> Unit,
    viewModel: RecentlyDeletedViewModel = hiltViewModel()
) {
    val deletedItems by viewModel.deletedItems.collectAsStateWithLifecycle()
    val selectedItems by viewModel.selectedItems.collectAsStateWithLifecycle()
    var isSelectionMode by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    
    var showDetailsDialog by remember { mutableStateOf(false) }
    var detailsData by remember { mutableStateOf<SelectionDetails?>(null) }
    
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

    BackHandler(enabled = !isSelectionMode && !isSearching) {
        onNavigateBack()
    }
    
    val BackgroundColor = Color(0xFF000000)
    val SurfaceColor = Color(0xFF1E1E1E)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFA0A0A0)

    val renderer = remember(isSelectionMode, isSearching, selectedItems, deletedItems, searchQuery, sortOrder) {
        object : com.zerogram.core.ui.scaffold.ScaffoldRenderer {
            @Composable
            override fun TopBar() {
            AnimatedContent(
                targetState = when {
                    isSelectionMode -> 2
                    isSearching -> 1
                    else -> 0
                },
                label = "topBarAnimation",
                transitionSpec = {
                    topBarEnter.togetherWith(topBarExit)
                }
            ) { state ->
                if (state == 2) {
                TopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text("${selectedItems.size} selected", color = TextPrimary, fontSize = 20.sp)
                            Text("${deletedItems.size} items in total", color = TextSecondary, fontSize = 14.sp)
                        }
                    },
                    navigationIcon = {
                        TextButton(onClick = { 
                            isSelectionMode = false
                            viewModel.clearSelection()
                        }) {
                            Text("Cancel", color = Color(0xFF1E88E5), fontSize = 16.sp)
                        }
                    },
                    actions = {
                        val isAllSelected = selectedItems.size == deletedItems.size && deletedItems.isNotEmpty()
                        TextButton(onClick = { 
                            if (isAllSelected) viewModel.clearSelection() else viewModel.selectAll()
                        }) {
                            Text(if (isAllSelected) "Deselect All" else "Select All", color = Color.White, fontSize = 16.sp)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceColor)
                )
            } else if (state == 1) {
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            placeholder = { Text("Search deleted items...", color = Color(0xFF888888)) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = Color(0xFF1E88E5)
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
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceColor)
                )
            } else {
                var showMenu by remember { mutableStateOf(false) }
                TopAppBar(
                    title = { Text("Recently Deleted", color = TextPrimary) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearching = true }) {
                            Icon(painterResource(id = R.drawable.ic_thin_search), contentDescription = "Search", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(painterResource(id = R.drawable.ic_thin_more), contentDescription = "More", tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                            AppDropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier.width(220.dp),
                                shape = MaterialTheme.shapes.medium,
                                containerColor = SurfaceColor
                            ) {
                                AppDropdownMenuItem(
                                    text = { Text("Newest first", color = TextPrimary, fontSize = 18.sp) },
                                    onClick = {
                                        viewModel.setSortOrder(SortOrder.NEWEST_FIRST)
                                        showMenu = false
                                    },
                                    trailingIcon = if (sortOrder == SortOrder.NEWEST_FIRST) { @androidx.compose.runtime.Composable { Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF64B5F6)) } } else null,
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                                )
                                HorizontalDivider(color = Color(0xFF333333), thickness = 1.dp)
                                AppDropdownMenuItem(
                                    text = { Text("Name A-Z", color = TextPrimary, fontSize = 18.sp) },
                                    onClick = {
                                        viewModel.setSortOrder(SortOrder.NAME_A_Z)
                                        showMenu = false
                                    },
                                    trailingIcon = if (sortOrder == SortOrder.NAME_A_Z) { @androidx.compose.runtime.Composable { Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF64B5F6)) } } else null,
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                                )
                                HorizontalDivider(color = Color(0xFF333333), thickness = 1.dp)
                                AppDropdownMenuItem(
                                    text = { Text("Name Z-A", color = TextPrimary, fontSize = 18.sp) },
                                    onClick = {
                                        viewModel.setSortOrder(SortOrder.NAME_Z_A)
                                        showMenu = false
                                    },
                                    trailingIcon = if (sortOrder == SortOrder.NAME_Z_A) { @androidx.compose.runtime.Composable { Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF64B5F6)) } } else null,
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                                )
                                HorizontalDivider(color = Color(0xFF333333), thickness = 1.dp)
                                AppDropdownMenuItem(
                                    text = { Text("Largest first", color = TextPrimary, fontSize = 18.sp) },
                                    onClick = {
                                        viewModel.setSortOrder(SortOrder.LARGEST_FIRST)
                                        showMenu = false
                                    },
                                    trailingIcon = if (sortOrder == SortOrder.LARGEST_FIRST) { @androidx.compose.runtime.Composable { Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF64B5F6)) } } else null,
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                                )
                                HorizontalDivider(color = Color(0xFF333333), thickness = 1.dp)
                                AppDropdownMenuItem(
                                    text = { Text("Smallest first", color = TextPrimary, fontSize = 18.sp) },
                                    onClick = {
                                        viewModel.setSortOrder(SortOrder.SMALLEST_FIRST)
                                        showMenu = false
                                    },
                                    trailingIcon = if (sortOrder == SortOrder.SMALLEST_FIRST) { @androidx.compose.runtime.Composable { Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF64B5F6)) } } else null,
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                                )
                                HorizontalDivider(color = Color(0xFF333333), thickness = 1.dp)
                                AppDropdownMenuItem(
                                    text = { Text("Select Items", color = TextPrimary, fontSize = 18.sp) },
                                    onClick = {
                                        isSelectionMode = true
                                        showMenu = false
                                    },
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                                )
                                HorizontalDivider(color = Color(0xFF333333), thickness = 1.dp)
                                AppDropdownMenuItem(
                                    text = { Text("Empty Bin", color = TextPrimary, fontSize = 18.sp) },
                                    onClick = {
                                        viewModel.deleteAll()
                                        showMenu = false
                                    },
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceColor)
                )
            }
            } // Close AnimatedContent
            } // Close TopBar

            @Composable
            override fun BottomBar() {
            AnimatedVisibility(
                visible = isSelectionMode,
                enter = bottomBarEnter,
                exit = bottomBarExit
            ) {
                BottomAppBar(
                    containerColor = SurfaceColor,
                    contentColor = Color.White
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        IconButton(
                            onClick = {
                                viewModel.recoverSelected()
                                isSelectionMode = false
                            },
                            enabled = selectedItems.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Refresh, 
                                    contentDescription = "Recover",
                                    tint = if (selectedItems.isNotEmpty()) Color(0xFF1E88E5) else Color(0xFF555555),
                                    modifier = Modifier.size(24.dp)
                                )
                                Text("Recover", fontSize = 12.sp, color = if (selectedItems.isNotEmpty()) Color(0xFF1E88E5) else Color(0xFF555555))
                            }
                        }
                        IconButton(
                            onClick = {
                                viewModel.deleteSelectedPermanently()
                                isSelectionMode = false
                            },
                            enabled = selectedItems.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    painterResource(id = R.drawable.ic_thin_delete), 
                                    contentDescription = "Delete",
                                    tint = if (selectedItems.isNotEmpty()) Color(0xFFE53935) else Color(0xFF555555),
                                    modifier = Modifier.size(24.dp)
                                )
                                Text("Delete", fontSize = 12.sp, color = if (selectedItems.isNotEmpty()) Color(0xFFE53935) else Color(0xFF555555))
                            }
                        }
                        IconButton(
                            onClick = {
                                viewModel.getSelectionDetails { details ->
                                    detailsData = details
                                    showDetailsDialog = true
                                }
                            },
                            enabled = selectedItems.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    painterResource(id = R.drawable.ic_thin_more), 
                                    contentDescription = "Details",
                                    tint = if (selectedItems.isNotEmpty()) Color.White else Color(0xFF555555),
                                    modifier = Modifier.size(24.dp)
                                )
                                Text("Details", fontSize = 12.sp, color = if (selectedItems.isNotEmpty()) Color.White else Color(0xFF555555))
                            }
                        }
                    }
                }
            }
            }
        }
    }

    Scaffold(
        topBar = { renderer.TopBar() },
        bottomBar = { renderer.BottomBar() },
        containerColor = BackgroundColor,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (deletedItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No recently deleted items", color = TextSecondary, fontSize = 16.sp)
                }
            } else {
                AppList(
                    items = ImmutableListWrapper(deletedItems),
                    selectedItems = selectedItems,
                    isSelectionMode = isSelectionMode,
                    onItemClick = { item ->
                        if (isSelectionMode) {
                            viewModel.toggleSelection(item.id)
                        } else {
                            // Open details or do nothing for single click in Recently Deleted if not in selection mode
                        }
                    },
                    onItemLongClick = { item ->
                        if (!isSelectionMode) {
                            isSelectionMode = true
                            viewModel.toggleSelection(item.id)
                        }
                    }
                )
            }
        }
        }

    val currentDetails = detailsData
    if (showDetailsDialog && currentDetails != null) {
        AlertDialog(
            onDismissRequest = { showDetailsDialog = false },
            title = { Text(currentDetails.title, color = Color.White) },
            text = {
                Column {
                    if (!currentDetails.isMultiple && currentDetails.name != null) {
                        Text("Name: ${currentDetails.name}", color = Color.White)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    if (currentDetails.dateModified != null) {
                        Text("Deleted: ${currentDetails.dateModified}", color = Color(0xFFA0A0A0))
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text("Size: ${currentDetails.sizeText}", color = Color(0xFFA0A0A0))
                    
                    val itemsText = currentDetails.itemsText
                    if (itemsText != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Contains: ${itemsText}", color = Color(0xFFA0A0A0))
                    }
                    val location = currentDetails.location
                    if (location != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Location: ${location}", color = Color(0xFFA0A0A0))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetailsDialog = false }) {
                    Text("OK", color = Color(0xFF1E88E5))
                }
            },
            containerColor = SurfaceColor
        )
    }
}

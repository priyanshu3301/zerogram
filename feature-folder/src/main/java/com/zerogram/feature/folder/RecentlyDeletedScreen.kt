package com.example.zerogram.ui.folder
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.*
import com.example.zerogram.ui.components.AppDropdownMenu
import com.example.zerogram.ui.components.AppDropdownMenuItem
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.zerogram.R
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.log10
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    
    // Automatically turn off selection mode if we leave the screen or clear manually?
    // Actually, we want it to stay open until cancelled.
    
    var showDetailsDialog by remember { mutableStateOf(false) }
    var detailsData by remember { mutableStateOf<SelectionDetails?>(null) }
    
    if (isSelectionMode) {
        androidx.activity.compose.BackHandler {
            isSelectionMode = false
            viewModel.clearSelection()
        }
    }
    
    if (isSearching) {
        androidx.activity.compose.BackHandler {
            isSearching = false
            viewModel.updateSearchQuery("")
        }
    }

    androidx.activity.compose.BackHandler(enabled = !isSelectionMode && !isSearching) {
        onNavigateBack()
    }
    
    val BackgroundColor = Color(0xFF000000)
    val SurfaceColor = Color(0xFF1E1E1E)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFA0A0A0)
    val DividerColor = Color(0xFF333333)

    Scaffold(
        containerColor = BackgroundColor,
        topBar = {
            if (isSelectionMode) {
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
                            Text("Cancel", color = Color(0xFF1E88E5), fontSize = 18.sp)
                        }
                    },
                    actions = {
                        val allSelected = selectedItems.size == deletedItems.size
                        TextButton(onClick = { if (allSelected) viewModel.clearSelection() else viewModel.selectAll() }) {
                            Text(if (allSelected) "Deselect all" else "Select all", color = Color(0xFF1E88E5), fontSize = 18.sp)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundColor)
                )
            } else if (isSearching) {
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            placeholder = { Text("Search...", color = TextSecondary) },
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
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundColor)
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text("Recently deleted", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Normal)
                            val itemCount = deletedItems.size
                            Text(
                                if (itemCount == 1) "1 item in total" else "$itemCount items in total",
                                color = TextSecondary, fontSize = 14.sp
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearching = true }) {
                            Icon(painter = painterResource(id = R.drawable.ic_thin_search), contentDescription = "Search", tint = TextPrimary)
                        }
                        IconButton(onClick = { /* TODO */ }) {
                            Icon(imageVector = Icons.Default.List, contentDescription = "View", tint = TextPrimary)
                        }
                        IconButton(onClick = { /* TODO */ }) {
                            Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More", tint = TextPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundColor)
                )
            }
        },
        bottomBar = {
            BottomAppBar(
                containerColor = SurfaceColor,
                contentColor = TextPrimary
            ) {
                if (isSelectionMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { 
                                viewModel.getSelectionDetails { details ->
                                    detailsData = details
                                    showDetailsDialog = true
                                }
                            }.padding(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Info, contentDescription = "Details", tint = TextPrimary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Details", color = TextPrimary, fontSize = 12.sp)
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { viewModel.recoverSelected() }.padding(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Recover", tint = TextPrimary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Recover", color = TextPrimary, fontSize = 12.sp)
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { viewModel.deleteSelectedPermanently() }.padding(8.dp)
                        ) {
                            Icon(painter = painterResource(id = R.drawable.ic_thin_delete), contentDescription = "Delete", tint = TextPrimary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Delete", color = TextPrimary, fontSize = 12.sp)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { viewModel.deleteAll() }.padding(8.dp)
                        ) {
                            Icon(painter = painterResource(id = R.drawable.ic_thin_delete), contentDescription = "Delete all", tint = TextPrimary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Delete all", color = TextPrimary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Text(
                text = "Deleted files are retained for only 30 days.",
                color = TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(
                    items = deletedItems,
                    key = { it.id },
                    contentType = { if (it.isFolder) "folder" else "file" }
                ) { item ->
                    DeletedItemRow(
                        item = item,
                        dividerColor = DividerColor,
                        isSelected = selectedItems.contains(item.id),
                        isSelectionMode = isSelectionMode,
                        onClick = {
                            if (isSelectionMode) {
                                viewModel.toggleSelection(item.id)
                            } else {
                                // TODO: Maybe preview file or show options
                            }
                        },
                        onLongClick = {
                            if (!isSelectionMode) {
                                isSelectionMode = true
                                viewModel.toggleSelection(item.id)
                            }
                        }
                    )
                }
            }
        }
    }
    
    if (showDetailsDialog && detailsData != null) {
        DetailsDialog(details = detailsData!!, onDismiss = { showDetailsDialog = false })
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DeletedItemRow(
    item: DeletedItemData, 
    dividerColor: Color,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000
    val currentTime = System.currentTimeMillis()
    val daysRemaining = maxOf(0L, (THIRTY_DAYS_MS - (currentTime - item.deletedAt)) / (24 * 60 * 60 * 1000))

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .background(if (isSelected) Color(0xFF1A1A1A) else Color.Transparent)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
        val iconRes = item.iconRes
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                color = Color.White,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            val infoText = "$daysRemaining days remaining | ${item.sizeText} | ${item.dateText}"

            Text(
                text = infoText,
                color = Color(0xFFA0A0A0),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isSelectionMode) {
            Spacer(modifier = Modifier.width(16.dp))
            Checkbox(
                checked = isSelected,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(
                    checkedColor = Color(0xFF1E88E5),
                    uncheckedColor = Color(0xFFA0A0A0),
                    checkmarkColor = Color.White
                ),
                modifier = Modifier.size(24.dp)
            )
        }
    } // End of Row
    } // End of Column
    HorizontalDivider(modifier = Modifier.padding(start = 80.dp), color = dividerColor, thickness = 1.dp)
}


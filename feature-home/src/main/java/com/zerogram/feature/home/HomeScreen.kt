package com.zerogram.feature.home

import com.zerogram.core.ui.navigation.SharedBoundsAnimSpec
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.*
import com.zerogram.core.ui.components.AppDropdownMenu
import com.zerogram.core.ui.components.AppDropdownMenuItem
import androidx.compose.runtime.*
import com.zerogram.feature.folder.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.activity.compose.BackHandler
import com.zerogram.core.ui.R
import androidx.hilt.navigation.compose.hiltViewModel
import com.zerogram.core.ui.navigation.LocalSharedTransitionScope
import com.zerogram.core.ui.navigation.LocalAnimatedVisibilityScope
import com.zerogram.core.ui.navigation.SharedBoundsAnimSpec

// Dark Theme Colors based on screenshot
// Constants removed in favor of MaterialTheme.colorScheme

// Category Colors
private val ColorPhotos = Color(0xFF3B82F6)
private val ColorVideos = Color(0xFF8B5CF6)
private val ColorAudio = Color(0xFFF97316)
private val ColorDocs = Color(0xFF0EA5E9)
private val ColorAPKs = Color(0xFF22C55E)
private val ColorArchives = Color(0xFF92400E)
private val ColorApps = Color(0xFF3B82F6)

// Hoisted list removed in favor of ViewModel state

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onStorageClick: () -> Unit = {},
    onSwitchVaultClick: () -> Unit = {},
    onCategoryClick: (String) -> Unit = {},
    onRecentlyDeletedClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onNavigateToFolder: (String) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    
    val vaultName by viewModel.vaultName.collectAsState()
    val totalStorageString by viewModel.totalStorageString.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val deletedStats by viewModel.deletedStats.collectAsState()

    BackHandler(enabled = pagerState.currentPage == 1) {
        coroutineScope.launch {
            pagerState.animateScrollToPage(0)
        }
    }

    var expanded by remember { mutableStateOf(false) }

    val renderer = remember(pagerState.currentPage, vaultName, scrollBehavior, deletedStats, expanded, totalStorageString) {
        object : com.zerogram.core.ui.scaffold.ScaffoldRenderer {
            @Composable
            override fun TopBar() {
                if (pagerState.currentPage == 0) {
                Box {
                    LargeTopAppBar(
                        title = {
                            // Intentionally left empty!
                            // This prevents Material 3 from rendering two texts and crossfading them (the "double shadow" issue).
                        },
                        actions = {
                            IconButton(onClick = onSearchClick) {
                                Icon(painter = painterResource(id = R.drawable.ic_thin_search), contentDescription = "Search", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(24.dp))
                            }
                            Box {
                                IconButton(onClick = { expanded = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(28.dp))
                                }
                                AppDropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false },
                                    modifier = Modifier.width(160.dp),
                                    shape = MaterialTheme.shapes.medium,
                                    containerColor = MaterialTheme.colorScheme.surface
                                ) {
                                    AppDropdownMenuItem(
                                        text = { Text("Switch Vault", color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp) },
                                        onClick = { 
                                            expanded = false 
                                            onSwitchVaultClick()
                                        },
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            scrolledContainerColor = MaterialTheme.colorScheme.background,
                        ),
                        scrollBehavior = scrollBehavior
                    )

                    // Our custom, single hardware-accelerated text layered on top.
                    // It smoothly translates and shrinks without fading or swapping.
                    val collapsedFraction = scrollBehavior.state.collapsedFraction
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    val expandedYPx = with(density) { 84.dp.toPx() }
                    val collapsedYPx = with(density) { 16.dp.toPx() }
                    Text(
                        text = vaultName,
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .windowInsetsPadding(TopAppBarDefaults.windowInsets)
                            .padding(start = 16.dp)
                            .graphicsLayer {
                                // Scale down from 1.0 (32sp) to 0.75 (24sp)
                                val scale = 1f - (0.25f * collapsedFraction)
                                scaleX = scale
                                scaleY = scale
                                
                                // Move the text UP smoothly as it shrinks
                                translationY = expandedYPx + (collapsedYPx - expandedYPx) * collapsedFraction
                                
                                // Ensures it scales from the top-left edge naturally
                                transformOrigin = TransformOrigin(0f, 0f)
                            }
                    )
                }
                } // End if (pagerState.currentPage == 0)
            }

            @Composable
            override fun BottomBar() {
                Column(modifier = Modifier.background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.navigationBars)) {
                    // Custom Navigation Bar to replicate TabRow-style indicator
                    TabRow(
                        selectedTabIndex = pagerState.currentPage,
                        modifier = Modifier.height(64.dp),
                        containerColor = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                        indicator = { tabPositions ->
                            Box(
                                modifier = Modifier
                                    .tabIndicatorOffset(tabPositions[pagerState.currentPage])
                                    .height(3.dp)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            )
                        },
                        divider = {}
                    ) {
                        Tab(
                            selected = pagerState.currentPage == 0,
                            onClick = { coroutineScope.launch { pagerState.scrollToPage(0) } },
                            selectedContentColor = MaterialTheme.colorScheme.onBackground,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            text = { Text("Files") },
                            icon = { Icon(Icons.Default.Folder, contentDescription = "Files", modifier = Modifier.size(24.dp)) }
                        )
                        Tab(
                            selected = pagerState.currentPage == 1,
                            onClick = { coroutineScope.launch { pagerState.scrollToPage(1) } },
                            selectedContentColor = MaterialTheme.colorScheme.onBackground,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            text = { Text("Transfers") },
                            icon = { Icon(Icons.Outlined.SwapVert, contentDescription = "Transfers", modifier = Modifier.size(24.dp)) }
                        )
                    }
                }
            }
        }
    }
    
    com.zerogram.core.ui.scaffold.ScreenScaffoldConfig(renderer)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .background(MaterialTheme.colorScheme.background)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
        ) { page ->
            when (page) {
                0 -> HomeFilesContent(
                    totalStorageString = totalStorageString,
                    categories = categories,
                    deletedStats = deletedStats,
                    onStorageClick = onStorageClick, 
                    onCategoryClick = onCategoryClick,
                    onRecentlyDeletedClick = onRecentlyDeletedClick
                )
                1 -> com.zerogram.feature.transfers.TransfersScreen(
                    onNavigateBack = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                    onNavigateToFolder = onNavigateToFolder
                )
            }
        }
    }
}

@Composable
fun HomeFilesContent(
    totalStorageString: String,
    categories: List<CategoryStats>,
    deletedStats: String,
    onStorageClick: () -> Unit = {},
    onCategoryClick: (String) -> Unit = {},
    onRecentlyDeletedClick: () -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
    ) {
        item {
            StorageCard(
                totalStorageString = totalStorageString,
                onClick = onStorageClick
            )
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }

        item {
            CategoryGrid(categories = categories, onCategoryClick = onCategoryClick)
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onRecentlyDeletedClick() }
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Recently deleted",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Recently deleted", color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(deletedStats, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun StorageCard(
    totalStorageString: String,
    onClick: () -> Unit = {}
) {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
    
    val modifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "storage_card_to_folder"),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ -> SharedBoundsAnimSpec },
                resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds()
            )
        }
    } else Modifier

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .then(modifier)
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
            .clickable(
                indication = ripple(color = MaterialTheme.colorScheme.onSurfaceVariant),
                interactionSource = null,
                onClick = onClick
            )
            .padding(20.dp)
    ) {
        Text("All files", color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(32.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                totalStorageString, 
                style = MaterialTheme.typography.headlineLarge, 
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(" | ∞", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 24.sp, modifier = Modifier.padding(bottom = 2.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(Color(0xFF333333), MaterialTheme.shapes.medium)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .fillMaxHeight()
                    .background(Color(0xFF64B5F6), MaterialTheme.shapes.medium)
            )
        }
    }
}

@Composable
fun CategoryGrid(categories: List<CategoryStats>, onCategoryClick: (String) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        val rows = categories.chunked(3)
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for (item in rowItems) {
                    Box(modifier = Modifier.weight(1f)) {
                        CategoryItem(item, onCategoryClick)
                    }
                }
                // Fill empty spaces in the last row
                val emptySpaces = 3 - rowItems.size
                for (i in 0 until emptySpaces) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun CategoryItem(data: CategoryStats, onCategoryClick: (String) -> Unit) {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
    
    val modifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "category_card_to_screen_${data.title}"),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ -> SharedBoundsAnimSpec },
                resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds()
            )
        }
    } else Modifier

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .then(modifier)
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
            .clickable(
                indication = ripple(color = MaterialTheme.colorScheme.onSurfaceVariant),
                interactionSource = null,
                onClick = { onCategoryClick(data.title) }
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(id = data.iconRes),
            contentDescription = data.title,
            tint = Color.Unspecified,
            modifier = Modifier.size(44.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(data.title, color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp)
        Text(data.count, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}
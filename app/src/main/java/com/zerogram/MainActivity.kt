package com.zerogram

import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.SystemBarStyle
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.metrics.performance.JankStats
import androidx.metrics.performance.PerformanceMetricsState
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zerogram.navigation.NavigationRoutes
import com.zerogram.feature.category.CategoryScreen
import com.zerogram.feature.folder.FolderScreen
import com.zerogram.feature.folder.RecentlyDeletedScreen
import com.zerogram.feature.home.HomeScreen
import com.zerogram.feature.login.LoginScreen
import com.zerogram.feature.search.SearchScreen
import com.zerogram.core.ui.theme.ZerogramTheme
import com.zerogram.feature.vault.VaultCreateScreen
import com.zerogram.feature.vault.VaultSelectionScreen
import com.zerogram.feature.vault.VaultUnlockScreen
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import dagger.hilt.android.AndroidEntryPoint
import java.net.URLDecoder
import java.net.URLEncoder
import com.zerogram.core.ui.navigation.LocalAnimatedVisibilityScope
import com.zerogram.core.ui.navigation.LocalSharedTransitionScope
import com.zerogram.core.ui.navigation.SharedBoundsAnimSpec
import com.zerogram.core.ui.scaffold.AppScaffold

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var jankStats: JankStats
    private val jankRingBuffer = ArrayDeque<String>(50)

    override fun onResume() {
        super.onResume()
        if (::jankStats.isInitialized) {
            jankStats.isTrackingEnabled = true
        }
    }

    override fun onPause() {
        super.onPause()
        if (::jankStats.isInitialized) {
            jankStats.isTrackingEnabled = false
            val snapshot = jankRingBuffer.toList()
            jankRingBuffer.clear()
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                snapshot.forEach { android.util.Log.d("ZerogramJank", it) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        
        splashScreen.setKeepOnScreenCondition {
            viewModel.startDestination.value == null
        }
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = android.net.Uri.parse("package:${packageName}")
                }
                startActivity(intent)
            }
        }
        // Force highest supported refresh rate (120Hz if available)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.supportedModes?.maxByOrNull { it.refreshRate }?.let { mode ->
                window.attributes = window.attributes.apply {
                    preferredDisplayModeId = mode.modeId
                }
            }
        }
        
        val jankFrameListener = JankStats.OnFrameListener { frameData ->
            if (frameData.isJank) {
                val route = frameData.states.find { it.key == "route" }?.value ?: "Unknown"
                if (jankRingBuffer.size >= 50) jankRingBuffer.removeFirst()
                jankRingBuffer.addLast("Jank ${frameData.frameDurationUiNanos / 1_000_000}ms @ $route")
            }
        }
        jankStats = JankStats.createAndTrack(window, jankFrameListener)

        setContent {
            ZerogramTheme {
                val startDestination by viewModel.startDestination.collectAsState()
                
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    if (startDestination != null) {
                        androidx.compose.runtime.key(startDestination) {
                            val navController = rememberNavController()
                            
                            val metricsStateHolder = androidx.compose.runtime.remember {
                                PerformanceMetricsState.getHolderForHierarchy(window.decorView)
                            }
                            val currentBackStackEntry by navController.currentBackStackEntryAsState()
                            val currentRoute = currentBackStackEntry?.destination?.route ?: "Unknown"
                            
                            LaunchedEffect(currentRoute) {
                                metricsStateHolder.state?.putState("route", currentRoute)
                            }
                            
                            SharedTransitionLayout {
                            CompositionLocalProvider(
                                LocalSharedTransitionScope provides this@SharedTransitionLayout
                            ) {
                                AppScaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                                    NavHost(
                                        modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
                                        navController = navController,
                                        startDestination = if (startDestination == "Home") NavigationRoutes.HOME else NavigationRoutes.LOGIN,
                                    enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(250, easing = LinearOutSlowInEasing)) },
                                    exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(250, easing = FastOutLinearInEasing)) },
                                    popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(250, easing = LinearOutSlowInEasing)) },
                                    popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(250, easing = FastOutLinearInEasing)) }
                                ) {
                            composable(
                                NavigationRoutes.LOGIN,
                                enterTransition = { fadeIn(animationSpec = tween(250)) },
                                exitTransition = { fadeOut(animationSpec = tween(250)) }
                            ) {
                                LoginScreen(
                                    onLoginSuccess = { 
                                        navController.navigate(NavigationRoutes.VAULT_SELECTION) {
                                            popUpTo(NavigationRoutes.LOGIN) { inclusive = true }
                                        }
                                    }
                                )
                            }
                            
                            composable(
                                NavigationRoutes.VAULT_SELECTION,
                                enterTransition = { fadeIn(animationSpec = tween(250)) },
                                exitTransition = { fadeOut(animationSpec = tween(250)) }
                            ) {
                                VaultSelectionScreen(
                                    onNavigateToUnlock = { chatId -> 
                                        navController.navigate("${NavigationRoutes.VAULT_UNLOCK}/$chatId") 
                                    },
                                    onNavigateToCreate = { 
                                        navController.navigate(NavigationRoutes.VAULT_CREATE) 
                                    },
                                    onVaultCreated = { key -> 
                                        val encodedKey = URLEncoder.encode(key, "UTF-8")
                                        navController.navigate("${NavigationRoutes.VAULT_CREATE}/$encodedKey") 
                                    },
                                    onUnlockSuccess = {
                                        viewModel.setVaultUnlocked()
                                    },
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                            
                            composable(NavigationRoutes.VAULT_CREATE) {
                                VaultCreateScreen(
                                    generatedKeyBase64 = null,
                                    onVaultCreated = { key -> 
                                        val encodedKey = URLEncoder.encode(key, "UTF-8")
                                        navController.navigate("${NavigationRoutes.VAULT_CREATE}/$encodedKey") 
                                    },
                                    onContinue = {},
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                            
                            composable(
                                route = "${NavigationRoutes.VAULT_CREATE}/{key}",
                                arguments = listOf(navArgument("key") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val encodedKey = backStackEntry.arguments?.getString("key") ?: ""
                                val decodedKey = URLDecoder.decode(encodedKey, "UTF-8")
                                VaultCreateScreen(
                                    generatedKeyBase64 = decodedKey,
                                    onVaultCreated = {},
                                    onContinue = { 
                                        viewModel.setVaultUnlocked()
                                    },
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                            
                            composable(
                                route = "${NavigationRoutes.VAULT_UNLOCK}/{chatId}",
                                arguments = listOf(navArgument("chatId") { type = NavType.LongType })
                            ) { backStackEntry ->
                                val chatId = backStackEntry.arguments?.getLong("chatId") ?: 0L
                                VaultUnlockScreen(
                                    chatId = chatId,
                                    onUnlockSuccess = { 
                                        viewModel.setVaultUnlocked()
                                    },
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                            
                            composable(
                                NavigationRoutes.HOME,
                                enterTransition = { 
                                    if (initialState.destination.route == null) EnterTransition.None 
                                    else fadeIn(animationSpec = tween(250)) 
                                },
                                exitTransition = { fadeOut(animationSpec = tween(250)) },
                                popEnterTransition = { fadeIn(animationSpec = tween(250)) },
                                popExitTransition = { fadeOut(animationSpec = tween(250)) }
                            ) {
                                CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
                                    HomeScreen(
                                        onStorageClick = { navController.navigate(NavigationRoutes.folder(null)) },
                                        onSwitchVaultClick = { navController.navigate(NavigationRoutes.VAULT_SELECTION) },
                                        onCategoryClick = { categoryName -> 
                                            navController.navigate(NavigationRoutes.category(categoryName)) 
                                        },
                                        onRecentlyDeletedClick = { navController.navigate(NavigationRoutes.RECENTLY_DELETED) },
                                        onSearchClick = { navController.navigate(NavigationRoutes.SEARCH) },
                                        onNavigateToFolder = { folderId -> 
                                            navController.navigate(NavigationRoutes.folder(folderId)) 
                                        }
                                    )
                                }
                            }
                            
                            composable(
                                route = NavigationRoutes.FOLDER_ROUTE_PATTERN,
                                arguments = listOf(
                                    navArgument("id") {
                                        type = NavType.StringType
                                        nullable = true
                                        defaultValue = null
                                    }
                                ),
                                enterTransition = { 
                                    if (initialState.destination.route == NavigationRoutes.HOME) {
                                        fadeIn(tween(250, easing = LinearOutSlowInEasing))
                                    } else {
                                        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(200, easing = LinearOutSlowInEasing)) + fadeIn(tween(200)) 
                                    }
                                },
                                exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(200, easing = FastOutLinearInEasing)) + fadeOut(tween(200)) },
                                popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(200, easing = LinearOutSlowInEasing)) + fadeIn(tween(200)) },
                                popExitTransition = { 
                                    if (targetState.destination.route == NavigationRoutes.HOME) {
                                        fadeOut(tween(250, easing = FastOutLinearInEasing))
                                    } else {
                                        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(200, easing = FastOutLinearInEasing)) + fadeOut(tween(200)) 
                                    }
                                }
                            ) { backStackEntry ->
                                CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
                                    val folderId = backStackEntry.arguments?.getString("id")
                                    FolderScreen(
                                        initialFolderId = folderId,
                                        onNavigateBack = { navController.popBackStack() }
                                    )
                                }
                            }
                            
                            composable(
                                route = "${NavigationRoutes.CATEGORY_PREFIX}{categoryName}",
                                arguments = listOf(navArgument("categoryName") { type = NavType.StringType }),
                                enterTransition = { 
                                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300, easing = FastOutSlowInEasing))
                                },
                                exitTransition = { 
                                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300, easing = FastOutSlowInEasing)) 
                                },
                                popEnterTransition = { 
                                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300, easing = FastOutSlowInEasing)) 
                                },
                                popExitTransition = { 
                                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300, easing = FastOutSlowInEasing))
                                }
                            ) { backStackEntry ->
                                CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
                                    val catName = backStackEntry.arguments?.getString("categoryName") ?: ""
                                    CategoryScreen(
                                        categoryName = catName,
                                        onNavigateBack = { navController.popBackStack() }
                                    )
                                }
                            }
                            
                            composable(NavigationRoutes.RECENTLY_DELETED) {
                                RecentlyDeletedScreen(
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                            
                            composable(NavigationRoutes.SEARCH) {
                                SearchScreen(
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                                } // end NavHost
                                } // end AppScaffold
                            } // end CompositionLocalProvider
                            } // end SharedTransitionLayout
                        } // end key(startDestination)
                    } // end else
                } // end Surface
            } // end ZerogramTheme
        } // end setContent
    } // end onCreate
} // end MainActivity
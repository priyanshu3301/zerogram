package com.zerogram.core.ui.scaffold

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme

interface ScaffoldRenderer {
    @Composable
    fun TopBar() {}
    
    @Composable
    fun BottomBar() {}
}

val LocalScaffoldController = compositionLocalOf<ScaffoldController> { 
    error("No ScaffoldController provided") 
}

class ScaffoldController {
    val topBarContent = mutableStateOf<(@Composable () -> Unit)?>(null)
    val bottomBarContent = mutableStateOf<(@Composable () -> Unit)?>(null)

    fun setRenderer(renderer: ScaffoldRenderer?) {
        if (renderer == null) {
            topBarContent.value = null
            bottomBarContent.value = null
        } else {
            topBarContent.value = { renderer.TopBar() }
            bottomBarContent.value = { renderer.BottomBar() }
        }
    }
}

/**
 * The single top-level Scaffold for the entire app.
 */
@Composable
fun AppScaffold(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.background,
    content: @Composable (PaddingValues) -> Unit
) {
    val controller = remember { ScaffoldController() }
    
    CompositionLocalProvider(LocalScaffoldController provides controller) {
        Scaffold(
            modifier = modifier,
            containerColor = containerColor,
            topBar = { controller.topBarContent.value?.invoke() },
            bottomBar = { controller.bottomBarContent.value?.invoke() },
            content = content
        )
    }
}

/**
 * Hook for individual screens to define their top/bottom bars without nesting Scaffolds.
 */
@Composable
fun ScreenScaffoldConfig(renderer: ScaffoldRenderer) {
    val controller = LocalScaffoldController.current
    DisposableEffect(renderer) {
        controller.setRenderer(renderer)
        onDispose { 
            // In a navigation scenario, the new screen usually sets its renderer before the old one disposes,
            // or the exact timing might cause flickering if we immediately null it out.
            // For now, we clear it when disposed to avoid leaking old bars.
            controller.setRenderer(null) 
        }
    }
}

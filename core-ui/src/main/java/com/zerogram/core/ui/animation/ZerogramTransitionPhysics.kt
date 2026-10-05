package com.zerogram.core.ui.animation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically

/**
 * Standardized animation physics for the Zerogram app to ensure a consistent, 
 * iPhone-like premium feel across all UI transitions.
 */
object ZerogramTransitionPhysics {
    
    /**
     * An iOS-like smooth, non-bouncy spring.
     * Perfect for overlays, popups, bottom sheets, and general visibility transitions.
     */
    fun <T> iosSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    val bottomBarEnter: EnterTransition
        get() = slideInVertically(
            initialOffsetY = { fullHeight: Int -> fullHeight },
            animationSpec = iosSpring()
        ) + expandVertically(
            animationSpec = iosSpring()
        ) + fadeIn(
            animationSpec = iosSpring()
        )

    val bottomBarExit: ExitTransition
        get() = slideOutVertically(
            targetOffsetY = { fullHeight: Int -> fullHeight },
            animationSpec = iosSpring()
        ) + shrinkVertically(
            animationSpec = iosSpring()
        ) + fadeOut(
            animationSpec = iosSpring()
        )

    val topBarEnter: EnterTransition
        get() = slideInVertically(
            initialOffsetY = { fullHeight: Int -> -fullHeight },
            animationSpec = iosSpring()
        ) + fadeIn(
            animationSpec = iosSpring()
        )

    val topBarExit: ExitTransition
        get() = slideOutVertically(
            targetOffsetY = { fullHeight: Int -> -fullHeight },
            animationSpec = iosSpring()
        ) + fadeOut(
            animationSpec = iosSpring()
        )
}

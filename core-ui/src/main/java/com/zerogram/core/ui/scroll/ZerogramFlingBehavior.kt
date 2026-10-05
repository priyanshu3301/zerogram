package com.zerogram.core.ui.scroll

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs

/**
 * A premium fling behavior that:
 *  - Extends the natural glide by boosting the effective decay half-life (lower friction).
 *  - Uses Compose's own SplineBasedDecay to preserve the authentic organic curve shape.
 *  - Applies a velocity boost multiplier so fast flings travel further before settling.
 */
@Composable
fun rememberZerogramFlingBehavior(
    velocityMultiplier: Float = 1.25f, 
    friction: Float = 0.70f  // Very low friction = long gradual stop
): FlingBehavior {
    val density = LocalDensity.current
    val baseDecay: DecayAnimationSpec<Float> = remember(density, friction) {
        exponentialDecay(
            frictionMultiplier = friction,
            absVelocityThreshold = 0.1f
        )
    }
    return remember(velocityMultiplier, baseDecay) {
        ZerogramFlingBehavior(baseDecay, velocityMultiplier)
    }
}

private class ZerogramFlingBehavior(
    private val decaySpec: DecayAnimationSpec<Float>,
    private val velocityMultiplier: Float,
) : FlingBehavior {

    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        // Apply the velocity boost
        val boostedVelocity = initialVelocity * velocityMultiplier

        // Short-circuit trivially small flings
        if (abs(boostedVelocity) <= 1f) return initialVelocity

        // Estimate the target to see if the fling is worth performing
        val targetValue = decaySpec.calculateTargetValue(0f, boostedVelocity)
        if (abs(targetValue) < 1f) return initialVelocity

        var velocityLeft = boostedVelocity
        var lastValue = 0f

        AnimationState(
            initialValue = 0f,
            initialVelocity = boostedVelocity,
        ).animateDecay(decaySpec) {
            val delta = (value - lastValue)
            val consumed = scrollBy(delta)
            lastValue = value

            // Stop the animation if scroll was blocked (hit boundary)
            if (abs(consumed) < abs(delta) * 0.5f) {
                cancelAnimation()
            }
            velocityLeft = velocity
        }
        // Return unconsumed velocity so overscroll effect can consume it
        return velocityLeft
    }
}

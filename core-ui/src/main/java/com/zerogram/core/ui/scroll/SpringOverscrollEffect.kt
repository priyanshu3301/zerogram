package com.zerogram.core.ui.scroll

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * A spring-physics overscroll effect that:
 *  - Absorbs edge-collision velocity with a damped spring (no abrupt halt).
 *  - Renders a subtle vertical translation of list content (rubber-band feel).
 *  - Snaps back to zero when the finger lifts, using a low-stiffness spring.
 */
@OptIn(ExperimentalFoundationApi::class)
class SpringOverscrollEffect(
    private val scope: CoroutineScope,
    private val maxOverscrollPx: Float = 120f
) : OverscrollEffect {

    private val translationAnim = Animatable(0f)

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset
    ): Offset {
        val consumed = performScroll(delta)
        val overscrollDelta = delta - consumed

        if (abs(overscrollDelta.y) > 0.5f) {
            val resistance = 1f - abs(translationAnim.value) / maxOverscrollPx
            val target = (translationAnim.value + overscrollDelta.y * resistance.coerceIn(0f, 1f))
                .coerceIn(-maxOverscrollPx, maxOverscrollPx)

            scope.launch {
                translationAnim.snapTo(target)
            }
        }

        return consumed
    }

    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity
    ) {
        val remainingVelocity = performFling(velocity)

        // If significant velocity remains after fling, absorb it via spring
        if (abs(remainingVelocity.y) > 100f) {
            val absorptionTarget = (remainingVelocity.y * 0.04f)
                .coerceIn(-maxOverscrollPx, maxOverscrollPx)
            translationAnim.animateTo(
                targetValue = absorptionTarget,
                animationSpec = spring(
                    dampingRatio = 0.6f,
                    stiffness = Spring.StiffnessLow
                )
            )
        }

        // Spring back to rest
        translationAnim.animateTo(
            targetValue = 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }

    override val isInProgress: Boolean
        get() = translationAnim.value != 0f

    override val effectModifier: Modifier = Modifier.drawWithContent {
        drawWithSpringTranslation(translationAnim.value)
    }

    private fun ContentDrawScope.drawWithSpringTranslation(translationY: Float) {
        // Translate the entire canvas by the spring offset, then draw content
        // This avoids invalidating child composables — pure draw-time transform
        withTransform({
            translate(top = translationY)
        }) {
            this@drawWithSpringTranslation.drawContent()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberSpringOverscrollEffect(): OverscrollEffect {
    val scope = rememberCoroutineScope()
    return remember(scope) { SpringOverscrollEffect(scope) }
}

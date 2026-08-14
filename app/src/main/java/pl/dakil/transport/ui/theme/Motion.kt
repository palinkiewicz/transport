package pl.dakil.transport.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme

/**
 * The app's [MotionScheme.expressive] motion with its *default spatial* spring replaced by a tween,
 * for subtrees where an overshoot reads as a glitch rather than as character.
 *
 * The map's bottom sheet is the case this exists for. It drives its drag settle, its open animation
 * and the fling that carries over from the timetable off that one spec, and a spring cannot be made
 * to stop dead: critical damping only removes the overshoot a *displacement* causes, while a fling
 * hands the animation an initial velocity that carries it past the anchor whatever the damping is —
 * and past the open anchor is where M3 stretches the sheet's own surface ([verticalScaleUp], in
 * `BottomSheetScaffold`) to cover the gap that opens beneath it. A duration-based spec ignores that
 * velocity, so the sheet cannot pass its anchor at any fling speed.
 *
 * The duration and easing are M3's own `BottomSheetAnimationSpec`, which is what the sheet animated
 * on before the motion scheme existed. Everything else in the scheme is left alone — the fast and
 * slow spatial specs, and all three effects specs — so this is a sheet's settle, not "the standard
 * motion scheme on that screen".
 *
 * Provide it for a subtree with `MaterialExpressiveTheme(motionScheme = SettledMotionScheme)`, and
 * keep this single instance: the motion scheme is a *static* CompositionLocal, so a fresh one per
 * recomposition would invalidate everything under it. It has to be provided around the whole sheet
 * container, since that is where the sheet reads the scheme — which is wider than the sheet itself,
 * so the map's sheet hands the theme's own scheme back to its content.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val SettledMotionScheme: MotionScheme = SettledSpatialMotionScheme(MotionScheme.expressive())

/** M3's own pre-motion-scheme bottom sheet animation. */
private val SheetSettleSpec = tween<Any>(durationMillis = 300, easing = FastOutSlowInEasing)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private class SettledSpatialMotionScheme(private val base: MotionScheme) : MotionScheme {
    @Suppress("UNCHECKED_CAST")
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> =
        SheetSettleSpec as FiniteAnimationSpec<T>

    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = base.fastSpatialSpec()

    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = base.slowSpatialSpec()

    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = base.defaultEffectsSpec()

    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = base.fastEffectsSpec()

    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = base.slowEffectsSpec()
}

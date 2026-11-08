package com.livewall.app.ui.theme

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/**
 * M3 Expressive motion physics. Springs replace easing+duration: no fixed duration, they
 * respond to input, velocity, interruptions and retargeting.
 *
 * - **Spatial springs** drive movement (position, size, corner radius / shape morphing) and may
 *   overshoot.
 * - **Effects springs** drive color and opacity and never overshoot (damping 1.0).
 */
object ExpressiveSprings {
    /** Fast spatial — small, snappy feedback (press, tap). */
    val fastSpatial: SpringSpec<Float> = spring(dampingRatio = 0.6f, stiffness = 800f)

    /** Default spatial — most standard transitions; subtle bounce. */
    val defaultSpatial: SpringSpec<Float> = spring(dampingRatio = 0.8f, stiffness = 380f)

    /** Slow spatial — full-screen animations. */
    val slowSpatial: SpringSpec<Float> = spring(dampingRatio = 0.8f, stiffness = 200f)

    /** Fast effects — quick color/opacity fades, no overshoot. */
    val fastEffects: SpringSpec<Float> = spring(dampingRatio = 1.0f, stiffness = 3800f)

    /** Default effects — standard color/opacity, no overshoot. */
    val defaultEffects: SpringSpec<Float> = spring(dampingRatio = 1.0f, stiffness = 1600f)

    /** Slow effects — full-screen content refresh, no overshoot. */
    val slowEffects: SpringSpec<Float> = spring(dampingRatio = 1.0f, stiffness = 800f)
}

/** Generic expressive springs for any animatable type (Color, Dp, …). */
inline fun <reified T> springFastSpatial(): SpringSpec<T> =
    spring(dampingRatio = 0.6f, stiffness = 800f)

inline fun <reified T> springDefaultSpatial(): SpringSpec<T> =
    spring(dampingRatio = 0.8f, stiffness = 380f)

inline fun <reified T> springSlowSpatial(): SpringSpec<T> =
    spring(dampingRatio = 0.8f, stiffness = 200f)

inline fun <reified T> springFastEffects(): SpringSpec<T> =
    spring(dampingRatio = 1.0f, stiffness = 3800f)

inline fun <reified T> springDefaultEffects(): SpringSpec<T> =
    spring(dampingRatio = 1.0f, stiffness = 1600f)

inline fun <reified T> springSlowEffects(): SpringSpec<T> =
    spring(dampingRatio = 1.0f, stiffness = 800f)

object AppMotion {
    val fastSpatial: SpringSpec<Float> get() = ExpressiveSprings.fastSpatial
    val defaultSpatial: SpringSpec<Float> get() = ExpressiveSprings.defaultSpatial
    val slowSpatial: SpringSpec<Float> get() = ExpressiveSprings.slowSpatial
    val fastEffects: SpringSpec<Float> get() = ExpressiveSprings.fastEffects
    val defaultEffects: SpringSpec<Float> get() = ExpressiveSprings.defaultEffects
    val slowEffects: SpringSpec<Float> get() = ExpressiveSprings.slowEffects
}

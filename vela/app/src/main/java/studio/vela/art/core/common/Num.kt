package studio.vela.art.core.common

import kotlin.math.abs

/** Linear interpolation between [a] and [b] by [t] (t is not clamped). */
fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/** Interpolation clamped into the segment. */
fun lerpClamped(a: Float, b: Float, t: Float): Float = lerp(a, b, t.coerceIn(0f, 1f))

fun clamp01(v: Float): Float = if (v < 0f) 0f else if (v > 1f) 1f else v

fun clamp(v: Float, min: Float, max: Float): Float = if (v < min) min else if (v > max) max else v

/** Maps [value] from one range to another, clamped to the output range. */
fun mapRange(value: Float, inMin: Float, inMax: Float, outMin: Float, outMax: Float): Float {
    if (abs(inMax - inMin) < 1e-6f) return outMin
    val t = clamp01((value - inMin) / (inMax - inMin))
    return outMin + (outMax - outMin) * t
}

fun smoothStep(edge0: Float, edge1: Float, x: Float): Float {
    if (abs(edge1 - edge0) < 1e-6f) return if (x < edge0) 0f else 1f
    val t = clamp01((x - edge0) / (edge1 - edge0))
    return t * t * (3f - 2f * t)
}

/** Rounds to a stable bucket, used to reuse cached Paints/Shaders cheaply. */
fun Float.bucket(step: Float): Float = if (step <= 0f) this else (this / step).toInt() * step

fun distance(ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = bx - ax
    val dy = by - ay
    return kotlin.math.sqrt(dx * dx + dy * dy)
}

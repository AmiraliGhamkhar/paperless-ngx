package studio.vela.art.drawing.brush

import studio.vela.art.core.common.clamp01
import studio.vela.art.core.common.distance
import studio.vela.art.core.common.lerp
import studio.vela.art.domain.model.BrushPreset
import studio.vela.art.domain.model.Dab
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Turns a stream of pointer samples into a stream of dabs.
 *
 * Responsibilities (all pure maths, no Android types, fully unit-tested):
 *  - input stabilisation (smoothed cursor),
 *  - constant-distance dab spacing with carried-over remainder,
 *  - pressure / tilt / velocity response,
 *  - taper-in, scatter, size and angle jitter, colour dynamics.
 */
class DabGenerator(
    preset: BrushPreset,
    private val random: Random = Random(preset.id.hashCode().toLong()),
) {
    var preset: BrushPreset = preset.sanitized()
        private set

    // Raw + stabilised cursor positions.
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var smoothedX = 0f
    private var smoothedY = 0f
    private var prevSegmentX = 0f
    private var prevSegmentY = 0f

    private var lastPressure = 0f
    private var lastTilt = 0f
    private var lastTimeMs = 0L
    private var speedPxPerMs = 0f

    /** Distance already accumulated toward the next dab. */
    private var spacingResidual = 0f

    /** Total travelled distance since the stroke started (drives taper). */
    private var travelled = 0f

    private var started = false
    private var dabCounter = 0

    fun updatePreset(newPreset: BrushPreset) {
        preset = newPreset.sanitized()
    }

    /** Starts a stroke and returns its first dab (or an empty list for zero-size brushes). */
    fun begin(x: Float, y: Float, pressure: Float, tilt: Float, timeMs: Long): List<Dab> {
        started = true
        lastRawX = x
        lastRawY = y
        smoothedX = x
        smoothedY = y
        prevSegmentX = x
        prevSegmentY = y
        lastPressure = pressure
        lastTilt = tilt
        lastTimeMs = timeMs
        speedPxPerMs = 0f
        spacingResidual = 0f
        travelled = 0f
        dabCounter = 0
        val dab = buildDab(x, y, pressure, 0f) ?: return emptyList()
        return listOf(dab)
    }

    /**
     * Feeds one pointer sample and returns every dab that should be stamped to keep the
     * stroke continuous up to that sample.
     */
    fun add(x: Float, y: Float, pressure: Float, tilt: Float, timeMs: Long): List<Dab> {
        if (!started) return begin(x, y, pressure, tilt, timeMs)

        // 1. stabilisation: exponential smoothing of the raw cursor.
        val stabilizer = preset.stabilizer.coerceIn(0f, 0.95f)
        val follow = 1f - stabilizer
        smoothedX += (x - smoothedX) * follow
        smoothedY += (y - smoothedY) * follow
        lastRawX = x
        lastRawY = y

        // 2. velocity estimate (document px per ms).
        val dt = max(1L, timeMs - lastTimeMs)
        val rawDistance = distance(prevSegmentX, prevSegmentY, smoothedX, smoothedY)
        val instantSpeed = rawDistance / dt
        speedPxPerMs = speedPxPerMs * 0.7f + instantSpeed * 0.3f
        lastTimeMs = timeMs
        lastPressure = pressure
        lastTilt = tilt

        if (rawDistance < 1e-4f) return emptyList()

        // 3. walk the segment placing dabs at constant spacing.
        val nominalSize = dabSize(pressure, tilt, speedPxPerMs)
        val step = max(0.6f, preset.spacing * nominalSize)
        val dabs = ArrayList<Dab>(4)
        var distanceToCover = spacingResidual + rawDistance
        val segStartX = prevSegmentX
        val segStartY = prevSegmentY
        while (distanceToCover >= step) {
            distanceToCover -= step
            val consumedFromSegment = rawDistance - distanceToCover
            val t = clamp01(consumedFromSegment / rawDistance)
            val dabX = lerp(segStartX, smoothedX, t)
            val dabY = lerp(segStartY, smoothedY, t)
            travelled += step
            val dab = buildDab(dabX, dabY, pressure, travelled)
            if (dab != null) dabs.add(dab)
        }
        spacingResidual = distanceToCover
        prevSegmentX = smoothedX
        prevSegmentY = smoothedY
        return dabs
    }

    /** Finishes the stroke. Dabs are already stamped, so nothing is emitted. */
    fun end(): List<Dab> {
        started = false
        return emptyList()
    }

    /** Effective dab diameter after pressure, tilt, velocity and jitter. */
    fun dabSize(pressure: Float, tilt: Float, speedPxPerMs: Float): Float {
        val pressureFactor = 0.08f + 0.92f * preset.pressureSize.apply(pressure)
        val tiltNorm = clamp01(tilt / (Math.PI.toFloat() / 2f))
        val tiltFactor = 1f + preset.tiltSize * tiltNorm
        val speedNorm = clamp01(speedPxPerMs / FAST_SPEED_PX_PER_MS)
        val velocityFactor = 1f - preset.velocitySize * speedNorm
        return (preset.size * pressureFactor * tiltFactor * velocityFactor).coerceAtLeast(0.5f)
    }

    private fun buildDab(x: Float, y: Float, pressure: Float, distanceTravelled: Float): Dab? {
        var size = dabSize(pressure, lastTilt, speedPxPerMs)
        if (preset.taperStart > 0f) {
            val taperDistance = max(1f, preset.taperStart * preset.size * 8f)
            val t = clamp01(distanceTravelled / taperDistance)
            val taperFactor = 0.12f + 0.88f * t
            size *= taperFactor
        }
        if (preset.sizeJitter > 0f) {
            size *= 1f - preset.sizeJitter * random.nextFloat()
        }
        if (size < 0.5f) return null

        var dabX = x
        var dabY = y
        if (preset.scatter > 0f) {
            val radius = preset.scatter * size * 0.5f
            // Uniform point in a disc.
            val r = radius * kotlin.math.sqrt(random.nextFloat())
            val theta = random.nextFloat() * (Math.PI.toFloat() * 2f)
            dabX += r * cos(theta)
            dabY += r * sin(theta)
        }

        val speedNorm = clamp01(speedPxPerMs / FAST_SPEED_PX_PER_MS)
        val flow = preset.flow * preset.pressureFlow.apply(pressure) * (1f - preset.velocityFlow * speedNorm)
        val alpha = (flow * preset.opacity * preset.pressureOpacity.apply(pressure)).coerceIn(0f, 1f)
        if (alpha <= 0.002f) return null

        val angle = preset.angleDeg +
            (if (preset.angleJitterDeg > 0f) (random.nextFloat() * 2f - 1f) * preset.angleJitterDeg else 0f)

        val hueShift = if (preset.hueJitter > 0f) (random.nextFloat() * 2f - 1f) * preset.hueJitter * 60f else 0f
        val satShift = if (preset.saturationJitter > 0f) (random.nextFloat() * 2f - 1f) * preset.saturationJitter else 0f
        val valShift = if (preset.valueJitter > 0f) (random.nextFloat() * 2f - 1f) * preset.valueJitter else 0f

        return Dab(
            x = dabX,
            y = dabY,
            size = size,
            angleDeg = angle,
            alpha = alpha,
            roundness = preset.roundness,
            hardness = preset.hardness,
            hueShiftDeg = hueShift,
            saturationShift = satShift,
            valueShift = valShift,
            seed = dabCounter++ * 0x9E3779B1.toInt() xor preset.id.hashCode(),
        )
    }

    /** Raw (unsmoothed) cursor, exposed for debugging/telemetry overlays. */
    fun rawPosition(): FloatArray = floatArrayOf(lastRawX, lastRawY)

    companion object {
        /** Speed (doc px/ms) treated as "fast" for velocity response. */
        const val FAST_SPEED_PX_PER_MS = 2.5f

        /** Smallest useful spacing so a stroke cannot degenerate into no dabs. */
        val MIN_STEP: Float = 0.6f

        fun stepFor(preset: BrushPreset, size: Float): Float = min(4f * size, max(MIN_STEP, preset.spacing * size))
    }
}

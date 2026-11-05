package studio.vela.art.domain.model

import kotlinx.serialization.Serializable
import kotlin.math.pow

@Serializable
enum class BrushCategory(val label: String) {
    SKETCHING("Sketching"),
    GRAPHITE("Graphite"),
    TECHNICAL_PEN("Technical pens"),
    INKING("Inking"),
    MARKER("Markers"),
    CALLIGRAPHY("Calligraphy"),
    AIRBRUSH("Airbrush"),
    CHARCOAL("Charcoal"),
    PASTEL("Pastel"),
    WATERCOLOR("Watercolor"),
    OIL("Oil paint"),
    ACRYLIC("Acrylic"),
    GOUACHE("Gouache"),
    TEXTURE("Texture"),
    SPRAY("Spray"),
    STAMP("Stamps"),
    PARTICLE("Particles"),
    ERASER("Erasers"),
    SMUDGE("Blenders"),
}

/**
 * How a dab is rasterised. Each engine is implemented by `DabRenderer`, so adding a new
 * engine never requires touching the stroke pipeline.
 */
@Serializable
enum class BrushEngineKind {
    /** Soft/hard round dab with hardness-driven gradient. */
    ROUND,

    /** Round dab masked by a procedural grain texture. */
    TEXTURED,

    /** Cluster of small dots inside the dab radius. */
    SPRAY,

    /** Flat, rotatable nib (calligraphy, markers, palette knife). */
    CHISEL,

    /** Rotatable procedural stamp shape (leaves, chalk chunks, grunge). */
    STAMP,

    /** Pulls colour from the layer below the pointer. */
    SMUDGE,

    /** Removes alpha instead of adding colour. */
    ERASER,
}

/** Procedural grain textures generated at runtime (no third-party image assets). */
@Serializable
enum class GrainKind(val label: String) {
    NONE("None"),
    PAPER_FINE("Paper fine"),
    PAPER_ROUGH("Paper rough"),
    CANVAS("Canvas weave"),
    CHALK("Chalk"),
    NOISE("Noise"),
    SPECKLE("Speckle"),
}

/**
 * Pressure response curve. A gamma curve plus output window is enough for natural
 * pressure mapping and keeps the model serialisable and testable.
 */
@Serializable
data class PressureResponse(
    val gamma: Float = 1f,
    val min: Float = 0f,
    val max: Float = 1f,
) {
    fun apply(pressure: Float): Float {
        val p = if (pressure < 0f) 0f else if (pressure > 1f) 1f else pressure
        val shaped = if (gamma == 1f) p else p.pow(gamma)
        return min + (max - min) * shaped
    }

    companion object {
        val IDENTITY = PressureResponse()
    }
}

/**
 * A brush preset. Every field below changes what the engine actually renders; presets that
 * would render identically are not shipped (see BrushLibrary).
 */
@Serializable
data class BrushPreset(
    val id: String,
    val name: String,
    val category: BrushCategory,
    val engine: BrushEngineKind,
    /** Diameter in document pixels at pressure 1.0. */
    val size: Float = 24f,
    /** Maximum stroke alpha; multiplies accumulated dab coverage. */
    val opacity: Float = 1f,
    /** Per-dab alpha before pressure response. */
    val flow: Float = 1f,
    /** Distance between dabs as a fraction of the dab diameter. */
    val spacing: Float = 0.08f,
    /** 0 = fully soft gradient, 1 = hard edge. */
    val hardness: Float = 0.75f,
    /** Nib aspect: 1 = round, 0.1 = very flat (chisel engines). */
    val roundness: Float = 1f,
    val angleDeg: Float = 0f,
    /** Random positional offset as a fraction of the dab radius. */
    val scatter: Float = 0f,
    /** Random size variation, fraction of the computed size. */
    val sizeJitter: Float = 0f,
    val angleJitterDeg: Float = 0f,
    val pressureSize: PressureResponse = PressureResponse(gamma = 1.2f),
    val pressureFlow: PressureResponse = PressureResponse(min = 0.25f),
    /** Extra stroke-level opacity response (applied on top of [pressureFlow]). */
    val pressureOpacity: PressureResponse = PressureResponse.IDENTITY,
    /** 0..1: how much stylus tilt widens the dab. */
    val tiltSize: Float = 0f,
    /** 0..1: how much fast strokes thin the line. */
    val velocitySize: Float = 0f,
    /** 0..1: how much fast strokes fade. */
    val velocityFlow: Float = 0f,
    /** 0..0.95 input smoothing. */
    val stabilizer: Float = 0.12f,
    /** Taper-in length as a multiple of the dab diameter (0 disables). */
    val taperStart: Float = 0f,
    val grain: GrainKind = GrainKind.NONE,
    val grainScale: Float = 1f,
    val grainStrength: Float = 0.55f,
    val hueJitter: Float = 0f,
    val saturationJitter: Float = 0f,
    val valueJitter: Float = 0f,
    val builtIn: Boolean = true,
) {
    /** Sanitises user edits so the engine never sees NaN/negative geometry. */
    fun sanitized(): BrushPreset = copy(
        size = size.coerceIn(1f, 2000f),
        opacity = opacity.coerceIn(0.01f, 1f),
        flow = flow.coerceIn(0.01f, 1f),
        spacing = spacing.coerceIn(0.01f, 4f),
        hardness = hardness.coerceIn(0f, 1f),
        roundness = roundness.coerceIn(0.05f, 1f),
        scatter = scatter.coerceIn(0f, 3f),
        sizeJitter = sizeJitter.coerceIn(0f, 0.95f),
        angleJitterDeg = angleJitterDeg.coerceIn(0f, 180f),
        tiltSize = tiltSize.coerceIn(0f, 1f),
        velocitySize = velocitySize.coerceIn(0f, 1f),
        velocityFlow = velocityFlow.coerceIn(0f, 1f),
        stabilizer = stabilizer.coerceIn(0f, 0.95f),
        taperStart = taperStart.coerceIn(0f, 4f),
        grainScale = grainScale.coerceIn(0.25f, 4f),
        grainStrength = grainStrength.coerceIn(0f, 1f),
        hueJitter = hueJitter.coerceIn(0f, 1f),
        saturationJitter = saturationJitter.coerceIn(0f, 1f),
        valueJitter = valueJitter.coerceIn(0f, 1f),
    )

    val isEraser: Boolean get() = engine == BrushEngineKind.ERASER
}

/** A single rasterised stamp generated by [studio.vela.art.drawing.brush.DabGenerator]. */
data class Dab(
    val x: Float,
    val y: Float,
    /** Diameter in document pixels. */
    val size: Float,
    /** Nib angle in degrees. */
    val angleDeg: Float,
    /** Per-dab alpha (flow × opacity × pressure response). */
    val alpha: Float,
    /** Horizontal scale factor for the nib (roundness). */
    val roundness: Float,
    /** Hardness snapshot (presets can vary it per dab in future versions). */
    val hardness: Float,
    val hueShiftDeg: Float = 0f,
    val saturationShift: Float = 0f,
    val valueShift: Float = 0f,
    /** Stable seed for procedural engines so re-flushes render identically. */
    val seed: Int = 0,
)

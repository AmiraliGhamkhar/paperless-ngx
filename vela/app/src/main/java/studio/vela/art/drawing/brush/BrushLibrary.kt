package studio.vela.art.drawing.brush

import studio.vela.art.domain.model.BrushCategory
import studio.vela.art.domain.model.BrushEngineKind
import studio.vela.art.domain.model.BrushPreset
import studio.vela.art.domain.model.GrainKind
import studio.vela.art.domain.model.PressureResponse

/**
 * Built-in brush library.
 *
 * Each preset is tuned to render differently: engine, spacing, hardness, grain, pressure
 * curves, taper and dynamics all differ meaningfully. Presets that would be visually
 * identical are not shipped.
 */
object BrushLibrary {

    private val pencil = BrushPreset(
        id = "graphite-pencil",
        name = "Graphite pencil",
        category = BrushCategory.GRAPHITE,
        engine = BrushEngineKind.TEXTURED,
        size = 7f,
        opacity = 0.95f,
        flow = 0.6f,
        spacing = 0.05f,
        hardness = 0.55f,
        grain = GrainKind.PAPER_FINE,
        grainScale = 0.7f,
        grainStrength = 0.7f,
        pressureSize = PressureResponse(gamma = 1.35f),
        pressureFlow = PressureResponse(gamma = 0.9f, min = 0.18f),
        tiltSize = 0.35f,
        velocitySize = 0.2f,
        stabilizer = 0.18f,
    )

    private val softGraphite = pencil.copy(
        id = "graphite-soft",
        name = "Soft graphite",
        size = 34f,
        flow = 0.2f,
        hardness = 0.15f,
        spacing = 0.04f,
        grain = GrainKind.PAPER_ROUGH,
        grainScale = 1.4f,
        grainStrength = 0.45f,
        tiltSize = 0.55f,
        opacity = 0.8f,
    )

    private val mechanicalPencil = BrushPreset(
        id = "mechanical-pencil",
        name = "Mechanical 0.5",
        category = BrushCategory.TECHNICAL_PEN,
        engine = BrushEngineKind.ROUND,
        size = 3.2f,
        flow = 0.85f,
        spacing = 0.03f,
        hardness = 0.9f,
        pressureSize = PressureResponse(gamma = 1.1f, min = 0.55f),
        pressureFlow = PressureResponse(min = 0.85f),
        stabilizer = 0.25f,
    )

    private val technicalPen = BrushPreset(
        id = "technical-pen",
        name = "Technical pen",
        category = BrushCategory.TECHNICAL_PEN,
        engine = BrushEngineKind.ROUND,
        size = 4.5f,
        flow = 1f,
        spacing = 0.03f,
        hardness = 0.97f,
        pressureSize = PressureResponse(gamma = 0.6f, min = 0.9f),
        pressureFlow = PressureResponse(min = 1f),
        stabilizer = 0.35f,
    )

    private val liner = BrushPreset(
        id = "ink-liner",
        name = "Ink liner",
        category = BrushCategory.INKING,
        engine = BrushEngineKind.ROUND,
        size = 6f,
        flow = 1f,
        spacing = 0.025f,
        hardness = 0.95f,
        pressureSize = PressureResponse(gamma = 1.6f, min = 0.15f),
        pressureFlow = PressureResponse(min = 0.9f),
        stabilizer = 0.3f,
        taperStart = 0.35f,
    )

    private val taperedInk = liner.copy(
        id = "ink-tapered",
        name = "Tapered inker",
        size = 12f,
        taperStart = 1.1f,
        pressureSize = PressureResponse(gamma = 2.2f, min = 0.05f),
        stabilizer = 0.45f,
    )

    private val dryInk = BrushPreset(
        id = "ink-dry",
        name = "Dry ink",
        category = BrushCategory.INKING,
        engine = BrushEngineKind.TEXTURED,
        size = 9f,
        flow = 0.85f,
        spacing = 0.045f,
        hardness = 0.7f,
        grain = GrainKind.CHALK,
        grainScale = 1.8f,
        grainStrength = 0.85f,
        pressureFlow = PressureResponse(min = 0.3f),
    )

    private val markerBroad = BrushPreset(
        id = "marker-broad",
        name = "Broad marker",
        category = BrushCategory.MARKER,
        engine = BrushEngineKind.CHISEL,
        size = 48f,
        opacity = 0.75f,
        flow = 0.35f,
        spacing = 0.05f,
        hardness = 0.85f,
        roundness = 0.22f,
        angleDeg = 42f,
        pressureSize = PressureResponse(gamma = 0.7f, min = 0.8f),
        stabilizer = 0.2f,
    )

    private val markerBrushTip = markerBroad.copy(
        id = "marker-brush",
        name = "Brush marker",
        size = 26f,
        roundness = 0.55f,
        angleDeg = 30f,
        opacity = 0.85f,
        flow = 0.5f,
        hardness = 0.65f,
    )

    private val calligraphyChisel = BrushPreset(
        id = "calligraphy-chisel",
        name = "Calligraphy chisel",
        category = BrushCategory.CALLIGRAPHY,
        engine = BrushEngineKind.CHISEL,
        size = 36f,
        flow = 1f,
        spacing = 0.02f,
        hardness = 0.92f,
        roundness = 0.12f,
        angleDeg = 40f,
        pressureSize = PressureResponse(gamma = 1.2f, min = 0.35f),
        stabilizer = 0.5f,
        taperStart = 0.25f,
    )

    private val airbrushSoft = BrushPreset(
        id = "airbrush-soft",
        name = "Airbrush soft",
        category = BrushCategory.AIRBRUSH,
        engine = BrushEngineKind.ROUND,
        size = 130f,
        opacity = 0.9f,
        flow = 0.06f,
        spacing = 0.035f,
        hardness = 0f,
        pressureSize = PressureResponse(gamma = 0.8f, min = 0.4f),
        pressureFlow = PressureResponse(gamma = 0.7f, min = 0.1f),
        velocityFlow = 0.35f,
        stabilizer = 0.3f,
    )

    private val airbrushFine = airbrushSoft.copy(
        id = "airbrush-fine",
        name = "Airbrush fine",
        size = 38f,
        flow = 0.12f,
        hardness = 0.05f,
        spacing = 0.05f,
        velocityFlow = 0.1f,
    )

    private val spray = BrushPreset(
        id = "spray-splatter",
        name = "Spray splatter",
        category = BrushCategory.SPRAY,
        engine = BrushEngineKind.SPRAY,
        size = 90f,
        opacity = 0.9f,
        flow = 0.55f,
        spacing = 0.12f,
        hardness = 0.6f,
        scatter = 0.9f,
        sizeJitter = 0.6f,
        grain = GrainKind.SPECKLE,
        pressureFlow = PressureResponse(min = 0.15f),
    )

    private val charcoalStick = BrushPreset(
        id = "charcoal-stick",
        name = "Charcoal stick",
        category = BrushCategory.CHARCOAL,
        engine = BrushEngineKind.TEXTURED,
        size = 46f,
        opacity = 0.95f,
        flow = 0.4f,
        spacing = 0.035f,
        hardness = 0.4f,
        grain = GrainKind.CHALK,
        grainScale = 2.2f,
        grainStrength = 0.9f,
        tiltSize = 0.6f,
        angleJitterDeg = 12f,
        pressureSize = PressureResponse(gamma = 1.3f),
        pressureFlow = PressureResponse(min = 0.2f),
        stabilizer = 0.1f,
    )

    private val vineCharcoal = charcoalStick.copy(
        id = "charcoal-vine",
        name = "Vine charcoal",
        size = 62f,
        flow = 0.25f,
        hardness = 0.25f,
        grain = GrainKind.PAPER_ROUGH,
        grainScale = 1.8f,
        grainStrength = 0.75f,
    )

    private val softPastel = BrushPreset(
        id = "pastel-soft",
        name = "Soft pastel",
        category = BrushCategory.PASTEL,
        engine = BrushEngineKind.TEXTURED,
        size = 38f,
        opacity = 1f,
        flow = 0.5f,
        spacing = 0.03f,
        hardness = 0.5f,
        grain = GrainKind.PAPER_FINE,
        grainScale = 1.2f,
        grainStrength = 0.65f,
        saturationJitter = 0.15f,
        valueJitter = 0.2f,
        pressureFlow = PressureResponse(min = 0.3f),
    )

    private val chalk = softPastel.copy(
        id = "chalk-texture",
        name = "Chalk",
        size = 26f,
        flow = 0.6f,
        hardness = 0.35f,
        grain = GrainKind.CHALK,
        grainScale = 1.6f,
        grainStrength = 0.95f,
        valueJitter = 0.3f,
    )

    private val watercolorWash = BrushPreset(
        id = "watercolor-wash",
        name = "Watercolor wash",
        category = BrushCategory.WATERCOLOR,
        engine = BrushEngineKind.TEXTURED,
        size = 110f,
        opacity = 0.55f,
        flow = 0.1f,
        spacing = 0.03f,
        hardness = 0.05f,
        grain = GrainKind.PAPER_ROUGH,
        grainScale = 2.6f,
        grainStrength = 0.35f,
        pressureSize = PressureResponse(gamma = 0.9f, min = 0.35f),
        velocityFlow = 0.4f,
        stabilizer = 0.35f,
    )

    private val wetWatercolor = watercolorWash.copy(
        id = "watercolor-wet",
        name = "Wet watercolor",
        size = 70f,
        opacity = 0.8f,
        flow = 0.22f,
        hardness = 0.12f,
        grain = GrainKind.NONE,
        hueJitter = 0.06f,
    )

    private val oilBristle = BrushPreset(
        id = "oil-bristle",
        name = "Oil bristle",
        category = BrushCategory.OIL,
        engine = BrushEngineKind.TEXTURED,
        size = 52f,
        opacity = 1f,
        flow = 0.75f,
        spacing = 0.06f,
        hardness = 0.55f,
        roundness = 0.8f,
        grain = GrainKind.CANVAS,
        grainScale = 1.6f,
        grainStrength = 0.7f,
        angleJitterDeg = 8f,
        sizeJitter = 0.12f,
        valueJitter = 0.12f,
        pressureFlow = PressureResponse(min = 0.4f),
    )

    private val acrylicFlat = BrushPreset(
        id = "acrylic-flat",
        name = "Acrylic flat",
        category = BrushCategory.ACRYLIC,
        engine = BrushEngineKind.CHISEL,
        size = 58f,
        opacity = 1f,
        flow = 0.9f,
        spacing = 0.045f,
        hardness = 0.75f,
        roundness = 0.3f,
        angleDeg = 90f,
        grain = GrainKind.CANVAS,
        grainScale = 1.2f,
        grainStrength = 0.3f,
        pressureFlow = PressureResponse(min = 0.6f),
    )

    private val gouache = BrushPreset(
        id = "gouache-opaque",
        name = "Gouache opaque",
        category = BrushCategory.GOUACHE,
        engine = BrushEngineKind.ROUND,
        size = 34f,
        opacity = 1f,
        flow = 0.85f,
        spacing = 0.04f,
        hardness = 0.6f,
        pressureFlow = PressureResponse(min = 0.5f),
        stabilizer = 0.2f,
    )

    private val paletteKnife = BrushPreset(
        id = "palette-knife",
        name = "Palette knife",
        category = BrushCategory.ACRYLIC,
        engine = BrushEngineKind.CHISEL,
        size = 44f,
        opacity = 1f,
        flow = 1f,
        spacing = 0.02f,
        hardness = 0.98f,
        roundness = 0.15f,
        angleDeg = 15f,
        valueJitter = 0.18f,
        pressureFlow = PressureResponse(min = 0.75f),
        stabilizer = 0.55f,
    )

    private val textureGrunge = BrushPreset(
        id = "texture-grunge",
        name = "Grunge texture",
        category = BrushCategory.TEXTURE,
        engine = BrushEngineKind.STAMP,
        size = 80f,
        opacity = 0.7f,
        flow = 0.7f,
        spacing = 0.35f,
        hardness = 0.8f,
        grain = GrainKind.SPECKLE,
        grainScale = 1f,
        grainStrength = 1f,
        angleJitterDeg = 180f,
        sizeJitter = 0.25f,
    )

    private val stampLeaf = BrushPreset(
        id = "stamp-leaf",
        name = "Leaf stamp",
        category = BrushCategory.STAMP,
        engine = BrushEngineKind.STAMP,
        size = 90f,
        opacity = 0.9f,
        flow = 0.9f,
        spacing = 0.6f,
        hardness = 0.9f,
        angleJitterDeg = 180f,
        sizeJitter = 0.2f,
        hueJitter = 0.1f,
    )

    private val particleDust = BrushPreset(
        id = "particle-dust",
        name = "Dust particles",
        category = BrushCategory.PARTICLE,
        engine = BrushEngineKind.SPRAY,
        size = 140f,
        opacity = 0.8f,
        flow = 0.4f,
        spacing = 0.18f,
        hardness = 0.5f,
        scatter = 1.6f,
        sizeJitter = 0.8f,
        valueJitter = 0.35f,
        pressureFlow = PressureResponse(min = 0.1f),
    )

    private val smudge = BrushPreset(
        id = "smudge-blender",
        name = "Smudge",
        category = BrushCategory.SMUDGE,
        engine = BrushEngineKind.SMUDGE,
        size = 48f,
        opacity = 0.6f,
        flow = 0.55f,
        spacing = 0.04f,
        hardness = 0.15f,
        pressureFlow = PressureResponse(min = 0.25f),
        stabilizer = 0.25f,
    )

    private val eraserSoft = BrushPreset(
        id = "eraser-soft",
        name = "Soft eraser",
        category = BrushCategory.ERASER,
        engine = BrushEngineKind.ERASER,
        size = 70f,
        opacity = 1f,
        flow = 0.35f,
        spacing = 0.03f,
        hardness = 0.05f,
        pressureFlow = PressureResponse(min = 0.2f),
    )

    private val eraserHard = BrushPreset(
        id = "eraser-hard",
        name = "Hard eraser",
        category = BrushCategory.ERASER,
        engine = BrushEngineKind.ERASER,
        size = 28f,
        opacity = 1f,
        flow = 1f,
        spacing = 0.03f,
        hardness = 0.95f,
        pressureFlow = PressureResponse(min = 0.85f),
    )

    private val sketchBlock = BrushPreset(
        id = "sketch-block",
        name = "Sketch block-in",
        category = BrushCategory.SKETCHING,
        engine = BrushEngineKind.ROUND,
        size = 16f,
        opacity = 0.55f,
        flow = 0.45f,
        spacing = 0.05f,
        hardness = 0.35f,
        velocitySize = 0.3f,
        pressureSize = PressureResponse(gamma = 1.4f),
        stabilizer = 0.05f,
    )

    private val inkBleed = BrushPreset(
        id = "ink-bleed",
        name = "Ink bleed",
        category = BrushCategory.INKING,
        engine = BrushEngineKind.TEXTURED,
        size = 20f,
        opacity = 0.9f,
        flow = 0.7f,
        spacing = 0.03f,
        hardness = 0.55f,
        grain = GrainKind.SPECKLE,
        grainScale = 0.9f,
        grainStrength = 0.5f,
        hueJitter = 0.05f,
        valueJitter = 0.15f,
    )

    /** Ordered by category so the brush panel groups naturally. */
    val builtIn: List<BrushPreset> = listOf(
        sketchBlock,
        pencil, softGraphite,
        mechanicalPencil, technicalPen,
        liner, taperedInk, dryInk, inkBleed,
        markerBroad, markerBrushTip,
        calligraphyChisel,
        airbrushSoft, airbrushFine,
        spray, particleDust,
        charcoalStick, vineCharcoal,
        softPastel, chalk,
        watercolorWash, wetWatercolor,
        oilBristle, acrylicFlat, gouache, paletteKnife,
        textureGrunge, stampLeaf,
        smudge,
        eraserSoft, eraserHard,
    )

    fun byId(id: String): BrushPreset? = builtIn.firstOrNull { it.id == id }

    fun byCategory(category: BrushCategory): List<BrushPreset> = builtIn.filter { it.category == category }

    fun categoriesInUse(): List<BrushCategory> = builtIn.map { it.category }.distinct()

    val default: BrushPreset get() = pencil

    /** Default eraser used by the eraser tool until the artist picks another. */
    val defaultEraser: BrushPreset get() = eraserHard
}

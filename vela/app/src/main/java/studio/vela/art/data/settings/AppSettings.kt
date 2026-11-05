package studio.vela.art.data.settings

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode(val label: String) {
    DARK("Dark"),
    LIGHT("Light"),
    SYSTEM("Follow system"),
    AMOLED("AMOLED black"),
}

@Serializable
enum class AccentChoice(val label: String) {
    LIME("Lime"),
    VIOLET("Violet"),
    CYAN("Cyan"),
    PINK("Pink"),
    CUSTOM("Custom"),
}

@Serializable
enum class CornerStyle(val label: String) {
    ROUNDED("Rounded"),
    SOFT("Soft"),
    SHARP("Sharp"),
}

@Serializable
enum class DensityLevel(val label: String) {
    COMPACT("Compact"),
    COMFORTABLE("Comfortable"),
    SPACIOUS("Spacious"),
}

/**
 * All user preferences in one serialisable object.
 *
 * Stored as a single JSON blob in DataStore: it keeps the key set in one place, makes adding
 * settings a one-line change, and avoids 30 hand-written preference keys.
 */
@Serializable
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accent: AccentChoice = AccentChoice.LIME,
    val customAccentArgb: Int = 0xFFC7F24E.toInt(),
    val cornerStyle: CornerStyle = CornerStyle.ROUNDED,
    val density: DensityLevel = DensityLevel.COMFORTABLE,
    val fontScale: Float = 1f,
    val reducedMotion: Boolean = false,
    val haptics: Boolean = true,

    // Stylus and gesture behaviour
    val palmRejection: Boolean = true,
    val drawWithFinger: Boolean = true,
    val eraserSwitchesTool: Boolean = true,
    val twoFingerUndo: Boolean = true,
    val threeFingerRedo: Boolean = true,
    val rotateGesture: Boolean = true,

    // Documents and performance
    val autosaveSeconds: Int = 5,
    val historyBudgetMb: Int = 160,
    val checkerboardBackground: Boolean = true,
    val showCanvasShadow: Boolean = true,

    // Editor defaults
    val defaultBrushId: String = "gouache-opaque",
    val defaultEraserId: String = "eraser-hard",
    val lastColorArgb: Int = 0xFF111111.toInt(),
)

/** Translates settings into the runtime input configuration. */
fun AppSettings.toInputPolicy(): studio.vela.art.core.input.InputPolicy =
    studio.vela.art.core.input.InputPolicy(
        palmRejection = palmRejection,
        drawWithFinger = drawWithFinger,
        stylusEraserSwitchesTool = eraserSwitchesTool,
        twoFingerTapUndo = twoFingerUndo,
        threeFingerTapRedo = threeFingerRedo,
        twoFingerRotate = rotateGesture,
    )

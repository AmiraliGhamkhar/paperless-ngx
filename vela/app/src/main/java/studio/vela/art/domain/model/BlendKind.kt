package studio.vela.art.domain.model

/**
 * Layer blend modes. Kept free of Android types so compositing behaviour can be tested
 * and so the persisted document format does not leak framework enums.
 */
enum class BlendKind(val label: String) {
    NORMAL("Normal"),
    MULTIPLY("Multiply"),
    SCREEN("Screen"),
    OVERLAY("Overlay"),
    DARKEN("Darken"),
    LIGHTEN("Lighten"),
    ADD("Add"),
    COLOR_DODGE("Color dodge"),
    COLOR_BURN("Color burn"),
    SOFT_LIGHT("Soft light"),
    HARD_LIGHT("Hard light"),
    DIFFERENCE("Difference"),
    EXCLUSION("Exclusion"),
    HUE("Hue"),
    SATURATION("Saturation"),
    COLOR("Color"),
    LUMINOSITY("Luminosity"),
    ;

    val isNormal: Boolean get() = this == NORMAL

    companion object {
        fun fromNameOrNormal(name: String?): BlendKind =
            entries.firstOrNull { it.name == name } ?: NORMAL
    }
}

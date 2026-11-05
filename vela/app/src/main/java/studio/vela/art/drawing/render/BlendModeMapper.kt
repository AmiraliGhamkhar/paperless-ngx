package studio.vela.art.drawing.render

import android.graphics.BlendMode
import studio.vela.art.domain.model.BlendKind

/** Maps the framework-independent [BlendKind] onto the platform compositing mode. */
fun BlendKind.toAndroidBlendMode(): BlendMode = when (this) {
    BlendKind.NORMAL -> BlendMode.SRC_OVER
    BlendKind.MULTIPLY -> BlendMode.MULTIPLY
    BlendKind.SCREEN -> BlendMode.SCREEN
    BlendKind.OVERLAY -> BlendMode.OVERLAY
    BlendKind.DARKEN -> BlendMode.DARKEN
    BlendKind.LIGHTEN -> BlendMode.LIGHTEN
    BlendKind.ADD -> BlendMode.PLUS
    BlendKind.COLOR_DODGE -> BlendMode.COLOR_DODGE
    BlendKind.COLOR_BURN -> BlendMode.COLOR_BURN
    BlendKind.SOFT_LIGHT -> BlendMode.SOFT_LIGHT
    BlendKind.HARD_LIGHT -> BlendMode.HARD_LIGHT
    BlendKind.DIFFERENCE -> BlendMode.DIFFERENCE
    BlendKind.EXCLUSION -> BlendMode.EXCLUSION
    BlendKind.HUE -> BlendMode.HUE
    BlendKind.SATURATION -> BlendMode.SATURATION
    BlendKind.COLOR -> BlendMode.COLOR
    BlendKind.LUMINOSITY -> BlendMode.LUMINOSITY
}

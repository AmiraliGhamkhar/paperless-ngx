package studio.vela.art.drawing.layers

import studio.vela.art.domain.model.BlendKind

/** Immutable snapshot of a layer's editable properties (used for undoable property edits). */
data class LayerProps(
    val name: String,
    val opacity: Float,
    val blend: BlendKind,
    val visible: Boolean,
    val locked: Boolean,
    val alphaLocked: Boolean,
    val clipping: Boolean,
) {
    fun applyTo(layer: Layer) {
        layer.name = name
        layer.opacity = opacity
        layer.blend = blend
        layer.visible = visible
        layer.locked = locked
        layer.alphaLocked = alphaLocked
        layer.clipping = clipping
        layer.markThumbnailDirty()
    }

    companion object {
        fun of(layer: Layer): LayerProps = LayerProps(
            name = layer.name,
            opacity = layer.opacity,
            blend = layer.blend,
            visible = layer.visible,
            locked = layer.locked,
            alphaLocked = layer.alphaLocked,
            clipping = layer.clipping,
        )
    }
}

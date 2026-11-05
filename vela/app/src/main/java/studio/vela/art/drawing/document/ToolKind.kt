package studio.vela.art.drawing.document

/**
 * Tools that are actually implemented. Unimplemented tools (selection, transform, text, vector)
 * are intentionally absent rather than shown as dead buttons.
 */
enum class ToolKind(val label: String) {
    BRUSH("Brush"),
    PENCIL("Pencil"),
    PEN("Pen"),
    ERASER("Eraser"),
    SMUDGE("Smudge"),
    FILL("Fill"),
    SHAPE("Shape"),
    EYEDROPPER("Eyedropper"),
    ;

    val isPaintTool: Boolean
        get() = this == BRUSH || this == PENCIL || this == PEN || this == ERASER || this == SMUDGE
}

enum class ShapeKind(val label: String) {
    LINE("Line"),
    RECTANGLE("Rectangle"),
    ELLIPSE("Ellipse"),
}

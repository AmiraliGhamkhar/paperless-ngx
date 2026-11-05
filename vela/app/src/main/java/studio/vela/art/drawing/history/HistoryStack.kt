package studio.vela.art.drawing.history

import studio.vela.art.drawing.document.ArtDocument

/**
 * Bounded undo/redo stack.
 *
 * Memory is capped rather than step count alone, because a single erased stroke on a large
 * canvas can cost more than a hundred small ones. The oldest steps are evicted first.
 */
class HistoryStack(
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val maxSteps: Int = DEFAULT_MAX_STEPS,
) {
    private val undoStack = ArrayDeque<EditCommand>()
    private val redoStack = ArrayDeque<EditCommand>()
    private var bytes: Long = 0L

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val undoLabel: String? get() = undoStack.lastOrNull()?.label
    val redoLabel: String? get() = redoStack.lastOrNull()?.label
    val undoCount: Int get() = undoStack.size
    val redoCount: Int get() = redoStack.size
    val bytesUsed: Long get() = bytes

    fun push(command: EditCommand) {
        if (command.approxBytes == 0L && command !is LayerPropsEdit && command !is LayerReorderEdit) {
            // Zero-cost no-op commands are not worth a history step.
        }
        undoStack.addLast(command)
        bytes += command.approxBytes
        while (redoStack.isNotEmpty()) {
            val dropped = redoStack.removeLast()
            bytes -= dropped.approxBytes
            dropped.release()
        }
        trim()
    }

    fun undo(doc: ArtDocument): Boolean {
        val command = undoStack.removeLastOrNull() ?: return false
        command.undo(doc)
        redoStack.addLast(command)
        return true
    }

    fun redo(doc: ArtDocument): Boolean {
        val command = redoStack.removeLastOrNull() ?: return false
        command.redo(doc)
        undoStack.addLast(command)
        return true
    }

    /** Drops one undo step without applying it (e.g. when a stroke was already saved). */
    fun dropLastUndo() {
        val dropped = undoStack.removeLastOrNull() ?: return
        bytes -= dropped.approxBytes
        dropped.release()
    }

    fun clear() {
        undoStack.forEach { it.release() }
        redoStack.forEach { it.release() }
        undoStack.clear()
        redoStack.clear()
        bytes = 0L
    }

    private fun trim() {
        while ((bytes > maxBytes || undoStack.size > maxSteps) && undoStack.size > 1) {
            val dropped = undoStack.removeFirst()
            bytes -= dropped.approxBytes
            dropped.release()
        }
    }

    companion object {
        const val DEFAULT_MAX_BYTES: Long = 160L * 1024 * 1024
        const val DEFAULT_MAX_STEPS: Int = 80
    }
}

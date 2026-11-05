package studio.vela.art.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Single indirection point for icon usage: keeps every screen consistent and makes swapping
 * an icon a one-line change.
 */
object VelaIcons {
    val back: ImageVector = Icons.Filled.ArrowBack
    val add: ImageVector = Icons.Filled.Add
    val close: ImageVector = Icons.Filled.Close
    val check: ImageVector = Icons.Filled.Check
    val delete: ImageVector = Icons.Filled.Delete
    val duplicate: ImageVector = Icons.Filled.ContentCopy
    val edit: ImageVector = Icons.Filled.Edit
    val more: ImageVector = Icons.Filled.MoreVert
    val search: ImageVector = Icons.Filled.Search
    val settings: ImageVector = Icons.Filled.Settings
    val tune: ImageVector = Icons.Filled.Tune
    val star: ImageVector = Icons.Filled.Star
    val starOutline: ImageVector = Icons.Filled.StarBorder
    val share: ImageVector = Icons.Filled.Share
    val download: ImageVector = Icons.Filled.Download
    val image: ImageVector = Icons.Filled.Image
    val layers: ImageVector = Icons.Filled.Layers
    val merge: ImageVector = Icons.Filled.CallMerge
    val palette: ImageVector = Icons.Filled.Palette
    val brush: ImageVector = Icons.Filled.Brush
    val pencil: ImageVector = Icons.Filled.Create
    val pen: ImageVector = Icons.Filled.Edit
    val eraser: ImageVector = Icons.Filled.Backspace
    val smudge: ImageVector = Icons.Filled.BlurOn
    val fill: ImageVector = Icons.Filled.FormatColorFill
    val shape: ImageVector = Icons.Filled.CropSquare
    val eyedropper: ImageVector = Icons.Filled.Colorize
    val undo: ImageVector = Icons.Filled.Undo
    val redo: ImageVector = Icons.Filled.Redo
    val visible: ImageVector = Icons.Filled.Visibility
    val hidden: ImageVector = Icons.Filled.VisibilityOff
    val lock: ImageVector = Icons.Filled.Lock
    val unlock: ImageVector = Icons.Filled.LockOpen
    val up: ImageVector = Icons.Filled.ArrowUpward
    val down: ImageVector = Icons.Filled.ArrowDownward
    val zoomIn: ImageVector = Icons.Filled.ZoomIn
    val zoomOut: ImageVector = Icons.Filled.ZoomOut
    val opacity: ImageVector = Icons.Filled.Opacity
    val grid: ImageVector = Icons.Filled.GridOn
    val gesture: ImageVector = Icons.Filled.Gesture
    val info: ImageVector = Icons.Filled.Info
}

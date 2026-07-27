package io.github.mabrur.streamly.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object StreamlyShapes {
    /** Buttons, chips, badges, avatars — the design uses 999px everywhere. */
    val Pill = RoundedCornerShape(percent = 50)
    val Thumbnail = RoundedCornerShape(14.dp)
    val SmallThumbnail = RoundedCornerShape(10.dp)
    val Button = RoundedCornerShape(12.dp)
    val Dialog = RoundedCornerShape(18.dp)
    val Logo = RoundedCornerShape(22.dp)

    val material = Shapes(
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(14.dp),
        large = RoundedCornerShape(18.dp),
    )
}

// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

private val TrashRed = Color(0xFFFF3B30)

/** Heart on card photos (Figma): lime outline; filled lime when the listing is in Favorites. */
@Composable
fun HeartButton(favorite: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(36.dp).clickable(role = Role.Checkbox, onClickLabel = if (favorite) "Remove from favorites" else "Add to favorites", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(24.dp)) {
            val w = size.width; val h = size.height
            val p = Path().apply {
                moveTo(w * 0.5f, h * 0.88f)
                cubicTo(w * 0.1f, h * 0.6f, w * 0.02f, h * 0.38f, w * 0.1f, h * 0.22f)
                cubicTo(w * 0.2f, h * 0.04f, w * 0.44f, h * 0.06f, w * 0.5f, h * 0.28f)
                cubicTo(w * 0.56f, h * 0.06f, w * 0.8f, h * 0.04f, w * 0.9f, h * 0.22f)
                cubicTo(w * 0.98f, h * 0.38f, w * 0.9f, h * 0.6f, w * 0.5f, h * 0.88f)
                close()
            }
            if (favorite) drawPath(p, FttColors.StartLime)
            drawPath(p, if (favorite) FttColors.OnLime else FttColors.StartLime, style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round))
        }
    }
}

/** Trash can over the photo on every My Claims card (Figma): red bin on a translucent white tile. */
@Composable
fun TrashButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(36.dp).clickable(role = Role.Button, onClickLabel = "Remove request", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(28.dp).background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(18.dp)) {
                val w = size.width; val h = size.height
                // lid + handle
                drawLine(TrashRed, Offset(w * 0.12f, h * 0.2f), Offset(w * 0.88f, h * 0.2f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                drawRoundRect(TrashRed, Offset(w * 0.36f, h * 0.06f), Size(w * 0.28f, h * 0.1f), CornerRadius(2.dp.toPx()))
                // body
                drawRoundRect(TrashRed, Offset(w * 0.2f, h * 0.3f), Size(w * 0.6f, h * 0.64f), CornerRadius(2.dp.toPx()))
            }
        }
    }
}

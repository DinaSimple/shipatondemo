// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** "UPROCK / Buttons" — lime, 50dp, radius 10, 1dp border. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, FttColors.OnLime),
        colors = ButtonDefaults.buttonColors(containerColor = FttColors.Lime, contentColor = FttColors.OnLime),
    ) { Text(text, style = FttType.bodyBold()) }
}

/** "UPROCK / Controls / Page Controls": active 32×6 black, inactive 6×6 gray, gap 3. */
@Composable
fun PageDots(count: Int, selected: Int, modifier: Modifier = Modifier) {
    Row(modifier.height(44.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            Box(
                Modifier
                    .height(6.dp)
                    .width(if (i == selected) 32.dp else 6.dp)
                    .background(if (i == selected) FttColors.TextPrimary else FttColors.DotInactive, RoundedCornerShape(10.dp))
            )
        }
    }
}

/** Back chevron (Icon/Dark/Arrow Back, 28dp). */
@Composable
fun BackChevron(modifier: Modifier = Modifier) {
    Canvas(modifier.size(28.dp).padding(6.dp)) {
        val p = Path().apply {
            moveTo(size.width * 0.62f, size.height * 0.12f)
            lineTo(size.width * 0.25f, size.height * 0.5f)
            lineTo(size.width * 0.62f, size.height * 0.88f)
        }
        drawPath(p, FttColors.TextPrimary, style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

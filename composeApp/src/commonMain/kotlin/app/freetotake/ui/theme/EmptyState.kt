// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.freetotake.domain.catalog.HomeCopy
import app.freetotake.resources.Res
import app.freetotake.resources.empty_mascot
import org.jetbrains.compose.resources.painterResource

/** v1.13: one empty state everywhere — "Nothing here yet." + the hero mascot (Figma reference). */
@Composable
fun EmptyState(modifier: Modifier = Modifier, text: String = HomeCopy.EMPTY) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text, style = FttType.subheadline(), color = FttColors.LabelSecondary, textAlign = TextAlign.Center)
        Image(painterResource(Res.drawable.empty_mascot), contentDescription = null, modifier = Modifier.size(width = 101.dp, height = 90.dp))
    }
}

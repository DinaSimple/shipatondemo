// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.ui.rewards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.freetotake.domain.rewards.BroccoliCopy
import app.freetotake.domain.rewards.PendingHandover
import app.freetotake.resources.Res
import app.freetotake.resources.broccoli_icon
import app.freetotake.resources.broccoli_mascot
import app.freetotake.ui.platform.PlatformBackHandler
import app.freetotake.ui.theme.FttColors
import app.freetotake.ui.theme.FttType
import app.freetotake.ui.theme.PrimaryButton
import org.jetbrains.compose.resources.painterResource

/** Home, top right: lime balance pill. Tap → rules. */
@Composable
fun BroccoliChip(balance: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.height(50.dp)
            .background(FttColors.StartLime, RoundedCornerShape(10.dp))
            .border(1.dp, FttColors.OnLime, RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClickLabel = "Broccoli rules", onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Image(painterResource(Res.drawable.broccoli_icon), contentDescription = null, modifier = Modifier.size(28.dp))
        Text("$balance", style = FttType.bodyBold(), color = FttColors.OnLime)
    }
}

/** Figma "homescreen remade": under My giveaways — balance panel + Start button. */
@Composable
fun BroccoliPanel(balance: Int, onStart: () -> Unit, onRules: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().background(FttColors.BackgroundSecondary, RoundedCornerShape(16.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().background(FttColors.BackgroundPrimary, RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClickLabel = "Broccoli rules", onClick = onRules)
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Image(painterResource(Res.drawable.broccoli_icon), contentDescription = null, modifier = Modifier.size(36.dp))
            Spacer(Modifier.size(10.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(BroccoliCopy.balance(balance), style = FttType.body().copy(fontSize = 17.sp), color = FttColors.LabelSecondary)
                Text(BroccoliCopy.HOME_HINT, style = FttType.caption().copy(fontSize = 12.sp), color = FttColors.LabelSecondary)
            }
        }
        PrimaryButton("Start", onClick = onStart)
    }
}

@Composable
fun BroccoliRulesDialog(balance: Int, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().background(FttColors.BackgroundSecondary, RoundedCornerShape(16.dp)).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Image(painterResource(Res.drawable.broccoli_mascot), contentDescription = null, modifier = Modifier.size(96.dp))
            Text(BroccoliCopy.RULES_TITLE, style = FttType.title1Bold().copy(fontSize = 22.sp))
            Text(BroccoliCopy.balance(balance), style = FttType.bodyBold(),
                modifier = Modifier.background(FttColors.StartLime, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                color = FttColors.OnLime)
            Text(BroccoliCopy.RULES_START, style = FttType.subheadline(), color = FttColors.TextSecondary, textAlign = TextAlign.Center)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BroccoliCopy.RULES.forEach { Text(it, style = FttType.subheadline().copy(fontSize = 14.sp)) }
            }
            Spacer(Modifier.height(4.dp))
            PrimaryButton(BroccoliCopy.OK, onClick = onDismiss)
        }
    }
}

/** Shown on Home once the giveaway's pickup time has passed: the giver confirms the handover (+1 when collected). */
@Composable
fun HandoverConfirmDialog(h: PendingHandover, busy: Boolean, onAnswer: (collected: Boolean) -> Unit) {
    Dialog(onDismissRequest = {}) {   // must be answered
        Column(
            Modifier.fillMaxWidth().background(FttColors.BackgroundSecondary, RoundedCornerShape(16.dp)).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(painterResource(Res.drawable.broccoli_mascot), contentDescription = null, modifier = Modifier.size(96.dp))
            Text(BroccoliCopy.confirmTitle(h), style = FttType.bodyBold().copy(fontSize = 18.sp), textAlign = TextAlign.Center)
            Text(BroccoliCopy.CONFIRM_TEXT, style = FttType.subheadline(), color = FttColors.TextSecondary, textAlign = TextAlign.Center)
            PrimaryButton(if (busy) "Saving…" else BroccoliCopy.CONFIRM_YES, onClick = { if (!busy) onAnswer(true) })
            Button(
                onClick = { if (!busy) onAnswer(false) },
                modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, FttColors.ButtonBorder),
                colors = ButtonDefaults.buttonColors(containerColor = FttColors.BackgroundSecondary, contentColor = FttColors.TextSecondary),
            ) { Text(BroccoliCopy.CONFIRM_NO, style = FttType.body()) }
        }
    }
}

/** Figma "Broccoli / Post login": unskippable, shown once after onboarding (also when it was skipped). */
@Composable
fun BroccoliRewardScreen(onContinue: () -> Unit) {
    PlatformBackHandler(enabled = true) { }   // cannot be skipped
    Column(
        Modifier.fillMaxSize().background(FttColors.BackgroundPrimary).statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(96.dp))
        Text(BroccoliCopy.REWARD_TITLE, style = FttType.largeTitleBold())
        Spacer(Modifier.height(6.dp))
        Text(BroccoliCopy.REWARD_SUBTITLE, style = FttType.subheadline())
        Spacer(Modifier.height(24.dp))
        Column(
            Modifier.weight(1f, fill = false).fillMaxWidth().background(FttColors.BackgroundSecondary, RoundedCornerShape(16.dp)).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.weight(1f, fill = false).widthIn(max = 300.dp).aspectRatio(720f / 681f)) {
                Image(painterResource(Res.drawable.broccoli_mascot), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
            Spacer(Modifier.height(24.dp))
            Text(BroccoliCopy.REWARD_TEXT, style = FttType.subheadline(), textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(BroccoliCopy.CONTINUE, onClick = onContinue)
        Spacer(Modifier.height(24.dp))
    }
}

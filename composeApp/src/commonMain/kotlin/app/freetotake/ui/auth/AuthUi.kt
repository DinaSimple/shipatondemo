// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.freetotake.domain.rules.UserAction
import app.freetotake.ui.theme.BackChevron
import app.freetotake.ui.theme.FttColors
import app.freetotake.ui.theme.FttType
import app.freetotake.ui.theme.PrimaryButton

fun UserAction.label(): String = when (this) {
    UserAction.POST_ITEM -> "create a giveaway"
    UserAction.MANAGE_OWN_ITEM -> "manage your giveaway"
    UserAction.SUBMIT_CLAIM -> "send a request"
    UserAction.CANCEL_CLAIM -> "cancel a request"
    UserAction.REVIEW_REQUESTS -> "review requests"
    UserAction.PRIVATE_CHAT -> "chat"
    UserAction.VIEW_PICKUP_DETAILS -> "see pickup details"
    UserAction.VIEW_MY_CLAIMS -> "see My Claims"
    UserAction.VIEW_MY_ITEMS -> "open My Applications"
    UserAction.FAVORITES -> "use Favorites"
    else -> "continue"
}

/** Authentication-required prompt shown when a guest starts a protected action. */
@Composable
fun AuthRequiredPrompt(action: UserAction, onSignIn: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FttColors.BackgroundSecondary,
        title = { Text("Sign in to continue", style = FttType.bodyBold()) },
        text = {
            Text(
                "You need an account to ${action.label()}. Browsing stays open without one.",
                style = FttType.body(), color = FttColors.TextSecondary,
            )
        },
        confirmButton = { TextButton(onClick = onSignIn) { Text("Sign in", style = FttType.bodyBold(), color = FttColors.TextPrimary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now", style = FttType.body(), color = FttColors.TextSecondary) } },
    )
}

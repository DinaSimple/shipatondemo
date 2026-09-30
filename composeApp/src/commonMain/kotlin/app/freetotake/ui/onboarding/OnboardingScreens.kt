// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.freetotake.domain.onboarding.OnboardingEvent
import app.freetotake.domain.onboarding.OnboardingStep
import app.freetotake.resources.Res
import app.freetotake.resources.logo
import app.freetotake.resources.onboarding_giveaway
import app.freetotake.resources.broccoli_coin
import app.freetotake.resources.onboarding_meetup
import app.freetotake.resources.onboarding_request
import app.freetotake.resources.onboarding_welcome
import app.freetotake.ui.theme.BackChevron
import app.freetotake.ui.theme.FttColors
import app.freetotake.ui.theme.FttType
import app.freetotake.ui.theme.PageDots
import app.freetotake.ui.theme.PrimaryButton
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private data class Page(val illustration: DrawableResource, val caption: String, val wide: Boolean = true)

/** Copy from Figma (approved over spec wording). */
private fun pageFor(step: OnboardingStep): Page = when (step) {
    OnboardingStep.WELCOME -> Page(Res.drawable.onboarding_welcome, "Give away or take for free food, clothes, and more!")
    OnboardingStep.GIVEAWAY -> Page(Res.drawable.onboarding_giveaway, "Post your giveaways and your available time!")
    OnboardingStep.MEETUP -> Page(Res.drawable.onboarding_meetup, "Choose who picks up your giveaways at your preferred meetup point!")
    OnboardingStep.BROCCOLI -> Page(Res.drawable.broccoli_coin, app.freetotake.domain.rewards.BroccoliCopy.ONBOARDING_CAPTION)
    else -> Page(Res.drawable.onboarding_request, "Send requests for what you need. Easy finds that make you happy!", wide = false)
}

/**
 * Renders SPLASH / info pages / final page. Drives auto-advance from [OnboardingStep.autoAdvanceMs];
 * all transitions go through the shared [app.freetotake.domain.onboarding.OnboardingFlow] via [onEvent].
 */
@Composable
fun OnboardingHost(step: OnboardingStep, onEvent: (OnboardingEvent) -> Unit) {
    step.autoAdvanceMs?.let { ms ->
        LaunchedEffect(step) {
            delay(ms)
            onEvent(OnboardingEvent.AutoAdvanceElapsed)
        }
    }
    if (step == OnboardingStep.SPLASH) {
        SplashScreen()
    } else {
        // Permission steps keep the final page visible under the system dialogs.
        val pageStep = if (step.isPage) step else OnboardingStep.BROCCOLI
        OnboardingPage(pageStep, onEvent)
    }
}

@Composable
fun SplashScreen() {
    Box(
        Modifier.fillMaxSize().background(FttColors.BackgroundSecondary).statusBarsPadding().navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(Res.drawable.logo), contentDescription = "Free to Take", modifier = Modifier.size(width = 150.dp, height = 140.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit)   // v1.15.4: uncropped logo
    }
}

@Composable
private fun OnboardingPage(step: OnboardingStep, onEvent: (OnboardingEvent) -> Unit) {
    Column(
        Modifier.fillMaxSize().background(FttColors.BackgroundPrimary).statusBarsPadding().navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Navigation bar: back (left) / Skip (right), 42dp
        Box(Modifier.fillMaxWidth().height(42.dp)) {
            if (step.showsBack) {
                Box(
                    Modifier.align(Alignment.CenterStart).padding(start = 7.dp)
                        .clickable(role = Role.Button, onClickLabel = "Back") { onEvent(OnboardingEvent.Back) }
                ) { BackChevron() }
            }
            if (step.showsSkip) {
                Text(
                    "Skip", style = FttType.body(), color = FttColors.TextSecondary,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp)
                        .clickable(role = Role.Button) { onEvent(OnboardingEvent.Skip) },
                )
            }
        }
        // Title block
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Welcome to", style = FttType.title2(), color = FttColors.TextPrimary)
            Text("Free to Take!", style = FttType.largeTitleBold(), color = FttColors.TextPrimary)
        }
        // Card (animated between pages)
        AnimatedContent(
            targetState = step,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                val forward = (targetState.pageIndex ?: 0) >= (initialState.pageIndex ?: 0)
                (slideInHorizontally { if (forward) it else -it } + fadeIn()) togetherWith
                    (slideOutHorizontally { if (forward) -it else it } + fadeOut())
            },
            label = "onboarding-card",
        ) { s -> OnboardingCard(pageFor(s)) }

        PageDots(OnboardingStep.PAGE_COUNT, step.pageIndex ?: 0)

        // Action bar (Figma "Complete onboarding"): "Next" on screens 1–3, "Start" on the final screen.
        Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp).height(50.dp)) {
            if (step.showsNextButton) PrimaryButton("Next", onClick = { onEvent(OnboardingEvent.NextTapped) })
            if (step.showsStartButton) PrimaryButton("Start", onClick = { onEvent(OnboardingEvent.StartTapped) })
        }
    }
}

@Composable
private fun OnboardingCard(page: Page) {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 427.dp).fillMaxSize()
                .background(FttColors.BackgroundSecondary, RoundedCornerShape(12.dp))
                .padding(top = 50.dp, bottom = 24.dp, start = 30.dp, end = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painterResource(page.illustration),
                contentDescription = null,
                modifier = Modifier.weight(1f, fill = false)
                    .widthIn(max = if (page.wide) 298.dp else 247.dp)
                    .aspectRatio(if (page.wide) 1f else 247f / 307.5f),
            )
            Spacer(Modifier.height(15.dp))
            Text(
                page.caption, style = FttType.subheadlineBold(), color = FttColors.TextPrimary,
                textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 308.dp),
            )
        }
    }
}

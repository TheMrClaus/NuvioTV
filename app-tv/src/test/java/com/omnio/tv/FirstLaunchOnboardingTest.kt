package com.omnio.tv

import org.junit.Assert.assertEquals
import org.junit.Test

class FirstLaunchOnboardingTest {

    @Test
    fun `returns welcome before pairing on first launch`() {
        assertEquals(
            FirstLaunchOnboardingStep.Welcome,
            resolveFirstLaunchOnboardingStep(
                hasSeenAuthQrOnFirstLaunch = false,
                isSignedIn = false,
                onboardingCompletedThisSession = false,
                hasAdvancedPastWelcome = false
            )
        )
    }

    @Test
    fun `returns pair after welcome is dismissed`() {
        assertEquals(
            FirstLaunchOnboardingStep.Pair,
            resolveFirstLaunchOnboardingStep(
                hasSeenAuthQrOnFirstLaunch = false,
                isSignedIn = false,
                onboardingCompletedThisSession = false,
                hasAdvancedPastWelcome = true
            )
        )
    }

    @Test
    fun `returns no onboarding step after completion`() {
        assertEquals(
            FirstLaunchOnboardingStep.Complete,
            resolveFirstLaunchOnboardingStep(
                hasSeenAuthQrOnFirstLaunch = false,
                isSignedIn = false,
                onboardingCompletedThisSession = true,
                hasAdvancedPastWelcome = true
            )
        )
    }
}

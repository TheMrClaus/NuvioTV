package com.omnio.tv

import com.omnio.tv.ui.screens.account.AuthQrBrandingTreatment
import com.omnio.tv.ui.screens.account.authQrBrandingTreatment
import com.omnio.tv.ui.screens.account.shouldShowWaitingStatePlaceholderSurfaces
import com.omnio.tv.ui.screens.profile.ProfileSelectionMainLayoutTreatment
import com.omnio.tv.ui.screens.profile.profileSelectionMainLayoutTreatment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstLaunchOnboardingTest {

    @Test
    fun `auth branding uses only the full Omnio wordmark`() {
        assertEquals(
            AuthQrBrandingTreatment(
                showMark = false,
                showWordmark = true
            ),
            authQrBrandingTreatment()
        )
    }

    @Test
    fun `waiting approval state hides empty placeholder surfaces`() {
        assertFalse(
            shouldShowWaitingStatePlaceholderSurfaces(
                isSignedIn = false,
                hasQrBitmap = false,
                isLoading = false
            )
        )
    }

    @Test
    fun `qr generation state still shows placeholder surface while loading code`() {
        assertTrue(
            shouldShowWaitingStatePlaceholderSurfaces(
                isSignedIn = false,
                hasQrBitmap = false,
                isLoading = true
            )
        )
    }

    @Test
    fun `profile picker reserves explicit gap and bottom hint space`() {
        assertEquals(
            ProfileSelectionMainLayoutTreatment(
                titleToGridGapDp = 56,
                bottomHintPaddingDp = 24,
                usesFlexibleBottomSpacer = true
            ),
            profileSelectionMainLayoutTreatment()
        )
    }

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

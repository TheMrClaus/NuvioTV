package com.omnio.tv

enum class FirstLaunchOnboardingStep {
    Welcome,
    Pair,
    Complete
}

fun resolveFirstLaunchOnboardingStep(
    hasSeenAuthQrOnFirstLaunch: Boolean?,
    isSignedIn: Boolean,
    onboardingCompletedThisSession: Boolean,
    hasAdvancedPastWelcome: Boolean
): FirstLaunchOnboardingStep? {
    if (hasSeenAuthQrOnFirstLaunch == null || hasSeenAuthQrOnFirstLaunch) return null
    if (onboardingCompletedThisSession || isSignedIn) return FirstLaunchOnboardingStep.Complete
    return if (hasAdvancedPastWelcome) {
        FirstLaunchOnboardingStep.Pair
    } else {
        FirstLaunchOnboardingStep.Welcome
    }
}

package wiki.asaf.wikisayit.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import wiki.asaf.wikisayit.ui.about.AboutScreen
import wiki.asaf.wikisayit.ui.common.PlaceholderScreen
import wiki.asaf.wikisayit.ui.contribution.ContributionScreen
import wiki.asaf.wikisayit.ui.done.DoneScreen
import wiki.asaf.wikisayit.ui.language.LanguageScreen
import wiki.asaf.wikisayit.ui.listsource.ListSourceScreen
import wiki.asaf.wikisayit.ui.profile.NewProfileScreen
import wiki.asaf.wikisayit.ui.profile.ProfileScreen
import wiki.asaf.wikisayit.ui.recording.RecordingScreen
import wiki.asaf.wikisayit.ui.review.ReviewScreen
import wiki.asaf.wikisayit.ui.session.RecordingFlowViewModel
import wiki.asaf.wikisayit.ui.settings.SettingsScreen
import wiki.asaf.wikisayit.ui.signin.SignInScreen
import wiki.asaf.wikisayit.ui.stats.StatsScreen
import wiki.asaf.wikisayit.ui.summary.SessionSummaryScreen

/**
 * [sessionViewModel] is created once in [wiki.asaf.wikisayit.MainActivity] (Activity-scoped,
 * not per-route) because the recording flow's state — selected profile/language, the
 * in-progress queue, review/approved lists — has to survive forward and back navigation
 * across every route from Profile to Done.
 */
@Composable
fun WikiSayItNavHost(
    navController: NavHostController,
    sessionViewModel: RecordingFlowViewModel,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = WikiSayItRoute.SignIn,
        modifier = modifier,
    ) {
        composable<WikiSayItRoute.SignIn> {
            SignInScreen(
                onSignIn = {
                    navController.navigate(WikiSayItRoute.Profile) {
                        popUpTo(WikiSayItRoute.SignIn) { inclusive = true }
                    }
                },
            )
        }
        composable<WikiSayItRoute.NewProfile> {
            NewProfileScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }
        composable<WikiSayItRoute.Profile> {
            ProfileScreen(
                viewModel = sessionViewModel,
                onProfileSelected = { navController.navigate(WikiSayItRoute.Language) },
                onNewProfile = { navController.navigate(WikiSayItRoute.NewProfile) },
            )
        }
        composable<WikiSayItRoute.Language> {
            LanguageScreen(
                viewModel = sessionViewModel,
                onLanguagePicked = { navController.navigate(WikiSayItRoute.ListSource) },
            )
        }
        composable<WikiSayItRoute.ListSource> {
            ListSourceScreen(
                viewModel = sessionViewModel,
                onStartRecording = { navController.navigate(WikiSayItRoute.Recording) },
            )
        }
        composable<WikiSayItRoute.Disambiguation> {
            PlaceholderScreen(title = "Disambiguation")
        }
        composable<WikiSayItRoute.Recording> {
            RecordingScreen(
                viewModel = sessionViewModel,
                onNavigateReview = { navController.navigate(WikiSayItRoute.Review) },
                onNavigateSummary = { navController.navigate(WikiSayItRoute.SessionSummary) },
                onNavigateListSource = { navController.popBackStack(WikiSayItRoute.ListSource, inclusive = false) },
            )
        }
        composable<WikiSayItRoute.Review> {
            ReviewScreen(
                viewModel = sessionViewModel,
                onNavigateRecording = {
                    navController.navigate(WikiSayItRoute.Recording) {
                        popUpTo(WikiSayItRoute.Recording) { inclusive = true }
                    }
                },
                onNavigateSummary = { navController.navigate(WikiSayItRoute.SessionSummary) },
            )
        }
        composable<WikiSayItRoute.SessionSummary> {
            SessionSummaryScreen(
                viewModel = sessionViewModel,
                onContribute = { navController.navigate(WikiSayItRoute.Contribution) },
                onBackToStart = {
                    navController.navigate(WikiSayItRoute.Profile) {
                        popUpTo(WikiSayItRoute.Profile) { inclusive = true }
                    }
                },
            )
        }
        composable<WikiSayItRoute.Contribution> {
            ContributionScreen(
                viewModel = sessionViewModel,
                onNavigateDone = {
                    navController.navigate(WikiSayItRoute.Done) {
                        popUpTo(WikiSayItRoute.Contribution) { inclusive = true }
                    }
                },
            )
        }
        composable<WikiSayItRoute.Done> {
            DoneScreen(
                viewModel = sessionViewModel,
                onRecordAnotherList = {
                    navController.navigate(WikiSayItRoute.Profile) {
                        popUpTo(WikiSayItRoute.Profile) { inclusive = true }
                    }
                },
            )
        }
        composable<WikiSayItRoute.Settings> {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable<WikiSayItRoute.Stats> {
            StatsScreen(onBack = { navController.popBackStack() })
        }
        composable<WikiSayItRoute.About> {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}

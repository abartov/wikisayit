package wiki.asaf.wikisayit.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import wiki.asaf.wikisayit.R
import wiki.asaf.wikisayit.ui.common.PlaceholderScreen
import wiki.asaf.wikisayit.ui.profile.ProfileScreen
import wiki.asaf.wikisayit.ui.session.RecordingFlowViewModel
import wiki.asaf.wikisayit.ui.signin.SignInScreen

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
            PlaceholderScreen(title = "New profile")
        }
        composable<WikiSayItRoute.Profile> {
            ProfileScreen(
                viewModel = sessionViewModel,
                onProfileSelected = { navController.navigate(WikiSayItRoute.Language) },
                onNewProfile = { navController.navigate(WikiSayItRoute.NewProfile) },
            )
        }
        composable<WikiSayItRoute.Language> {
            PlaceholderScreen(title = stringResource(R.string.nav_destination_language))
        }
        composable<WikiSayItRoute.ListSource> {
            PlaceholderScreen(title = stringResource(R.string.nav_destination_list_source))
        }
        composable<WikiSayItRoute.Disambiguation> {
            PlaceholderScreen(title = "Disambiguation")
        }
        composable<WikiSayItRoute.Recording> {
            PlaceholderScreen(title = stringResource(R.string.nav_destination_recording))
        }
        composable<WikiSayItRoute.Review> {
            PlaceholderScreen(title = stringResource(R.string.nav_destination_review))
        }
        composable<WikiSayItRoute.SessionSummary> {
            PlaceholderScreen(title = "Session summary")
        }
        composable<WikiSayItRoute.Contribution> {
            PlaceholderScreen(title = stringResource(R.string.nav_destination_contribution))
        }
        composable<WikiSayItRoute.Done> {
            PlaceholderScreen(title = "Done")
        }
        composable<WikiSayItRoute.Settings> {
            PlaceholderScreen(title = stringResource(R.string.nav_destination_settings))
        }
        composable<WikiSayItRoute.Stats> {
            PlaceholderScreen(title = stringResource(R.string.nav_destination_stats))
        }
        composable<WikiSayItRoute.About> {
            PlaceholderScreen(title = stringResource(R.string.nav_destination_about))
        }
    }
}

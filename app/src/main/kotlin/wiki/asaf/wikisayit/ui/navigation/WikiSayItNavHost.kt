package wiki.asaf.wikisayit.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import wiki.asaf.wikisayit.R
import wiki.asaf.wikisayit.ui.common.PlaceholderScreen
import wiki.asaf.wikisayit.ui.welcome.WelcomeScreen

@Composable
fun WikiSayItNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = WikiSayItRoute.Profile,
        modifier = modifier,
    ) {
        composable<WikiSayItRoute.Profile> {
            WelcomeScreen()
        }
        composable<WikiSayItRoute.Language> {
            PlaceholderScreen(title = stringResource(R.string.nav_destination_language))
        }
        composable<WikiSayItRoute.ListSource> {
            PlaceholderScreen(title = stringResource(R.string.nav_destination_list_source))
        }
        composable<WikiSayItRoute.Recording> {
            PlaceholderScreen(title = stringResource(R.string.nav_destination_recording))
        }
        composable<WikiSayItRoute.Review> {
            PlaceholderScreen(title = stringResource(R.string.nav_destination_review))
        }
        composable<WikiSayItRoute.Contribution> {
            PlaceholderScreen(title = stringResource(R.string.nav_destination_contribution))
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

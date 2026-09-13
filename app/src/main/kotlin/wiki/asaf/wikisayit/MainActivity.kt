package wiki.asaf.wikisayit

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import wiki.asaf.wikisayit.network.oauth.OAuthConfig
import wiki.asaf.wikisayit.network.oauth.OAuthLoginController
import wiki.asaf.wikisayit.ui.navigation.WikiSayItNavHost
import wiki.asaf.wikisayit.ui.navigation.WikiSayItRoute
import wiki.asaf.wikisayit.ui.scaffold.WikiSayItAppScaffold
import wiki.asaf.wikisayit.ui.session.RecordingFlowViewModel
import wiki.asaf.wikisayit.ui.theme.WikiSayItTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var oAuthLoginController: OAuthLoginController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleOAuthRedirect(intent)
        setContent {
            WikiSayItTheme {
                val navController = rememberNavController()
                val sessionViewModel: RecordingFlowViewModel = hiltViewModel()
                val uiState by sessionViewModel.uiState.collectAsStateWithLifecycle()
                val backStackEntry by navController.currentBackStackEntryAsState()

                // The shared chrome (hamburger/wordmark/chip) is unconditional across the main
                // flow in the design handoff; only Sign-in (no chrome) and New profile (its own
                // back-chevron bar) opt out.
                val showTopBar =
                    backStackEntry?.destination?.let {
                        !it.hasRoute<WikiSayItRoute.SignIn>() && !it.hasRoute<WikiSayItRoute.NewProfile>()
                    } ?: true
                val username = uiState.activeProfile?.profile?.wikimediaUsername
                val chipText =
                    when {
                        uiState.language != null && username != null -> "${uiState.language!!.isoCode} · $username"
                        username != null -> username
                        else -> ""
                    }
                val menuFooterText =
                    if (username != null) {
                        stringResource(R.string.nav_menu_signed_in_as, username)
                    } else {
                        ""
                    }

                WikiSayItAppScaffold(
                    showTopBar = showTopBar,
                    chipText = chipText,
                    menuFooterText = menuFooterText,
                    onSettingsClick = { navController.navigate(WikiSayItRoute.Settings) },
                    onStatsClick = { navController.navigate(WikiSayItRoute.Stats) },
                    onAboutClick = { navController.navigate(WikiSayItRoute.About) },
                ) { contentModifier ->
                    WikiSayItNavHost(
                        navController = navController,
                        sessionViewModel = sessionViewModel,
                        modifier = contentModifier,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthRedirect(intent)
    }

    /** Forwards the OAuth redirect (`android:launchMode="singleTask"` brings it back here as a new intent). */
    private fun handleOAuthRedirect(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != OAuthConfig.REDIRECT_SCHEME) return
        lifecycleScope.launch { oAuthLoginController.handleRedirect(data.toString()) }
    }
}

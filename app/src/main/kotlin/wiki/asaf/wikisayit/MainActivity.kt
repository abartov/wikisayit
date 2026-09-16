package wiki.asaf.wikisayit

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import wiki.asaf.wikisayit.data.local.db.RecordingStatDao
import wiki.asaf.wikisayit.data.local.settings.SettingsRepository
import wiki.asaf.wikisayit.data.profile.ProfileRepository
import wiki.asaf.wikisayit.network.oauth.OAuthConfig
import wiki.asaf.wikisayit.network.oauth.OAuthLoginController
import wiki.asaf.wikisayit.ui.debug.SCREENSHOT_TARGET_EXTRA
import wiki.asaf.wikisayit.ui.debug.ScreenshotTarget
import wiki.asaf.wikisayit.ui.debug.applyScreenshotTarget
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

    // Only ever read behind BuildConfig.DEBUG, by the screenshot harness below.
    @Inject
    lateinit var profileRepository: ProfileRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var recordingStatDao: RecordingStatDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleOAuthRedirect(intent)
        setContent {
            WikiSayItTheme {
                // Nothing else in the tree paints an opaque root background, so without this the
                // native window background (light or dark per values[-night]/themes.xml) shows
                // through in gaps/insets — mismatched against theme-aware text colors in dark mode.
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()
                    val sessionViewModel: RecordingFlowViewModel = hiltViewModel()
                    val uiState by sessionViewModel.uiState.collectAsStateWithLifecycle()
                    val backStackEntry by navController.currentBackStackEntryAsState()

                    if (BuildConfig.DEBUG) {
                        LaunchedEffect(Unit) {
                            val target =
                                intent.getStringExtra(SCREENSHOT_TARGET_EXTRA)
                                    ?.let { name -> runCatching { ScreenshotTarget.valueOf(name) }.getOrNull() }
                                    ?: return@LaunchedEffect
                            val route =
                                applyScreenshotTarget(
                                    target = target,
                                    sessionViewModel = sessionViewModel,
                                    profileRepository = profileRepository,
                                    settingsRepository = settingsRepository,
                                    recordingStatDao = recordingStatDao,
                                )
                            navController.navigate(route) { popUpTo(0) { inclusive = true } }
                        }
                    }

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
                            uiState.language != null && username != null ->
                                "${uiState.language!!.isoCode} · $username"
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
                        onTitleClick = sessionViewModel::askResetSession,
                    ) { contentModifier ->
                        WikiSayItNavHost(
                            navController = navController,
                            sessionViewModel = sessionViewModel,
                            modifier = contentModifier,
                        )
                    }

                    if (uiState.showResetSessionDialog) {
                        AlertDialog(
                            onDismissRequest = sessionViewModel::cancelResetSession,
                            title = { Text(stringResource(R.string.top_bar_reset_dialog_title)) },
                            text = { Text(stringResource(R.string.top_bar_reset_dialog_body)) },
                            confirmButton = {
                                TextButton(onClick = {
                                    sessionViewModel.confirmResetSession()
                                    navController.navigate(WikiSayItRoute.Profile) {
                                        popUpTo(WikiSayItRoute.Profile) { inclusive = true }
                                    }
                                }) { Text(stringResource(R.string.top_bar_reset_confirm)) }
                            },
                            dismissButton = {
                                TextButton(onClick = sessionViewModel::cancelResetSession) {
                                    Text(stringResource(R.string.top_bar_reset_cancel))
                                }
                            },
                        )
                    }
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

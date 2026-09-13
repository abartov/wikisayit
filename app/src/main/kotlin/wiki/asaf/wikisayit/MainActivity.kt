package wiki.asaf.wikisayit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import wiki.asaf.wikisayit.ui.navigation.WikiSayItNavHost
import wiki.asaf.wikisayit.ui.navigation.WikiSayItRoute
import wiki.asaf.wikisayit.ui.scaffold.WikiSayItAppScaffold
import wiki.asaf.wikisayit.ui.theme.WikiSayItTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WikiSayItTheme {
                val navController = rememberNavController()
                WikiSayItAppScaffold(
                    onSettingsClick = { navController.navigate(WikiSayItRoute.Settings) },
                    onStatsClick = { navController.navigate(WikiSayItRoute.Stats) },
                    onAboutClick = { navController.navigate(WikiSayItRoute.About) },
                ) { contentModifier ->
                    WikiSayItNavHost(navController = navController, modifier = contentModifier)
                }
            }
        }
    }
}

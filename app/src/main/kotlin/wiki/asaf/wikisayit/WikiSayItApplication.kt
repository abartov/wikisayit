package wiki.asaf.wikisayit

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import wiki.asaf.wikisayit.data.language.WikimediaLanguageRepository
import javax.inject.Inject

@HiltAndroidApp
class WikiSayItApplication : Application() {
    @Inject lateinit var wikimediaLanguages: WikimediaLanguageRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch { wikimediaLanguages.refresh() }
    }
}

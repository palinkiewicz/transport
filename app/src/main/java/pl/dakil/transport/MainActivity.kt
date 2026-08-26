package pl.dakil.transport

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import pl.dakil.transport.data.prefs.SettingsRepository
import pl.dakil.transport.ui.navigation.AppNavHost
import pl.dakil.transport.ui.navigation.parseGeoUri
import pl.dakil.transport.ui.search.SearchStateHolder
import pl.dakil.transport.ui.theme.TransportTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var searchStateHolder: SearchStateHolder

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleGeoIntent(intent)

        // Read straight from the repository rather than through SettingsViewModel: that one seeds
        // its state asynchronously, so the app would paint a frame of the wrong colours on every
        // start. DataStore has no synchronous read, hence the one blocking first emission — a
        // single small file, once, before anything is drawn.
        val initialSettings = runBlocking { settingsRepository.settings.first() }

        setContent {
            val settings by settingsRepository.settings.collectAsState(initial = initialSettings)
            TransportTheme(
                colorTheme = settings.colorTheme,
                darkThemeOption = settings.darkTheme,
                pureBlack = settings.pureBlack,
            ) {
                AppNavHost()
            }
        }
    }

    /** The activity is `singleTop`, so a second shared point arrives here, not in a new instance. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleGeoIntent(intent)
    }

    /**
     * A point shared by another app (typically a map's "open in…") is opened on the Map screen,
     * selected, with its panel showing. Anything unparseable is ignored and the app just opens
     * normally, rather than guessing at where the user meant to go.
     */
    private fun handleGeoIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        parseGeoUri(intent.data)?.let(searchStateHolder::setExternalLocation)
    }
}

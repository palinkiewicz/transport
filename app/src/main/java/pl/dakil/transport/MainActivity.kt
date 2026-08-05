package pl.dakil.transport

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import pl.dakil.transport.ui.navigation.AppNavHost
import pl.dakil.transport.ui.navigation.parseGeoUri
import pl.dakil.transport.ui.search.SearchStateHolder
import pl.dakil.transport.ui.theme.TransportTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var searchStateHolder: SearchStateHolder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleGeoIntent(intent)
        setContent {
            TransportTheme {
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
     * A point shared by another app (typically a map's "open in…") becomes the destination of a
     * new search. Anything unparseable is ignored and the app just opens normally, rather than
     * guessing at where the user meant to go.
     */
    private fun handleGeoIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        parseGeoUri(intent.data)?.let(searchStateHolder::setExternalDestination)
    }
}

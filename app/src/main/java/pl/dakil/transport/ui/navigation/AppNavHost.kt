package pl.dakil.transport.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import pl.dakil.transport.domain.model.PendingMapJourney
import pl.dakil.transport.domain.model.PendingMapTrip
import pl.dakil.transport.ui.components.LocalLineColorSettings
import pl.dakil.transport.ui.saved.SavedScreen
import pl.dakil.transport.ui.itinerary.ItineraryScreen
import pl.dakil.transport.ui.itinerary.SavedItineraryScreen
import pl.dakil.transport.ui.map.MapScreen
import pl.dakil.transport.ui.results.DeparturesScreen
import pl.dakil.transport.ui.results.ResultsScreen
import pl.dakil.transport.ui.results.ResultsViewModel
import pl.dakil.transport.ui.search.ConnectionsSearchScreen
import pl.dakil.transport.ui.search.DeparturesSearchScreen
import pl.dakil.transport.ui.search.LocationPickerScreen
import pl.dakil.transport.ui.settings.SettingsScreen

@Composable
fun AppNavHost(
    startDestinationViewModel: StartDestinationViewModel = hiltViewModel(),
    lineColorSettingsViewModel: LineColorSettingsViewModel = hiltViewModel(),
    showOnMapViewModel: ShowOnMapViewModel = hiltViewModel(),
) {
    val startTab by startDestinationViewModel.startTab.collectAsStateWithLifecycle()
    // One frame of nothing while the setting is read, rather than starting on the map and
    // yanking the user to another tab a moment later.
    val startRoute = startTab?.route() ?: return

    val navController = rememberNavController()

    // A point shared by another app: the Map screen shows and selects it (through the same
    // pending-location signal a MAP-target pick uses), so opening that tab is the whole handling
    // here. Waits for the nav host to exist, hence living here rather than in the activity that
    // parsed the intent.
    val pendingMapRequest by startDestinationViewModel.pendingMapRequest.collectAsStateWithLifecycle()
    LaunchedEffect(pendingMapRequest) {
        if (pendingMapRequest) {
            startDestinationViewModel.consumeMapRequest()
            navController.navigateToTab(MapRoute)
        }
    }

    /**
     * Opening a line shows it on the map: the run is handed over and a map is pushed to follow it.
     *
     * A plain push, deliberately not [navigateToTab]: the map is normally already on this stack a
     * few entries down (map → stop → board), and switching to it there would mean popping the
     * screens the user came through, so back could no longer retrace them — and going round the
     * loop again (a stop on the shown route → its board → its run → the map again) would keep
     * throwing that history away. Pushing instead means back walks the whole way out, one screen
     * at a time.
     */
    val showTripOnMap = { trip: PendingMapTrip ->
        showOnMapViewModel.showTrip(trip)
        navController.navigate(ShownOnMapRoute)
    }

    /** An itinerary is shown on a pushed map the same way, and closing its pane pops back here. */
    val showJourneyOnMap = { journey: PendingMapJourney ->
        showOnMapViewModel.showJourney(journey)
        navController.navigate(ShownOnMapRoute)
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val showBottomBar = backStackEntry?.destination?.let(::isBottomBarDestination) ?: true

    val lineColors by lineColorSettingsViewModel.lineColors.collectAsStateWithLifecycle()

    // Provided here rather than in the activity: everything that draws a line badge lives under
    // this host, and the value has to stay live so a colour change lands on an already-open screen.
    CompositionLocalProvider(LocalLineColorSettings provides lineColors) {
        Scaffold(
            bottomBar = { if (showBottomBar) TransportBottomBar(navController) },
            // Each destination has its own Scaffold (and usually a TopAppBar) that already claims
            // system bar insets; letting this outer Scaffold also claim them double-pads every screen.
            contentWindowInsets = WindowInsets(0),
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = startRoute,
                modifier = Modifier.padding(innerPadding),
            ) {
                // The tab's map and a pushed one are the same screen behind two destinations, so
                // they are declared from one lambda rather than twice over.
                val mapScreen: @Composable () -> Unit = {
                    MapScreen(
                        onOpenTimetable = { route -> navController.navigate(route) },
                        onNavigateToConnections = { navController.navigateToTab(ConnectionsRoute) },
                        onOpenLocationSearch = {
                            navController.navigate(LocationPickerRoute(PickerTarget.MAP))
                        },
                        onOpenTrip = showTripOnMap,
                        onCloseJourney = { navController.popBackStack() },
                    )
                }
                composable<MapRoute> { mapScreen() }
                composable<ShownOnMapRoute> { mapScreen() }
                composable<ConnectionsRoute> {
                    ConnectionsSearchScreen(
                        onSearch = { route -> navController.navigate(route) },
                        onPickFrom = { navController.navigate(LocationPickerRoute(PickerTarget.FROM)) },
                        onPickTo = { navController.navigate(LocationPickerRoute(PickerTarget.TO)) },
                        onPickVia = { index ->
                            navController.navigate(LocationPickerRoute(PickerTarget.VIA, index))
                        },
                    )
                }
                composable<DeparturesRoute> {
                    DeparturesSearchScreen(
                        onSearch = { route -> navController.navigate(route) },
                        onPickStop = { navController.navigate(LocationPickerRoute(PickerTarget.STOP)) },
                    )
                }
                composable<LocationPickerRoute> {
                    LocationPickerScreen(onBack = { navController.popBackStack() })
                }
                composable<SavedRoute> {
                    SavedScreen(
                        onOpenConnectionsSearch = { navController.navigateToTab(ConnectionsRoute) },
                        onOpenConnection = { route -> navController.navigate(route) },
                        onOpenTrip = showTripOnMap,
                        onOpenItinerary = { route -> navController.navigate(route) },
                    )
                }
                composable<SettingsRoute> {
                    SettingsScreen()
                }
                navigation<ResultsGraph>(startDestination = ResultsRoute::class) {
                    composable<ResultsRoute> { entry ->
                        val parentEntry = remember(entry) { navController.getBackStackEntry<ResultsGraph>() }
                        val resultsViewModel: ResultsViewModel = hiltViewModel(parentEntry)
                        ResultsScreen(
                            viewModel = resultsViewModel,
                            onBack = { navController.popBackStack() },
                            onJourneySelected = { index -> navController.navigate(ItineraryRoute(index)) },
                        )
                    }
                    composable<ItineraryRoute> { entry ->
                        val parentEntry = remember(entry) { navController.getBackStackEntry<ResultsGraph>() }
                        val resultsViewModel: ResultsViewModel = hiltViewModel(parentEntry)
                        val route: ItineraryRoute = entry.toRoute()
                        ItineraryScreen(
                            journey = resultsViewModel.journeyAt(route.index),
                            fromName = resultsViewModel.fromName,
                            toName = resultsViewModel.toName,
                            onBack = { navController.popBackStack() },
                            onOpenTrip = showTripOnMap,
                            onShowOnMap = showJourneyOnMap,
                            endpoints = resultsViewModel.fromPlace to resultsViewModel.toPlace,
                        )
                    }
                }
                composable<SavedItineraryRoute> {
                    SavedItineraryScreen(
                        onBack = { navController.popBackStack() },
                        onOpenTrip = showTripOnMap,
                        onShowOnMap = showJourneyOnMap,
                    )
                }
                composable<DepartureBoardRoute> {
                    DeparturesScreen(
                        onBack = { navController.popBackStack() },
                        onDepartureSelected = showTripOnMap,
                    )
                }
            }
        }
    }
}

/**
 * Switches to a bottom-bar tab, which is also what the bar itself calls: everything above the graph's
 * start destination is popped and the tab is landed on at its own root. `launchSingleTop` is what
 * keeps one entry per tab, so a tab can never end up with two live ViewModels racing for the
 * locations they share.
 *
 * Deliberately **without** `saveState`/`restoreState`. They look like the way to keep a tab's pushed
 * screens across a switch, and they file the popped entries under the destination being navigated
 * *to* — which is the graph's start destination, i.e. whichever tab the user starts on. Every switch
 * therefore saved a foreign tab's screens under that tab's id, and the next tap on it restored them:
 * tapping Map landed on a departure board, or on Connections, and a Map tap from a map pushed to
 * show a run or an itinerary put that very map straight back and so appeared to do nothing at all.
 * A tab tap is a request to be *at* that tab, so this lands there and the stack it left goes.
 */
internal fun NavHostController.navigateToTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id)
        launchSingleTop = true
    }
}

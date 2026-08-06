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
import pl.dakil.transport.ui.components.LocalLineColorSettings
import pl.dakil.transport.ui.favourites.FavouritesScreen
import pl.dakil.transport.ui.itinerary.ItineraryScreen
import pl.dakil.transport.ui.map.MapScreen
import pl.dakil.transport.ui.results.DeparturesScreen
import pl.dakil.transport.ui.results.ResultsScreen
import pl.dakil.transport.ui.results.ResultsViewModel
import pl.dakil.transport.ui.search.ConnectionsSearchScreen
import pl.dakil.transport.ui.search.DeparturesSearchScreen
import pl.dakil.transport.ui.search.LocationPickerScreen
import pl.dakil.transport.ui.settings.SettingsScreen
import pl.dakil.transport.ui.trip.TripScreen

@Composable
fun AppNavHost(
    startDestinationViewModel: StartDestinationViewModel = hiltViewModel(),
    lineColorSettingsViewModel: LineColorSettingsViewModel = hiltViewModel(),
) {
    val startTab by startDestinationViewModel.startTab.collectAsStateWithLifecycle()
    // One frame of nothing while the setting is read, rather than starting on the map and
    // yanking the user to another tab a moment later.
    val startRoute = startTab?.route() ?: return

    val navController = rememberNavController()

    // A destination shared by another app: the search form is where it is acted on, so opening
    // that tab is the whole handling. Waits for the nav host to exist, hence living here rather
    // than in the activity that parsed the intent.
    val pendingRouteRequest by startDestinationViewModel.pendingRouteRequest.collectAsStateWithLifecycle()
    LaunchedEffect(pendingRouteRequest) {
        if (pendingRouteRequest) {
            startDestinationViewModel.consumeRouteRequest()
            navController.navigateToTab(ConnectionsRoute)
        }
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
                composable<MapRoute> {
                    MapScreen(
                        onOpenTimetable = { route -> navController.navigate(route) },
                        onNavigateToConnections = { navController.navigateToTab(ConnectionsRoute) },
                        onOpenLocationSearch = {
                            navController.navigate(LocationPickerRoute(PickerTarget.MAP))
                        },
                    )
                }
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
                composable<FavouritesRoute> {
                    FavouritesScreen(
                        onOpenConnectionsSearch = { navController.navigateToTab(ConnectionsRoute) },
                        onOpenConnection = { route -> navController.navigate(route) },
                        onOpenTrip = { route -> navController.navigate(route) },
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
                            onOpenTrip = { route -> navController.navigate(route) },
                        )
                    }
                }
                composable<DepartureBoardRoute> {
                    DeparturesScreen(
                        onBack = { navController.popBackStack() },
                        onDepartureSelected = { route -> navController.navigate(route) },
                    )
                }
                composable<TripRoute> {
                    TripScreen(
                        onBack = { navController.popBackStack() },
                        onOpenDepartures = { route -> navController.navigate(route) },
                    )
                }
            }
        }
    }
}

/**
 * Switches to a bottom-bar tab the same way the bar itself does, so the tab's saved state (and
 * with it its already-created ViewModel) is restored rather than a second copy being stacked
 * on top — two live copies of a search ViewModel would race for the shared picked locations.
 */
private fun NavHostController.navigateToTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

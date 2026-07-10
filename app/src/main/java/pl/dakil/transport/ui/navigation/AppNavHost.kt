package pl.dakil.transport.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import pl.dakil.transport.ui.itinerary.ItineraryScreen
import pl.dakil.transport.ui.map.MapScreen
import pl.dakil.transport.ui.results.DeparturesScreen
import pl.dakil.transport.ui.results.ResultsScreen
import pl.dakil.transport.ui.results.ResultsViewModel
import pl.dakil.transport.ui.search.SearchScreen
import pl.dakil.transport.ui.trip.TripScreen

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val showBottomBar = backStackEntry?.destination?.let {
        it.hasRoute<MapRoute>() || it.hasRoute<SearchRoute>()
    } ?: true

    Scaffold(
        bottomBar = { if (showBottomBar) TransportBottomBar(navController) },
        // Each destination has its own Scaffold (and usually a TopAppBar) that already claims
        // system bar insets; letting this outer Scaffold also claim them double-pads every screen.
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = MapRoute,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<MapRoute> {
                MapScreen(
                    onOpenTimetable = { route -> navController.navigate(route) },
                    onOpenTrip = { route -> navController.navigate(route) },
                    onNavigateToSearch = {
                        navController.navigate(SearchRoute) {
                            popUpTo(MapRoute) { saveState = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable<SearchRoute> {
                SearchScreen(
                    onSearchConnections = { route -> navController.navigate(route) },
                    onSearchDepartures = { route -> navController.navigate(route) },
                )
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
                    )
                }
            }
            composable<DeparturesRoute> {
                DeparturesScreen(
                    onBack = { navController.popBackStack() },
                    onDepartureSelected = { route -> navController.navigate(route) },
                )
            }
            composable<TripRoute> {
                TripScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

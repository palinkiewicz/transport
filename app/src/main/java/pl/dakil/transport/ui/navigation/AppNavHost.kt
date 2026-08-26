package pl.dakil.transport.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
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
import pl.dakil.transport.ui.settings.SettingsSection
import pl.dakil.transport.ui.settings.SettingsSectionScreen

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
        // What the bar measures while it is on screen. The destinations are padded by this rather
        // than by the Scaffold's own inset: an [AnimatedVisibility] that only slides keeps its child
        // at full height for the whole exit, so the Scaffold would hold a bar's worth of bottom
        // padding right through a push and then drop it in one frame — leaving the arriving screen
        // short of a bar's height for the entire transition and snapping it to full size at the end.
        // Keyed off whether the bar *belongs* on the destination, the height changes the moment the
        // navigation starts and the bar simply slides away over a screen that is already full size.
        var barHeight by remember { mutableStateOf(0.dp) }
        val density = LocalDensity.current

        Scaffold(
            bottomBar = {
                // Slides out with the pushed screen that hid it rather than vanishing under it.
                AnimatedVisibility(
                    visible = showBottomBar,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it }),
                ) {
                    TransportBottomBar(
                        navController = navController,
                        modifier = Modifier.onSizeChanged {
                            barHeight = with(density) { it.height.toDp() }
                        },
                    )
                }
            },
            // Each destination has its own Scaffold (and usually a TopAppBar) that already claims
            // system bar insets; letting this outer Scaffold also claim them double-pads every screen.
            contentWindowInsets = WindowInsets(0),
        ) { _ ->
            NavHost(
                navController = navController,
                startDestination = startRoute,
                // Painted with the screen background so no window flashes through the fade-through,
                // where both screens are partially transparent at once.
                modifier = Modifier
                    .padding(bottom = if (showBottomBar) barHeight else 0.dp)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                // Switching between bottom-bar tabs plays Material fade-through (no direction);
                // everything pushed on top of a tab uses Material 3's shared-axis X instead.
                enterTransition = {
                    if (initialState.isTabRoot() && targetState.isTabRoot()) fadeThroughEnter()
                    else slideIntoContainer(SlideDirection.Start, tween(TRANSITION_DURATION_MS)) +
                        fadeIn(tween(TRANSITION_DURATION_MS))
                },
                exitTransition = {
                    if (initialState.isTabRoot() && targetState.isTabRoot()) fadeThroughExit()
                    else slideOutOfContainer(SlideDirection.Start, tween(TRANSITION_DURATION_MS)) +
                        fadeOut(tween(TRANSITION_DURATION_MS))
                },
                popEnterTransition = {
                    if (initialState.isTabRoot() && targetState.isTabRoot()) fadeThroughEnter()
                    else slideIntoContainer(SlideDirection.End, tween(TRANSITION_DURATION_MS)) +
                        fadeIn(tween(TRANSITION_DURATION_MS))
                },
                popExitTransition = {
                    if (initialState.isTabRoot() && targetState.isTabRoot()) fadeThroughExit()
                    else slideOutOfContainer(SlideDirection.End, tween(TRANSITION_DURATION_MS)) +
                        fadeOut(tween(TRANSITION_DURATION_MS))
                },
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
                // The index and its sections share one ViewModel through the graph entry, so a
                // change made in a section is what the index reads when it comes back — see
                // [SettingsGraph].
                navigation<SettingsGraph>(startDestination = SettingsRoute::class) {
                    composable<SettingsRoute> { entry ->
                        val parentEntry = remember(entry) { navController.getBackStackEntry<SettingsGraph>() }
                        SettingsScreen(
                            onOpenSection = { section ->
                                navController.navigate(SettingsSectionRoute(section.name))
                            },
                            viewModel = hiltViewModel(parentEntry),
                        )
                    }
                    composable<SettingsSectionRoute> { entry ->
                        val parentEntry = remember(entry) { navController.getBackStackEntry<SettingsGraph>() }
                        val route: SettingsSectionRoute = entry.toRoute()
                        SettingsSectionScreen(
                            // Guarded rather than `valueOf` outright: the section list has
                            // changed shape before, and an entry restored after process death
                            // across an in-place upgrade can name one that no longer exists.
                            section = runCatching { SettingsSection.valueOf(route.section) }
                                .getOrDefault(SettingsSection.GENERAL),
                            onBack = { navController.popBackStack() },
                            viewModel = hiltViewModel(parentEntry),
                        )
                    }
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

private const val TRANSITION_DURATION_MS = 500
private const val FADE_THROUGH_DURATION_MS = 220
private const val FADE_THROUGH_OUT_MS = 90

/**
 * Material fade-through: the outgoing screen fades out first, then the incoming one fades and
 * scales up into the gap. Used only between tabs, which have no spatial relationship to slide along.
 */
private fun fadeThroughEnter(): EnterTransition =
    fadeIn(tween(FADE_THROUGH_DURATION_MS, delayMillis = FADE_THROUGH_OUT_MS)) +
        scaleIn(
            initialScale = 0.92f,
            animationSpec = tween(FADE_THROUGH_DURATION_MS, delayMillis = FADE_THROUGH_OUT_MS),
        )

private fun fadeThroughExit(): ExitTransition = fadeOut(tween(FADE_THROUGH_OUT_MS))

package pl.dakil.transport.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DepartureBoard
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import pl.dakil.transport.R

private data class BottomBarDestination(
    val route: Any,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
)

private val bottomBarDestinations = listOf(
    BottomBarDestination(MapRoute, R.string.tab_map, Icons.Default.Map),
    BottomBarDestination(ConnectionsRoute, R.string.tab_connections, Icons.Default.Route),
    BottomBarDestination(DeparturesRoute, R.string.tab_departures, Icons.Default.DepartureBoard),
    BottomBarDestination(FavouritesRoute, R.string.tab_favourites, Icons.Default.Star),
    BottomBarDestination(SettingsRoute, R.string.tab_settings, Icons.Default.Settings),
)

/** True for the top-level destinations that show the bottom bar. */
fun isBottomBarDestination(destination: androidx.navigation.NavDestination): Boolean =
    bottomBarDestinations.any { destination.hasRoute(it.route::class) }

@Composable
fun TransportBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    NavigationBar {
        bottomBarDestinations.forEach { destination ->
            NavigationBarItem(
                selected = currentDestination?.hasRoute(destination.route::class) == true,
                onClick = {
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(destination.icon, contentDescription = stringResource(destination.labelRes))
                },
                label = { Text(stringResource(destination.labelRes)) },
            )
        }
    }
}

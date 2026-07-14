package pl.dakil.transport.ui.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.dakil.transport.domain.model.TransitLocation
import pl.dakil.transport.ui.components.FavoriteButton
import pl.dakil.transport.ui.components.LocationListItem
import pl.dakil.transport.ui.navigation.PickerTarget

/**
 * Full-screen location search (Google-Maps-style): a search field on top, and below it either
 * the geocoder suggestions for the query, or — while the query is empty — the current location
 * and the favourite places. Selection is returned via [SearchStateHolder]; see the ViewModel.
 */
@Composable
fun LocationPickerScreen(
    onBack: () -> Unit,
    viewModel: LocationPickerViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }

    fun pick(location: TransitLocation) {
        viewModel.select(location)
        onBack()
    }

    Scaffold(
        contentWindowInsets = WindowInsets.systemBars.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
        ),
        topBar = {},
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.weight(1f),
                ) {
                    TextField(
                        value = query,
                        onValueChange = viewModel::onQueryChange,
                        placeholder = {
                            Text(
                                when (viewModel.target) {
                                    PickerTarget.FROM -> "Where from?"
                                    PickerTarget.TO -> "Where to?"
                                    PickerTarget.MAP -> "Search stops & places"
                                },
                            )
                        },
                        singleLine = true,
                        trailingIcon = if (query.isNotEmpty()) {
                            {
                                IconButton(onClick = { viewModel.onQueryChange("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        } else {
                            null
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            errorIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )
                }
            }
            LaunchedEffect(Unit) { focusRequester.requestFocus() }

            LocationPickerList(
                query = query,
                items = items,
                currentLocation = viewModel.currentLocation,
                onPick = ::pick,
                onToggleFavorite = viewModel::toggleFavorite,
            )
        }
    }
}

@Composable
private fun LocationPickerList(
    query: String,
    items: List<PickerItem>,
    currentLocation: TransitLocation?,
    onPick: (TransitLocation) -> Unit,
    onToggleFavorite: (TransitLocation) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (query.isBlank()) {
            currentLocation?.let { current ->
                item(key = "current-location") {
                    ListItem(
                        headlineContent = { Text("Current location") },
                        leadingContent = {
                            Icon(
                                Icons.Default.MyLocation,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .clickable { onPick(current) }
                            .animateItem(),
                    )
                }
            }
            if (items.isNotEmpty()) {
                item(key = "favourites-header") {
                    Text(
                        text = "Favourites",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
                            .animateItem(),
                    )
                }
            }
        }

        items(
            count = items.size,
            key = { index -> items[index].location.favoriteKey },
        ) { index ->
            val item = items[index]
            LocationListItem(
                location = item.location,
                onClick = { onPick(item.location) },
                distanceMeters = item.distanceMeters,
                trailingContent = {
                    FavoriteButton(
                        isFavorite = item.isFavorite,
                        onToggle = { onToggleFavorite(item.location) },
                    )
                },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

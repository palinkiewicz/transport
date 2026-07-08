package pl.dakil.transport.ui.itinerary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import pl.dakil.transport.domain.model.Journey
import pl.dakil.transport.domain.model.JourneyLeg
import pl.dakil.transport.ui.components.ErrorBox
import pl.dakil.transport.ui.components.ModeChip

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItineraryScreen(journey: Journey?, fromName: String, toName: String, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Itinerary") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (journey == null) {
            ErrorBox("Itinerary not available", Modifier.padding(innerPadding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
            ) {
                items(journey.legs.size) { index ->
                    LegRow(
                        leg = journey.legs[index],
                        isLast = index == journey.legs.lastIndex,
                        fromNameOverride = if (index == 0) fromName else null,
                        toNameOverride = if (index == journey.legs.lastIndex) toName else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun LegRow(leg: JourneyLeg, isLast: Boolean, fromNameOverride: String? = null, toNameOverride: String? = null) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .width(24.dp)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(leg.mode.color),
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .weight(1f)
                        .background(leg.mode.color),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.padding(bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(leg.startTime.format(timeFormatter), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(fromNameOverride ?: leg.fromName, style = MaterialTheme.typography.bodyMedium)
                leg.fromTrack?.let { Text("Platform $it", style = MaterialTheme.typography.labelSmall) }
            }
            Spacer(Modifier.height(4.dp))
            if (leg.isTransit) {
                ModeChip(mode = leg.mode, label = leg.lineLabel, routeColorHex = leg.routeColor)
                leg.headsign?.let {
                    Text("towards $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
                if (leg.intermediateStopNames.isNotEmpty()) {
                    Text(
                        "${leg.intermediateStopNames.size} intermediate stops",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            } else {
                Text(
                    "${leg.mode.name.lowercase().replaceFirstChar { it.uppercase() }} · ${leg.duration / 60} min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(leg.endTime.format(timeFormatter), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(toNameOverride ?: leg.toName, style = MaterialTheme.typography.bodyMedium)
                leg.toTrack?.let { Text("Platform $it", style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

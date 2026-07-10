package pl.dakil.transport.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The app's single control for starring/unstarring anything (locations, connections, lines):
 * an [IconButton] whose star pops in with a springy scale when toggled.
 */
@Composable
fun FavoriteButton(
    isFavorite: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onToggle, modifier = modifier) {
        AnimatedContent(
            targetState = isFavorite,
            transitionSpec = {
                (
                    scaleIn(
                        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
                        initialScale = 0.4f,
                    ) + fadeIn()
                    ).togetherWith(scaleOut(targetScale = 0.4f) + fadeOut())
            },
            label = "favorite-star",
        ) { starred ->
            Icon(
                imageVector = if (starred) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = if (starred) "Remove from favourites" else "Add to favourites",
                tint = if (starred) MaterialTheme.colorScheme.primary else LocalContentColor.current,
            )
        }
    }
}

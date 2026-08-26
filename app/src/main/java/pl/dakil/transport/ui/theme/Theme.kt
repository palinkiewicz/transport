package pl.dakil.transport.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import pl.dakil.transport.domain.model.AppColorTheme
import pl.dakil.transport.domain.model.DarkThemeOption

// ---- The bundled palettes -----------------------------------------------------------------------
//
// Each scheme names only the sixteen roles that carry its identity — the three accent families plus
// background and surface. The neutral family (surface variants, containers, outlines) is derived
// from those by `withNeutralSurfaces` below rather than written out twelve more times, because it is
// a mechanical tonal ramp off the background and hand-copying it is how the ramps drift apart.

/** Vivid violet with a teal third accent: the app's own identity, and the default. */
private val TransportLight = lightColorScheme(
    primary = Color(0xFF7126D9),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEBDDFF),
    onPrimaryContainer = Color(0xFF24005A),
    secondary = Color(0xFF635B70),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE9DEF8),
    onSecondaryContainer = Color(0xFF1F182B),
    tertiary = Color(0xFF006A6A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF9CF1F0),
    onTertiaryContainer = Color(0xFF002020),
    background = Color(0xFFFDF7FF),
    onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFDF7FF),
    onSurface = Color(0xFF1D1B20),
)

private val TransportDark = darkColorScheme(
    primary = Color(0xFFCFBCFF),
    onPrimary = Color(0xFF3F0092),
    primaryContainer = Color(0xFF590FC0),
    onPrimaryContainer = Color(0xFFEBDDFF),
    secondary = Color(0xFFCDC2DB),
    onSecondary = Color(0xFF342D40),
    secondaryContainer = Color(0xFF4B4358),
    onSecondaryContainer = Color(0xFFE9DEF8),
    tertiary = Color(0xFF80D5D4),
    onTertiary = Color(0xFF003736),
    tertiaryContainer = Color(0xFF004F4F),
    onTertiaryContainer = Color(0xFF9CF1F0),
    background = Color(0xFF15111B),
    onBackground = Color(0xFFE6E0E9),
    surface = Color(0xFF15111B),
    onSurface = Color(0xFFE6E0E9),
)

private val OceanLight = lightColorScheme(
    primary = Color(0xFF0061A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD1E4FF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF535F70),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD7E3F7),
    onSecondaryContainer = Color(0xFF101C2B),
    tertiary = Color(0xFF6B5778),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF2DAFF),
    onTertiaryContainer = Color(0xFF251431),
    background = Color(0xFFF8F9FF),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF8F9FF),
    onSurface = Color(0xFF191C20),
)

private val OceanDark = darkColorScheme(
    primary = Color(0xFF9ECAFF),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF00497D),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFFBBC7DB),
    onSecondary = Color(0xFF253140),
    secondaryContainer = Color(0xFF3B4858),
    onSecondaryContainer = Color(0xFFD7E3F7),
    tertiary = Color(0xFFD6BEE4),
    onTertiary = Color(0xFF3B2948),
    tertiaryContainer = Color(0xFF523F5F),
    onTertiaryContainer = Color(0xFFF2DAFF),
    background = Color(0xFF191C20),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF191C20),
    onSurface = Color(0xFFE2E2E9),
)

private val LavenderLight = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF31111D),
    background = Color(0xFFFEF7FF),
    onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFEF7FF),
    onSurface = Color(0xFF1D1B20),
)

private val LavenderDark = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E0E9),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE6E0E9),
)

private val SunsetLight = lightColorScheme(
    primary = Color(0xFF8C5000),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDCBE),
    onPrimaryContainer = Color(0xFF2D1600),
    secondary = Color(0xFF745B45),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDCBE),
    onSecondaryContainer = Color(0xFF2A1808),
    tertiary = Color(0xFF5A623A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDEE7B3),
    onTertiaryContainer = Color(0xFF191E00),
    background = Color(0xFFFFF8F4),
    onBackground = Color(0xFF221A11),
    surface = Color(0xFFFFF8F4),
    onSurface = Color(0xFF221A11),
)

private val SunsetDark = darkColorScheme(
    primary = Color(0xFFFFB877),
    onPrimary = Color(0xFF4B2800),
    primaryContainer = Color(0xFF6B3C00),
    onPrimaryContainer = Color(0xFFFFDCBE),
    secondary = Color(0xFFE3C1A7),
    onSecondary = Color(0xFF422C1A),
    secondaryContainer = Color(0xFF5B422F),
    onSecondaryContainer = Color(0xFFFFDCBE),
    tertiary = Color(0xFFC2CB99),
    onTertiary = Color(0xFF2C3410),
    tertiaryContainer = Color(0xFF424B24),
    onTertiaryContainer = Color(0xFFDEE7B3),
    background = Color(0xFF19120B),
    onBackground = Color(0xFFEFE0D4),
    surface = Color(0xFF19120B),
    onSurface = Color(0xFFEFE0D4),
)

private val RoseLight = lightColorScheme(
    primary = Color(0xFF984061),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9E2),
    onPrimaryContainer = Color(0xFF3E001D),
    secondary = Color(0xFF74565F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD9E2),
    onSecondaryContainer = Color(0xFF2B151C),
    tertiary = Color(0xFF7C5635),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCC1),
    onTertiaryContainer = Color(0xFF2E1500),
    background = Color(0xFFFFF8F8),
    onBackground = Color(0xFF22191C),
    surface = Color(0xFFFFF8F8),
    onSurface = Color(0xFF22191C),
)

private val RoseDark = darkColorScheme(
    primary = Color(0xFFFFB1C8),
    onPrimary = Color(0xFF5E1133),
    primaryContainer = Color(0xFF7B2949),
    onPrimaryContainer = Color(0xFFFFD9E2),
    secondary = Color(0xFFE2BDC6),
    onSecondary = Color(0xFF422931),
    secondaryContainer = Color(0xFF5A3F47),
    onSecondaryContainer = Color(0xFFFFD9E2),
    tertiary = Color(0xFFEFBD94),
    onTertiary = Color(0xFF48290B),
    tertiaryContainer = Color(0xFF613F20),
    onTertiaryContainer = Color(0xFFFFDCC1),
    background = Color(0xFF191114),
    onBackground = Color(0xFFEFDFE2),
    surface = Color(0xFF191114),
    onSurface = Color(0xFFEFDFE2),
)

private val TealLight = lightColorScheme(
    primary = Color(0xFF006A60),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF74F8E5),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF4A635F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E2),
    onSecondaryContainer = Color(0xFF05201C),
    tertiary = Color(0xFF456179),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCCE5FF),
    onTertiaryContainer = Color(0xFF001E31),
    background = Color(0xFFF4FBF8),
    onBackground = Color(0xFF161D1B),
    surface = Color(0xFFF4FBF8),
    onSurface = Color(0xFF161D1B),
)

private val TealDark = darkColorScheme(
    primary = Color(0xFF53DBC9),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005048),
    onPrimaryContainer = Color(0xFF74F8E5),
    secondary = Color(0xFFB1CCC6),
    onSecondary = Color(0xFF1C3531),
    secondaryContainer = Color(0xFF334B47),
    onSecondaryContainer = Color(0xFFCCE8E2),
    tertiary = Color(0xFFADCAE6),
    onTertiary = Color(0xFF153349),
    tertiaryContainer = Color(0xFF2D4961),
    onTertiaryContainer = Color(0xFFCCE5FF),
    background = Color(0xFF0E1513),
    onBackground = Color(0xFFDDE4E1),
    surface = Color(0xFF0E1513),
    onSurface = Color(0xFFDDE4E1),
)

/**
 * Fills the neutral family from the scheme's own background.
 *
 * `lightColorScheme()` and `darkColorScheme()` default every unnamed role to the Material baseline,
 * which is faintly purple. That is invisible next to the violet default and obvious next to the
 * teal one — a purple bottom bar under a green app. Rather than write six more surface tones per
 * scheme by hand, they are lerped between background and onBackground at Material's own tonal
 * steps, which is what those tones are.
 *
 * Never applied to a dynamic scheme: the platform already hands over a complete, coherent set.
 */
private fun ColorScheme.withNeutralSurfaces(dark: Boolean): ColorScheme {
    // Toward the text colour in light mode, toward it in dark mode too — the ramp runs the same
    // direction either way, because in dark mode "onBackground" is the lighter of the pair.
    fun tone(fraction: Float) = lerp(background, onBackground, fraction)

    return copy(
        surfaceVariant = tone(if (dark) 0.14f else 0.10f),
        onSurfaceVariant = tone(0.70f),
        outline = tone(0.50f),
        outlineVariant = tone(0.22f),
        surfaceDim = if (dark) background else tone(0.09f),
        surfaceBright = if (dark) tone(0.14f) else background,
        surfaceContainerLowest = if (dark) tone(0.03f) else background,
        surfaceContainerLow = tone(0.03f),
        surfaceContainer = tone(0.06f),
        surfaceContainerHigh = tone(0.09f),
        surfaceContainerHighest = tone(0.12f),
        surfaceTint = primary,
        inverseSurface = onBackground,
        inverseOnSurface = background,
    )
}

/**
 * True black in dark mode, for OLED screens where an unlit pixel costs nothing.
 *
 * The containers stay just off black: flattening them too would erase every elevation cue the
 * Material components rely on to separate a sheet from the page behind it — and this app leans on
 * that heavily, since the map's panels are all sheets over a full-bleed map.
 */
private fun ColorScheme.toPureBlack() = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0D0D0D),
    surfaceContainer = Color(0xFF131313),
    surfaceContainerHigh = Color(0xFF1B1B1B),
    surfaceContainerHighest = Color(0xFF232323),
)

/**
 * Resolves a [ColorScheme] for a colour theme and a dark flag.
 *
 * Public because the appearance picker paints its swatches from it — a swatch that guessed at the
 * colours it previews would be a second source of truth for what a theme looks like.
 */
@Composable
fun colorSchemeFor(colorTheme: AppColorTheme, darkTheme: Boolean): ColorScheme {
    // Read unconditionally: a composable call present in only one branch of this `when` changes the
    // slot table shape when `colorTheme` toggles into or out of DYNAMIC, which loses unrelated
    // `rememberSaveable` state further up the tree on that transition. Reading it here every time
    // keeps the call structure identical across all branches.
    val context = LocalContext.current
    return when (colorTheme) {
        AppColorTheme.DYNAMIC ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                // Fall back to the app's own palette rather than hiding the option, because a saved
                // choice should still open something.
                bundled(AppColorTheme.TRANSPORT, darkTheme)
            }

        else -> bundled(colorTheme, darkTheme)
    }
}

private fun bundled(colorTheme: AppColorTheme, darkTheme: Boolean): ColorScheme {
    val scheme = when (colorTheme) {
        AppColorTheme.TRANSPORT, AppColorTheme.DYNAMIC ->
            if (darkTheme) TransportDark else TransportLight

        AppColorTheme.OCEAN -> if (darkTheme) OceanDark else OceanLight
        AppColorTheme.LAVENDER -> if (darkTheme) LavenderDark else LavenderLight
        AppColorTheme.SUNSET -> if (darkTheme) SunsetDark else SunsetLight
        AppColorTheme.ROSE -> if (darkTheme) RoseDark else RoseLight
        AppColorTheme.TEAL -> if (darkTheme) TealDark else TealLight
    }
    return scheme.withNeutralSurfaces(darkTheme)
}

/** Whether this option means "paint dark" right now. */
@Composable
fun DarkThemeOption.resolveDark(): Boolean = when (this) {
    DarkThemeOption.SYSTEM -> isSystemInDarkTheme()
    DarkThemeOption.LIGHT -> false
    DarkThemeOption.DARK -> true
}

/**
 * The app theme.
 *
 * Dynamic colour is offered rather than assumed: it was unconditional here before there was a
 * picker to put it in, and it is not available at all below Android 12.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TransportTheme(
    colorTheme: AppColorTheme = AppColorTheme.TRANSPORT,
    darkThemeOption: DarkThemeOption = DarkThemeOption.SYSTEM,
    pureBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = darkThemeOption.resolveDark()

    val colorScheme = colorSchemeFor(colorTheme, darkTheme)
        .let { if (darkTheme && pureBlack) it.toPureBlack() else it }

    // The app's own dark flag can now disagree with the system's, and the system bars are painted by
    // the platform from the system setting. Without this the status bar icons come out white on a
    // light app, or black on a dark one, for anyone who overrides the system.
    //
    // Keyed on `darkTheme` rather than run as a `SideEffect`, because the Map screen sets the status
    // bar icons from the *basemap* for as long as it is open (see MapStatusBarIcons) and restores
    // the previous value on the way out. An effect firing on every recomposition of the theme root
    // would stomp that while the map is up.
    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(view, darkTheme) {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            onDispose {}
        }
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = Typography,
        content = content,
    )
}

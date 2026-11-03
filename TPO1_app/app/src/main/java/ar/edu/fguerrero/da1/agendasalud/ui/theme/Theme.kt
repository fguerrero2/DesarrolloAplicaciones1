package ar.edu.fguerrero.da1.agendasalud.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Azul80,
    primaryContainer = AzulContenedor30,
    onPrimaryContainer = AlAzulContenedor90,
    secondary = Gris80,
    tertiary = Ambar80,
    tertiaryContainer = AmbarContenedor30,
    onTertiaryContainer = AlAmbarContenedor90,
    background = Fondo10,
    surface = Fondo10,
    surfaceVariant = Superficie20,
)

private val LightColorScheme = lightColorScheme(
    primary = Azul40,
    primaryContainer = AzulContenedor90,
    onPrimaryContainer = AlAzulContenedor10,
    secondary = Gris40,
    secondaryContainer = GrisContenedor90,
    tertiary = Ambar40,
    tertiaryContainer = AmbarContenedor90,
    onTertiaryContainer = AlAmbarContenedor10,
    background = Fondo99,
    surface = Fondo99,
    surfaceVariant = Superficie95,
)

/**
 * Tema de la app: paleta propia en claro y oscuro (Color.kt) y la tipografía estándar de Material 3.
 * Sin colores dinámicos: la app se ve igual en todos los celulares.
 */
@Composable
fun AgendaSaludTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        content = content,
    )
}

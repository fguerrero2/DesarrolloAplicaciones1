package ar.edu.fguerrero.da1.agendasalud.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

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

@Composable
fun AgendaSaludTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Desactivado: la app usa su propia paleta para verse igual en todos los celulares
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
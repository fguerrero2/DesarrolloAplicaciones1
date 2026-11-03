package ar.edu.fguerrero.da1.agendasalud.presentation

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Logotipo Bicolor Humanista para el interior de la app.
 * "Agenda" (Primary / Azul) + "Salud" (Tertiary / Ámbar)
 */
@Composable
fun AgendaSaludLogo(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 28.sp
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Agenda",
            fontWeight = FontWeight.Medium,
            fontSize = fontSize,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Salud",
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
            color = MaterialTheme.colorScheme.tertiary
        )
    }
}

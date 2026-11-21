package com.example.recordatorios.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.recordatorios.ui.theme.RecordatoriosTheme

/**
 * Componente reutilizable para comunicar CARGA, VACÍO o ERROR.
 * Siempre muestra texto (no depende solo del color) y anuncia los cambios
 * a los lectores de pantalla mediante liveRegion.
 */
@Composable
fun EstadoDePantalla(
    titulo: String,
    mensaje: String,
    modifier: Modifier = Modifier,
    icono: String? = null,
    cargando: Boolean = false,
    esError: Boolean = false,
    textoAccion: String? = null,
    onAccion: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (cargando) CircularProgressIndicator()
        if (icono != null) Text(icono, style = MaterialTheme.typography.displaySmall)
        Text(
            text = titulo,
            style = MaterialTheme.typography.titleLarge,
            color = if (esError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Text(
            text = mensaje,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        if (textoAccion != null && onAccion != null) {
            Button(onClick = onAccion) { Text(textoAccion) }
        }
    }
}

@Composable
fun IndicadorDeCarga(
    modifier: Modifier = Modifier,
    mensaje: String = "Esto puede tardar unos segundos"
) = EstadoDePantalla(
    titulo = "Cargando…",
    mensaje = mensaje,
    cargando = true,
    modifier = modifier
)

@Composable
fun EstadoVacio(
    mensaje: String,
    textoAccion: String,
    onAccion: () -> Unit,
    modifier: Modifier = Modifier
) = EstadoDePantalla(
    titulo = "No hay recordatorios",
    mensaje = mensaje,
    icono = "📭",
    textoAccion = textoAccion,
    onAccion = onAccion,
    modifier = modifier
)

@Composable
fun EstadoDeError(
    mensaje: String,
    onReintentar: () -> Unit,
    modifier: Modifier = Modifier,
    titulo: String = "Algo salió mal"
) = EstadoDePantalla(
    titulo = "⚠ $titulo",
    mensaje = mensaje,
    esError = true,
    textoAccion = "Reintentar",
    onAccion = onReintentar,
    modifier = modifier
)

@Preview(showBackground = true)
@Composable
private fun EstadoDeErrorPreview() {
    RecordatoriosTheme {
        EstadoDeError(mensaje = "No se pudieron cargar los recordatorios.", onReintentar = {})
    }
}

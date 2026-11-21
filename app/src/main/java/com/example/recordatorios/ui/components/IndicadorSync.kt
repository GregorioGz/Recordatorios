package com.example.recordatorios.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.recordatorios.model.EstadoSincronizacion
import com.example.recordatorios.model.EstadoSync
import com.example.recordatorios.ui.theme.RecordatoriosTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Presentación de cada estado de sincronización: ícono + texto (nunca solo color). */
private data class AparienciaSync(val icono: ImageVector, val texto: String, val esError: Boolean)

private fun EstadoSync.apariencia(): AparienciaSync = when (this) {
    EstadoSync.SINCRONIZADO -> AparienciaSync(Icons.Filled.CheckCircle, "Sincronizado", false)
    EstadoSync.PENDIENTE_CREAR,
    EstadoSync.PENDIENTE_ACTUALIZAR -> AparienciaSync(Icons.Filled.Refresh, "Pendiente de sincronizar", false)
    EstadoSync.PENDIENTE_ELIMINAR -> AparienciaSync(Icons.Filled.Refresh, "Eliminación pendiente", false)
    EstadoSync.ERROR -> AparienciaSync(Icons.Filled.Warning, "Error: reintentar", true)
}

/** Indicador compacto del estado de sincronización de UN recordatorio. */
@Composable
fun IndicadorSync(
    estadoSync: EstadoSync,
    modifier: Modifier = Modifier
) {
    val apariencia = estadoSync.apariencia()
    val color = when {
        apariencia.esError -> MaterialTheme.colorScheme.error
        estadoSync == EstadoSync.SINCRONIZADO -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "Sincronización: ${apariencia.texto}"
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(apariencia.icono, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(apariencia.texto, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/**
 * Indicador GLOBAL (debajo del TopAppBar): sin conexión, sincronizando,
 * N pendientes o último error, con acción para sincronizar o reintentar.
 */
@Composable
fun BannerSincronizacion(
    estado: EstadoSincronizacion,
    onSincronizarAhora: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pendientes = when (estado.pendientes) {
        0 -> "Todo sincronizado"
        1 -> "1 cambio pendiente de sincronizar"
        else -> "${estado.pendientes} cambios pendientes de sincronizar"
    }
    val (texto, esError, accion) = when {
        !estado.hayConexion -> Triple(
            "Sin conexión · $pendientes. Se enviarán al recuperar la red.", false, null
        )
        estado.sincronizando -> Triple("Sincronizando…", false, null)
        estado.ultimoError != null -> Triple("⚠ ${estado.ultimoError}", true, "Reintentar")
        estado.pendientes > 0 -> Triple(pendientes, false, "Sincronizar")
        else -> Triple(pendientes, false, null)
    }
    val fondo: Color = if (esError) MaterialTheme.colorScheme.errorContainer
    else MaterialTheme.colorScheme.secondaryContainer
    val contenido: Color = if (esError) MaterialTheme.colorScheme.onErrorContainer
    else MaterialTheme.colorScheme.onSecondaryContainer

    Surface(
        color = fondo,
        contentColor = contenido,
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (estado.sincronizando) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = contenido)
            }
            Text(texto, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            if (accion != null) {
                TextButton(onClick = onSincronizarAhora) { Text(accion) }
            }
        }
    }
}

/** Fecha legible para la interfaz (dd/MM/yyyy HH:mm). */
fun formatearFecha(epochMs: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(epochMs))

@Preview(showBackground = true)
@Composable
private fun BannerSincronizacionPreview() {
    RecordatoriosTheme {
        BannerSincronizacion(
            estado = EstadoSincronizacion(pendientes = 2, ultimoError = "El servidor tuvo un problema."),
            onSincronizarAhora = {}
        )
    }
}

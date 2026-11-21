package com.example.recordatorios.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.recordatorios.data.DatosSimulados
import com.example.recordatorios.model.EstadoRecordatorio
import com.example.recordatorios.model.Prioridad
import com.example.recordatorios.model.Recordatorio
import com.example.recordatorios.ui.theme.RecordatoriosTheme

/**
 * Componente reutilizable que representa UN recordatorio.
 * No conoce la navegación: solo informa el toque mediante la lambda onClick.
 */
@Composable
fun RecordatorioCard(
    recordatorio: Recordatorio,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorCategoria = ColoresRecordatorio.color(recordatorio.categoria)
    val completado = recordatorio.estado == EstadoRecordatorio.Completado

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(colorCategoria)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = recordatorio.titulo,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (completado) TextDecoration.LineThrough else null
                )
                Text(
                    text = "${ColoresRecordatorio.etiqueta(recordatorio.categoria)} ${recordatorio.categoria.nombre}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorCategoria
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EtiquetaPrioridad(recordatorio.prioridad)
                    AssistChip(
                        onClick = onClick,
                        label = { Text(recordatorio.estado.etiqueta) },
                        modifier = Modifier.semantics {
                            contentDescription = "Estado: ${recordatorio.estado.etiqueta}"
                        }
                    )
                }
            }
        }
    }
}

/** Chip de prioridad: punto de color + texto (el color nunca va solo). */
@Composable
fun EtiquetaPrioridad(
    prioridad: Prioridad,
    modifier: Modifier = Modifier
) {
    AssistChip(
        onClick = {},
        label = { Text("Prioridad ${prioridad.etiqueta}") },
        leadingIcon = {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(ColoresRecordatorio.color(prioridad))
            )
        },
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
private fun RecordatorioCardPreview() {
    RecordatoriosTheme {
        RecordatorioCard(recordatorio = DatosSimulados.recordatorios.first(), onClick = {})
    }
}

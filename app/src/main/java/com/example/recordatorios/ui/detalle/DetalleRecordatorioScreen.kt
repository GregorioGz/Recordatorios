package com.example.recordatorios.ui.detalle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.recordatorios.data.DatosSimulados
import com.example.recordatorios.model.Recordatorio
import com.example.recordatorios.ui.components.ColoresRecordatorio
import com.example.recordatorios.ui.components.EstadoDeError
import com.example.recordatorios.ui.components.EstadoDePantalla
import com.example.recordatorios.ui.components.EtiquetaPrioridad
import com.example.recordatorios.ui.components.IndicadorDeCarga
import com.example.recordatorios.ui.theme.RecordatoriosTheme

@Composable
fun DetalleRecordatorioRoute(
    onVolver: () -> Unit,
    viewModel: DetalleRecordatorioViewModel = viewModel(factory = DetalleRecordatorioViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DetalleRecordatorioScreen(
        uiState = uiState,
        onVolver = onVolver,
        onReintentar = viewModel::onReintentar
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetalleRecordatorioScreen(
    uiState: DetalleRecordatorioUiState,
    onVolver: () -> Unit,
    onReintentar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Detalle") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { innerPadding ->
        val contenidoModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)

        when (uiState) {
            DetalleRecordatorioUiState.Cargando ->
                IndicadorDeCarga(modifier = contenidoModifier)

            is DetalleRecordatorioUiState.Error ->
                EstadoDeError(mensaje = uiState.mensaje, onReintentar = onReintentar, modifier = contenidoModifier)

            is DetalleRecordatorioUiState.NoEncontrado ->
                EstadoDePantalla(
                    titulo = "Recordatorio no encontrado",
                    mensaje = "No existe un recordatorio con el identificador ${uiState.id}.",
                    icono = "🔍",
                    textoAccion = "Volver al listado",
                    onAccion = onVolver,
                    modifier = contenidoModifier
                )

            is DetalleRecordatorioUiState.Encontrado ->
                DetalleContenido(recordatorio = uiState.recordatorio, modifier = contenidoModifier)
        }
    }
}

@Composable
private fun DetalleContenido(
    recordatorio: Recordatorio,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = recordatorio.titulo,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        EtiquetaPrioridad(recordatorio.prioridad)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilaDato(
                    "Categoría",
                    "${ColoresRecordatorio.etiqueta(recordatorio.categoria)} ${recordatorio.categoria.nombre}"
                )
                HorizontalDivider()
                FilaDato("Prioridad", recordatorio.prioridad.etiqueta)
                HorizontalDivider()
                FilaDato("Estado", recordatorio.estado.etiqueta)
                HorizontalDivider()
                FilaDato("Descripción", recordatorio.descripcion ?: "Sin descripción")
                recordatorio.ubicacion?.let {
                    HorizontalDivider()
                    FilaDato("Ubicación", "📍 ${it.nombre}")
                }
                recordatorio.avisoMinutosAntes?.let {
                    HorizontalDivider()
                    FilaDato("Aviso", "⏰ $it minutos antes")
                }
                HorizontalDivider()
                FilaDato("Identificador", "#${recordatorio.id}")
            }
        }
    }
}

@Composable
private fun FilaDato(etiqueta: String, valor: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            etiqueta,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(valor, style = MaterialTheme.typography.bodyLarge)
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun DetallePreview() {
    RecordatoriosTheme {
        DetalleRecordatorioScreen(
            uiState = DetalleRecordatorioUiState.Encontrado(DatosSimulados.recordatorios.first()),
            onVolver = {}, onReintentar = {}
        )
    }
}

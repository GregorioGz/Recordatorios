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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.recordatorios.data.DatosSimulados
import com.example.recordatorios.model.EstadoRecordatorio
import com.example.recordatorios.model.EstadoSync
import com.example.recordatorios.model.Recordatorio
import com.example.recordatorios.ui.components.ColoresRecordatorio
import com.example.recordatorios.ui.components.EstadoDeError
import com.example.recordatorios.ui.components.EstadoDePantalla
import com.example.recordatorios.ui.components.EtiquetaPrioridad
import com.example.recordatorios.ui.components.IndicadorDeCarga
import com.example.recordatorios.ui.components.IndicadorSync
import com.example.recordatorios.ui.components.SelectorDeOpciones
import com.example.recordatorios.ui.components.formatearFecha
import com.example.recordatorios.ui.theme.RecordatoriosTheme

@Composable
fun DetalleRecordatorioRoute(
    onVolver: () -> Unit,
    onEditar: (String) -> Unit,
    onEliminado: () -> Unit,
    viewModel: DetalleRecordatorioViewModel = viewModel(factory = DetalleRecordatorioViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Después de eliminar se vuelve al listado (una sola vez)
    LaunchedEffect(uiState) {
        if (uiState is DetalleRecordatorioUiState.Eliminado) onEliminado()
    }

    DetalleRecordatorioScreen(
        uiState = uiState,
        onVolver = onVolver,
        onReintentar = viewModel::onReintentar,
        onEditar = onEditar,
        onEliminarClick = viewModel::onEliminarClick,
        onConfirmarEliminacion = viewModel::onConfirmarEliminacion,
        onCancelarEliminacion = viewModel::onCancelarEliminacion,
        onEstadoSeleccionado = viewModel::onEstadoSeleccionado,
        onReintentarSincronizacion = viewModel::onReintentarSincronizacion,
        onMensajeMostrado = viewModel::onMensajeMostrado
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetalleRecordatorioScreen(
    uiState: DetalleRecordatorioUiState,
    onVolver: () -> Unit,
    onReintentar: () -> Unit,
    onEditar: (String) -> Unit,
    onEliminarClick: () -> Unit,
    onConfirmarEliminacion: () -> Unit,
    onCancelarEliminacion: () -> Unit,
    onEstadoSeleccionado: (EstadoRecordatorio) -> Unit,
    onReintentarSincronizacion: () -> Unit,
    onMensajeMostrado: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val mensaje = (uiState as? DetalleRecordatorioUiState.Encontrado)?.mensaje
    LaunchedEffect(mensaje) {
        if (mensaje != null) {
            snackbarHostState.showSnackbar(mensaje)
            onMensajeMostrado()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Detalle") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (uiState is DetalleRecordatorioUiState.Encontrado) {
                        IconButton(onClick = { onEditar(uiState.recordatorio.id) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Editar")
                        }
                        IconButton(onClick = onEliminarClick) {
                            Icon(Icons.Filled.Delete, contentDescription = "Eliminar")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        val contenidoModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)

        when (uiState) {
            DetalleRecordatorioUiState.Cargando, DetalleRecordatorioUiState.Eliminado ->
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

            is DetalleRecordatorioUiState.Encontrado -> {
                DetalleContenido(
                    recordatorio = uiState.recordatorio,
                    onEstadoSeleccionado = onEstadoSeleccionado,
                    onReintentarSincronizacion = onReintentarSincronizacion,
                    modifier = contenidoModifier
                )
                if (uiState.confirmandoEliminacion) {
                    AlertDialog(
                        onDismissRequest = onCancelarEliminacion,
                        title = { Text("¿Eliminar recordatorio?") },
                        text = {
                            Text("Se eliminará \"${uiState.recordatorio.titulo}\". Esta acción no se puede deshacer.")
                        },
                        confirmButton = {
                            TextButton(onClick = onConfirmarEliminacion) { Text("Eliminar") }
                        },
                        dismissButton = {
                            TextButton(onClick = onCancelarEliminacion) { Text("Cancelar") }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DetalleContenido(
    recordatorio: Recordatorio,
    onEstadoSeleccionado: (EstadoRecordatorio) -> Unit,
    onReintentarSincronizacion: () -> Unit,
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

        // Cambio rápido de estado (se guarda en Room y queda pendiente de sincronizar)
        SelectorDeOpciones(
            titulo = "Estado",
            opciones = EstadoRecordatorio.todos,
            seleccionada = recordatorio.estado,
            etiquetaDe = { it.etiqueta },
            onSeleccion = onEstadoSeleccionado,
            mensajeError = null
        )

        TarjetaSincronizacion(
            estadoSync = recordatorio.estadoSync,
            errorSync = recordatorio.errorSync,
            onReintentar = onReintentarSincronizacion
        )

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
                FilaDato("Creado", formatearFecha(recordatorio.fechaCreacion))
                HorizontalDivider()
                FilaDato("Última modificación", formatearFecha(recordatorio.fechaActualizacion))
                HorizontalDivider()
                FilaDato("Identificador", recordatorio.id)
            }
        }
    }
}

/** Estado de sincronización del recordatorio, con su último error y la opción de reintentar. */
@Composable
private fun TarjetaSincronizacion(
    estadoSync: EstadoSync,
    errorSync: String?,
    onReintentar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val conError = estadoSync == EstadoSync.ERROR
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = if (conError) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        else CardDefaults.cardColors()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Sincronización",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IndicadorSync(estadoSync)
            if (errorSync != null) {
                Text(errorSync, style = MaterialTheme.typography.bodySmall)
            }
            if (estadoSync.estaPendiente) {
                OutlinedButton(onClick = onReintentar) {
                    Text(if (conError) "Reintentar sincronización" else "Sincronizar ahora")
                }
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
            uiState = DetalleRecordatorioUiState.Encontrado(
                DatosSimulados.recordatorios.first().copy(
                    estadoSync = EstadoSync.ERROR,
                    errorSync = "El servidor tuvo un problema. Los cambios quedan guardados; reintentá más tarde."
                )
            ),
            onVolver = {}, onReintentar = {}, onEditar = {}, onEliminarClick = {},
            onConfirmarEliminacion = {}, onCancelarEliminacion = {}, onEstadoSeleccionado = {},
            onReintentarSincronizacion = {}, onMensajeMostrado = {}
        )
    }
}

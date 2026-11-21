package com.example.recordatorios.ui.crear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.recordatorios.data.DatosSimulados
import com.example.recordatorios.model.Categoria
import com.example.recordatorios.model.Prioridad
import com.example.recordatorios.model.Validador
import com.example.recordatorios.ui.components.CampoDeTexto
import com.example.recordatorios.ui.components.SelectorDeOpciones
import com.example.recordatorios.ui.theme.RecordatoriosTheme

@Composable
fun CrearRecordatorioRoute(
    onGuardadoExitoso: () -> Unit,
    onCancelar: () -> Unit,
    viewModel: CrearRecordatorioViewModel = viewModel(factory = CrearRecordatorioViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Cuando el envío termina con éxito, se avisa a la navegación (una sola vez)
    val envio = uiState.envio
    LaunchedEffect(envio) {
        if (envio is EstadoEnvio.Exito) onGuardadoExitoso()
    }

    CrearRecordatorioScreen(
        uiState = uiState,
        onTituloCambiado = viewModel::onTituloCambiado,
        onDescripcionCambiada = viewModel::onDescripcionCambiada,
        onCategoriaSeleccionada = viewModel::onCategoriaSeleccionada,
        onPrioridadSeleccionada = viewModel::onPrioridadSeleccionada,
        onGuardar = viewModel::onGuardar,
        onReintentar = viewModel::onReintentarGuardado,
        onDescartarError = viewModel::onErrorDescartado,
        onCancelar = onCancelar
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrearRecordatorioScreen(
    uiState: CrearRecordatorioUiState,
    onTituloCambiado: (String) -> Unit,
    onDescripcionCambiada: (String) -> Unit,
    onCategoriaSeleccionada: (Categoria) -> Unit,
    onPrioridadSeleccionada: (Prioridad) -> Unit,
    onGuardar: () -> Unit,
    onReintentar: () -> Unit,
    onDescartarError: () -> Unit,
    onCancelar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val habilitado = !uiState.enviando

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Nuevo recordatorio") },
                navigationIcon = {
                    IconButton(onClick = onCancelar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancelar y volver")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CampoDeTexto(
                valor = uiState.titulo,
                onValorCambiado = onTituloCambiado,
                etiqueta = "Título *",
                mensajeError = uiState.errorTitulo,
                enabled = habilitado,
                ayuda = "Mínimo ${Validador.MIN_TITULO} caracteres"
            )

            CampoDeTexto(
                valor = uiState.descripcion,
                onValorCambiado = onDescripcionCambiada,
                etiqueta = "Descripción (opcional)",
                mensajeError = uiState.errorDescripcion,
                enabled = habilitado,
                singleLine = false,
                minLines = 3,
                maxCaracteres = Validador.MAX_DESCRIPCION
            )

            SelectorDeOpciones(
                titulo = "Categoría *",
                opciones = uiState.categoriasDisponibles,
                seleccionada = uiState.categoriaSeleccionada,
                etiquetaDe = { it.nombre },
                onSeleccion = onCategoriaSeleccionada,
                mensajeError = uiState.errorCategoria,
                enabled = habilitado
            )

            SelectorDeOpciones(
                titulo = "Prioridad *",
                opciones = uiState.prioridadesDisponibles,
                seleccionada = uiState.prioridadSeleccionada,
                etiquetaDe = { it.etiqueta },
                onSeleccion = onPrioridadSeleccionada,
                mensajeError = uiState.errorPrioridad,
                enabled = habilitado
            )

            // Error general de guardado, con opción de reintento
            val envio = uiState.envio
            if (envio is EstadoEnvio.Error) {
                ErrorDeGuardado(
                    mensaje = envio.mensaje,
                    onReintentar = onReintentar,
                    onDescartar = onDescartarError
                )
            }

            if (!uiState.formularioValido) {
                Text(
                    text = "Completá los campos obligatorios (*) para habilitar el guardado.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
            ) {
                OutlinedButton(onClick = onCancelar) { Text("Cancelar") }
                Button(onClick = onGuardar, enabled = uiState.puedeGuardar) {
                    if (uiState.enviando) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Guardando…")
                    } else {
                        Text("Guardar")
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorDeGuardado(
    mensaje: String,
    onReintentar: () -> Unit,
    onDescartar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Assertive }
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "⚠ Error al guardar",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(mensaje, color = MaterialTheme.colorScheme.onErrorContainer)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDescartar) { Text("Cerrar") }
                TextButton(onClick = onReintentar) { Text("Reintentar") }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun CrearRecordatorioPreview() {
    RecordatoriosTheme {
        CrearRecordatorioScreen(
            uiState = CrearRecordatorioUiState(
                titulo = "Ab",
                categoriasDisponibles = DatosSimulados.categorias.toList(),
                errorTitulo = "El título debe tener al menos 4 caracteres"
            ),
            onTituloCambiado = {}, onDescripcionCambiada = {}, onCategoriaSeleccionada = {},
            onPrioridadSeleccionada = {}, onGuardar = {}, onReintentar = {},
            onDescartarError = {}, onCancelar = {}
        )
    }
}

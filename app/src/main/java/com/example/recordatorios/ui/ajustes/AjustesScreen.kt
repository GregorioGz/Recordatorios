package com.example.recordatorios.ui.ajustes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.recordatorios.data.preferencias.FiltroPrioridad
import com.example.recordatorios.data.preferencias.Orden
import com.example.recordatorios.data.preferencias.Tema
import com.example.recordatorios.ui.components.IndicadorDeCarga
import com.example.recordatorios.ui.components.SelectorDeOpciones
import com.example.recordatorios.ui.theme.RecordatoriosTheme

@Composable
fun AjustesRoute(
    onVolver: () -> Unit,
    viewModel: AjustesViewModel = viewModel(factory = AjustesViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AjustesScreen(
        uiState = uiState,
        onVolver = onVolver,
        onTemaSeleccionado = viewModel::onTemaSeleccionado,
        onMostrarCompletadosCambiado = viewModel::onMostrarCompletadosCambiado,
        onOrdenSeleccionado = viewModel::onOrdenSeleccionado,
        onFiltroSeleccionado = viewModel::onFiltroSeleccionado,
        onErrorDescartado = viewModel::onErrorDescartado
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AjustesScreen(
    uiState: AjustesUiState,
    onVolver: () -> Unit,
    onTemaSeleccionado: (Tema) -> Unit,
    onMostrarCompletadosCambiado: (Boolean) -> Unit,
    onOrdenSeleccionado: (Orden) -> Unit,
    onFiltroSeleccionado: (FiltroPrioridad) -> Unit,
    onErrorDescartado: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.mensajeError) {
        uiState.mensajeError?.let {
            snackbarHostState.showSnackbar(it)
            onErrorDescartado()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
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

        if (uiState.cargando) {
            IndicadorDeCarga(modifier = contenidoModifier, mensaje = "Leyendo preferencias guardadas")
            return@Scaffold
        }

        val preferencias = uiState.preferencias
        Column(
            modifier = contenidoModifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            SelectorDeOpciones(
                titulo = "Tema",
                opciones = Tema.entries,
                seleccionada = preferencias.tema,
                etiquetaDe = { it.etiqueta },
                onSeleccion = onTemaSeleccionado,
                mensajeError = null
            )
            HorizontalDivider()
            FilaInterruptor(
                titulo = "Mostrar completados",
                descripcion = "Incluye en el listado los recordatorios ya completados",
                activado = preferencias.mostrarCompletados,
                onCambio = onMostrarCompletadosCambiado
            )
            HorizontalDivider()
            SelectorDeOpciones(
                titulo = "Ordenar listado por",
                opciones = Orden.entries,
                seleccionada = preferencias.orden,
                etiquetaDe = { it.etiqueta },
                onSeleccion = onOrdenSeleccionado,
                mensajeError = null
            )
            HorizontalDivider()
            SelectorDeOpciones(
                titulo = "Filtrar por prioridad",
                opciones = FiltroPrioridad.entries,
                seleccionada = preferencias.filtroPrioridad,
                etiquetaDe = { it.etiqueta },
                onSeleccion = onFiltroSeleccionado,
                mensajeError = null
            )
            Text(
                text = "Los ajustes se guardan en el dispositivo y se conservan al cerrar la aplicación.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Fila completa tocable con un Switch (accesible: toda la fila actúa como interruptor). */
@Composable
private fun FilaInterruptor(
    titulo: String,
    descripcion: String,
    activado: Boolean,
    onCambio: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = activado, role = Role.Switch, onValueChange = onCambio),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.labelLarge)
            Text(
                descripcion,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = activado, onCheckedChange = null)
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun AjustesPreview() {
    RecordatoriosTheme {
        AjustesScreen(
            uiState = AjustesUiState(cargando = false),
            onVolver = {}, onTemaSeleccionado = {}, onMostrarCompletadosCambiado = {},
            onOrdenSeleccionado = {}, onFiltroSeleccionado = {}, onErrorDescartado = {}
        )
    }
}

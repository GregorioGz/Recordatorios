package com.example.recordatorios.ui.lista

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.recordatorios.data.DatosSimulados
import com.example.recordatorios.model.Recordatorio
import com.example.recordatorios.ui.components.EstadoDeError
import com.example.recordatorios.ui.components.EstadoVacio
import com.example.recordatorios.ui.components.IndicadorDeCarga
import com.example.recordatorios.ui.components.RecordatorioCard
import com.example.recordatorios.ui.theme.RecordatoriosTheme

/**
 * "Route": conecta el ViewModel con la pantalla. Observa el StateFlow con
 * collectAsStateWithLifecycle (la recolección se pausa cuando la pantalla no está visible).
 */
@Composable
fun ListaRecordatoriosRoute(
    onCrearRecordatorio: () -> Unit,
    onRecordatorioClick: (Int) -> Unit,
    viewModel: ListaRecordatoriosViewModel = viewModel(factory = ListaRecordatoriosViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ListaRecordatoriosScreen(
        uiState = uiState,
        onCrearRecordatorio = onCrearRecordatorio,
        onRecordatorioClick = onRecordatorioClick,
        onReintentar = viewModel::onReintentar,
        onSimular = viewModel::onSimular
    )
}

/** Pantalla "tonta": solo dibuja el estado recibido y avisa eventos por lambdas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListaRecordatoriosScreen(
    uiState: ListaRecordatoriosUiState,
    onCrearRecordatorio: () -> Unit,
    onRecordatorioClick: (Int) -> Unit,
    onReintentar: () -> Unit,
    onSimular: (Simulacion) -> Unit,
    modifier: Modifier = Modifier
) {
    var menuAbierto by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Mis recordatorios") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                actions = {
                    Box {
                        IconButton(onClick = { menuAbierto = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Simular estados")
                        }
                        DropdownMenu(expanded = menuAbierto, onDismissRequest = { menuAbierto = false }) {
                            Simulacion.entries.forEach { simulacion ->
                                DropdownMenuItem(
                                    text = { Text(simulacion.etiqueta) },
                                    onClick = {
                                        menuAbierto = false
                                        onSimular(simulacion)
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCrearRecordatorio,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Nuevo") }
            )
        }
    ) { innerPadding ->
        val contenidoModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)

        // when EXHAUSTIVO: cada estado dibuja una sola cosa, sin combinaciones contradictorias
        when (uiState) {
            ListaRecordatoriosUiState.Cargando ->
                IndicadorDeCarga(modifier = contenidoModifier)

            ListaRecordatoriosUiState.Vacio ->
                EstadoVacio(
                    mensaje = "Todavía no existen recordatorios. Creá el primero para empezar.",
                    textoAccion = "Crear recordatorio",
                    onAccion = onCrearRecordatorio,
                    modifier = contenidoModifier
                )

            is ListaRecordatoriosUiState.Error ->
                EstadoDeError(
                    mensaje = uiState.mensaje,
                    onReintentar = onReintentar,
                    modifier = contenidoModifier
                )

            is ListaRecordatoriosUiState.ConDatos ->
                ListaDeRecordatorios(
                    recordatorios = uiState.recordatorios,
                    onRecordatorioClick = onRecordatorioClick,
                    modifier = contenidoModifier
                )
        }
    }
}

@Composable
private fun ListaDeRecordatorios(
    recordatorios: List<Recordatorio>,
    onRecordatorioClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        // espacio extra abajo para que el FAB no tape la última tarjeta
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(recordatorios, key = { it.id }) { recordatorio ->
            RecordatorioCard(
                recordatorio = recordatorio,
                onClick = { onRecordatorioClick(recordatorio.id) }
            )
        }
    }
}

// ---------- Previews de cada estado ----------
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ListaConDatosPreview() {
    RecordatoriosTheme {
        ListaRecordatoriosScreen(
            uiState = ListaRecordatoriosUiState.ConDatos(DatosSimulados.recordatorios),
            onCrearRecordatorio = {}, onRecordatorioClick = {}, onReintentar = {}, onSimular = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ListaVaciaPreview() {
    RecordatoriosTheme {
        ListaRecordatoriosScreen(
            uiState = ListaRecordatoriosUiState.Vacio,
            onCrearRecordatorio = {}, onRecordatorioClick = {}, onReintentar = {}, onSimular = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ListaErrorPreview() {
    RecordatoriosTheme {
        ListaRecordatoriosScreen(
            uiState = ListaRecordatoriosUiState.Error("No se pudieron cargar los recordatorios."),
            onCrearRecordatorio = {}, onRecordatorioClick = {}, onReintentar = {}, onSimular = {}
        )
    }
}

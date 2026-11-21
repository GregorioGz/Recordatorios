package com.example.recordatorios.ui.lista

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.recordatorios.model.EstadoSincronizacion
import com.example.recordatorios.model.Recordatorio
import com.example.recordatorios.ui.components.BannerSincronizacion
import com.example.recordatorios.ui.components.EstadoDeError
import com.example.recordatorios.ui.components.EstadoVacio
import com.example.recordatorios.ui.components.IndicadorDeCarga
import com.example.recordatorios.ui.components.RecordatorioCard
import com.example.recordatorios.ui.theme.RecordatoriosTheme

/**
 * "Route": conecta el ViewModel con la pantalla. Observa los StateFlow con
 * collectAsStateWithLifecycle (la recolección se pausa cuando la pantalla no está visible).
 */
@Composable
fun ListaRecordatoriosRoute(
    onCrearRecordatorio: () -> Unit,
    onRecordatorioClick: (String) -> Unit,
    onAbrirAjustes: () -> Unit,
    viewModel: ListaRecordatoriosViewModel = viewModel(factory = ListaRecordatoriosViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sincronizacion by viewModel.sincronizacion.collectAsStateWithLifecycle()
    ListaRecordatoriosScreen(
        uiState = uiState,
        sincronizacion = sincronizacion,
        onCrearRecordatorio = onCrearRecordatorio,
        onRecordatorioClick = onRecordatorioClick,
        onAbrirAjustes = onAbrirAjustes,
        onReintentar = viewModel::onReintentar,
        onSincronizarAhora = viewModel::onSincronizarAhora,
        onSimular = viewModel::onSimular,
        onMensajeMostrado = viewModel::onMensajeMostrado
    )
}

/** Pantalla "tonta": solo dibuja el estado recibido y avisa eventos por lambdas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListaRecordatoriosScreen(
    uiState: ListaRecordatoriosUiState,
    sincronizacion: SincronizacionUiState,
    onCrearRecordatorio: () -> Unit,
    onRecordatorioClick: (String) -> Unit,
    onAbrirAjustes: () -> Unit,
    onReintentar: () -> Unit,
    onSincronizarAhora: () -> Unit,
    onSimular: (Simulacion) -> Unit,
    onMensajeMostrado: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(sincronizacion.mensaje) {
        sincronizacion.mensaje?.let {
            snackbarHostState.showSnackbar(it)
            onMensajeMostrado()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Mis recordatorios") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                actions = {
                    IconButton(onClick = onSincronizarAhora, enabled = !sincronizacion.estado.sincronizando) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Sincronizar ahora")
                    }
                    IconButton(onClick = onAbrirAjustes) {
                        Icon(Icons.Filled.Settings, contentDescription = "Ajustes")
                    }
                    MenuDePruebas(
                        escenarioServidorActivo = sincronizacion.escenarioServidorActivo,
                        onSimular = onSimular
                    )
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
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Indicador global: sin conexión / sincronizando / pendientes / último error
            BannerSincronizacion(estado = sincronizacion.estado, onSincronizarAhora = onSincronizarAhora)

            val contenidoModifier = Modifier
                .weight(1f)
                .fillMaxWidth()

            // when EXHAUSTIVO: cada estado dibuja una sola cosa, sin combinaciones contradictorias
            when (uiState) {
                ListaRecordatoriosUiState.Cargando ->
                    IndicadorDeCarga(modifier = contenidoModifier)

                is ListaRecordatoriosUiState.Vacio ->
                    if (uiState.ocultosPorAjustes) EstadoVacio(
                        mensaje = "No hay recordatorios que coincidan con los ajustes actuales " +
                            "(filtro de prioridad o completados ocultos).",
                        textoAccion = "Cambiar ajustes",
                        onAccion = onAbrirAjustes,
                        modifier = contenidoModifier
                    ) else EstadoVacio(
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
}

/** Menú ⋮ SOLO PARA PRUEBAS: simulaciones locales, de preferencias y del servidor. */
@Composable
private fun MenuDePruebas(
    escenarioServidorActivo: Simulacion?,
    onSimular: (Simulacion) -> Unit,
    modifier: Modifier = Modifier
) {
    var menuAbierto by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { menuAbierto = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Menú de pruebas")
        }
        DropdownMenu(expanded = menuAbierto, onDismissRequest = { menuAbierto = false }) {
            Simulacion.entries.forEachIndexed { indice, simulacion ->
                if (indice > 0 && Simulacion.entries[indice - 1].grupo != simulacion.grupo) {
                    HorizontalDivider()
                }
                DropdownMenuItem(
                    text = { Text(simulacion.etiqueta) },
                    trailingIcon = if (simulacion == escenarioServidorActivo) {
                        { Icon(Icons.Filled.Check, contentDescription = "Activo") }
                    } else null,
                    onClick = {
                        menuAbierto = false
                        onSimular(simulacion)
                    }
                )
            }
        }
    }
}

@Composable
private fun ListaDeRecordatorios(
    recordatorios: List<Recordatorio>,
    onRecordatorioClick: (String) -> Unit,
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
@Composable
private fun ListaPreview(uiState: ListaRecordatoriosUiState, estado: EstadoSincronizacion = EstadoSincronizacion()) {
    RecordatoriosTheme {
        ListaRecordatoriosScreen(
            uiState = uiState,
            sincronizacion = SincronizacionUiState(estado = estado),
            onCrearRecordatorio = {}, onRecordatorioClick = {}, onAbrirAjustes = {}, onReintentar = {},
            onSincronizarAhora = {}, onSimular = {}, onMensajeMostrado = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ListaConDatosPreview() =
    ListaPreview(ListaRecordatoriosUiState.ConDatos(DatosSimulados.recordatorios), EstadoSincronizacion(pendientes = 2))

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ListaVaciaPreview() = ListaPreview(ListaRecordatoriosUiState.Vacio())

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ListaErrorPreview() =
    ListaPreview(ListaRecordatoriosUiState.Error("No se pudieron leer los recordatorios."), EstadoSincronizacion(hayConexion = false))

package com.example.recordatorios.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.recordatorios.data.DatosSimulados
import com.example.recordatorios.model.EstadoRecordatorio
import com.example.recordatorios.model.Prioridad
import com.example.recordatorios.model.Recordatorio
import com.example.recordatorios.model.UiState
import com.example.recordatorios.ui.theme.RecordatoriosTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaRecordatorios(modifier: Modifier = Modifier) {
    // Filtros seleccionados (null = sin filtro)
    var estadoFiltro by remember { mutableStateOf<EstadoRecordatorio?>(null) }
    var prioridadFiltro by remember { mutableStateOf<Prioridad?>(null) }

    val todos = DatosSimulados.recordatorios
    var lista = todos
    estadoFiltro?.let { lista = DatosSimulados.filtrarPorEstado(lista, it) }
    prioridadFiltro?.let { lista = DatosSimulados.filtrarPorPrioridad(lista, it) }

    val uiState: UiState = if (lista.isEmpty()) UiState.Vacio else UiState.Exito(lista)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Mis recordatorios") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Resumen(todos)

            // Filtro por estado
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(EstadoRecordatorio.todos) { estado ->
                    FilterChip(
                        selected = estadoFiltro == estado,
                        onClick = { estadoFiltro = if (estadoFiltro == estado) null else estado },
                        label = { Text(estado.etiqueta) }
                    )
                }
            }
            // Filtro por prioridad
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(Prioridad.todas) { prioridad ->
                    FilterChip(
                        selected = prioridadFiltro == prioridad,
                        onClick = {
                            prioridadFiltro = if (prioridadFiltro == prioridad) null else prioridad
                        },
                        label = { Text("Prioridad ${prioridad.etiqueta}") }
                    )
                }
            }

            when (uiState) {
                is UiState.Exito -> ListaRecordatorios(uiState.recordatorios)
                UiState.Vacio -> MensajeCentrado("No hay recordatorios con ese filtro")
                UiState.Cargando -> MensajeCentrado("Cargando…")
                is UiState.Error -> MensajeCentrado(uiState.mensaje)
            }
        }
    }
}

@Composable
private fun Resumen(todos: List<Recordatorio>) {
    val porEstado = DatosSimulados.agruparPorEstado(todos)
    Text(
        text = "${todos.size} recordatorios · ${DatosSimulados.categorias.size} categorías · " +
            "${porEstado[EstadoRecordatorio.Pendiente]?.size ?: 0} pendientes",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun ListaRecordatorios(recordatorios: List<Recordatorio>) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(recordatorios, key = { it.id }) { TarjetaRecordatorio(it) }
    }
}

@Composable
fun TarjetaRecordatorio(r: Recordatorio) {
    val colorCategoria = DatosSimulados.colorPorCategoria[r.categoria] ?: Color.Gray
    val etiqueta = DatosSimulados.etiquetaPorCategoria[r.categoria] ?: "•"
    val colorPrioridad = DatosSimulados.colorPorPrioridad[r.prioridad] ?: Color.Gray
    val completado = r.estado == EstadoRecordatorio.Completado

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            // Franja de color por categoría (Map categoría -> color)
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(colorCategoria)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    r.titulo,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (completado) TextDecoration.LineThrough else null
                )
                Text(
                    "$etiqueta ${r.categoria.nombre}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorCategoria
                )
                r.descripcion?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                r.ubicacion?.let {
                    Text("📍 ${it.nombre}", style = MaterialTheme.typography.bodySmall)
                }
                r.avisoMinutosAntes?.let {
                    Text("⏰ Aviso $it min antes", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.padding(top = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = {},
                        label = { Text(r.prioridad.etiqueta) },
                        leadingIcon = {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(colorPrioridad)
                            )
                        }
                    )
                    AssistChip(onClick = {}, label = { Text(r.estado.etiqueta) })
                }
            }
        }
    }
}

@Composable
private fun MensajeCentrado(texto: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(texto, style = MaterialTheme.typography.bodyLarge)
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PantallaRecordatoriosPreview() {
    RecordatoriosTheme { PantallaRecordatorios() }
}

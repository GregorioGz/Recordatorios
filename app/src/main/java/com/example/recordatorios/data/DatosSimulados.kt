package com.example.recordatorios.data

import androidx.compose.ui.graphics.Color
import com.example.recordatorios.model.Categoria
import com.example.recordatorios.model.EstadoRecordatorio
import com.example.recordatorios.model.Prioridad
import com.example.recordatorios.model.Recordatorio
import com.example.recordatorios.model.Ubicacion

/**
 * Datos simulados (sin persistencia ni red en esta etapa).
 * Uso de colecciones:
 *  - List  -> recordatorios (ordenados, admiten duplicados de categoría)
 *  - Set   -> categorías únicas (descarta repetidas)
 *  - Map   -> categoría -> color / etiqueta; prioridad -> color
 */
object DatosSimulados {

    private val trabajo = Categoria(1, "Trabajo")
    private val estudio = Categoria(2, "Estudio")
    private val salud = Categoria(3, "Salud")
    private val hogar = Categoria(4, "Hogar")

    // List inmutable de recordatorios simulados
    val recordatorios: List<Recordatorio> = listOf(
        Recordatorio(
            id = 1,
            titulo = "Entregar Actividad 1",
            categoria = estudio,
            prioridad = Prioridad.Alta,
            estado = EstadoRecordatorio.EnCurso,
            descripcion = "Subir el PDF con capturas y enlace al repositorio",
            avisoMinutosAntes = 60
        ),
        Recordatorio(
            id = 2,
            titulo = "Reunión de equipo",
            categoria = trabajo,
            prioridad = Prioridad.Media,
            ubicacion = Ubicacion("Oficina central", -34.6037, -58.3816)
        ),
        Recordatorio(
            id = 3,
            titulo = "Turno con el médico",
            categoria = salud,
            prioridad = Prioridad.Alta,
            avisoMinutosAntes = 120
        ),
        Recordatorio(
            id = 4,
            titulo = "Comprar verduras",
            categoria = hogar,
            prioridad = Prioridad.Baja,
            estado = EstadoRecordatorio.Completado,
            ubicacion = Ubicacion("Verdulería del barrio", -34.6100, -58.3900)
        ),
        Recordatorio(
            id = 5,
            titulo = "Repasar Kotlin: sealed classes",
            categoria = estudio,
            prioridad = Prioridad.Media,
            descripcion = "Capítulo de clases selladas e inmutabilidad"
        ),
        Recordatorio(
            id = 6,
            titulo = "Enviar informe mensual",
            categoria = trabajo,
            prioridad = Prioridad.Alta,
            estado = EstadoRecordatorio.Completado
        ),
        Recordatorio(
            id = 7,
            titulo = "Salir a caminar",
            categoria = salud,
            prioridad = Prioridad.Baja
        )
    )

    // Set: aunque varios recordatorios compartan categoría, el Set no repite
    val categorias: Set<Categoria> = recordatorios.map { it.categoria }.toSet()

    // Map: cada categoría asociada a un color
    val colorPorCategoria: Map<Categoria, Color> = mapOf(
        trabajo to Color(0xFF1E88E5),
        estudio to Color(0xFF8E24AA),
        salud to Color(0xFF43A047),
        hogar to Color(0xFFFB8C00)
    )

    // Map: cada categoría asociada a una etiqueta corta (emoji)
    val etiquetaPorCategoria: Map<Categoria, String> = mapOf(
        trabajo to "💼",
        estudio to "📚",
        salud to "❤️",
        hogar to "🏠"
    )

    // Map: color según prioridad
    val colorPorPrioridad: Map<Prioridad, Color> = mapOf(
        Prioridad.Alta to Color(0xFFE53935),
        Prioridad.Media to Color(0xFFFDD835),
        Prioridad.Baja to Color(0xFF7CB342)
    )

    // ---------- Filtros ----------
    fun filtrarPorEstado(
        lista: List<Recordatorio>,
        estado: EstadoRecordatorio
    ): List<Recordatorio> = lista.filter { it.estado == estado }

    fun filtrarPorPrioridad(
        lista: List<Recordatorio>,
        prioridad: Prioridad
    ): List<Recordatorio> = lista.filter { it.prioridad == prioridad }

    // Map agrupado: estado -> lista de recordatorios (útil para contadores)
    fun agruparPorEstado(lista: List<Recordatorio>): Map<EstadoRecordatorio, List<Recordatorio>> =
        lista.groupBy { it.estado }
}

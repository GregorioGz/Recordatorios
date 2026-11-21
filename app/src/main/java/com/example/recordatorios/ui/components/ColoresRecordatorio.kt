package com.example.recordatorios.ui.components

import androidx.compose.ui.graphics.Color
import com.example.recordatorios.data.DatosSimulados
import com.example.recordatorios.model.Categoria
import com.example.recordatorios.model.Prioridad

/** Map: cada categoría asociada a un color y a una etiqueta (viene de la Actividad 1). */
object ColoresRecordatorio {

    private val colorPorCategoria: Map<Categoria, Color> = mapOf(
        DatosSimulados.trabajo to Color(0xFF1E88E5),
        DatosSimulados.estudio to Color(0xFF8E24AA),
        DatosSimulados.salud to Color(0xFF43A047),
        DatosSimulados.hogar to Color(0xFFFB8C00)
    )

    private val etiquetaPorCategoria: Map<Categoria, String> = mapOf(
        DatosSimulados.trabajo to "💼",
        DatosSimulados.estudio to "📚",
        DatosSimulados.salud to "❤️",
        DatosSimulados.hogar to "🏠"
    )

    private val colorPorPrioridad: Map<Prioridad, Color> = mapOf(
        Prioridad.Alta to Color(0xFFE53935),
        Prioridad.Media to Color(0xFFFDD835),
        Prioridad.Baja to Color(0xFF7CB342)
    )

    fun color(categoria: Categoria): Color = colorPorCategoria[categoria] ?: Color.Gray
    fun etiqueta(categoria: Categoria): String = etiquetaPorCategoria[categoria] ?: "•"
    fun color(prioridad: Prioridad): Color = colorPorPrioridad[prioridad] ?: Color.Gray
}

package com.example.recordatorios.ui.ajustes

import com.example.recordatorios.data.preferencias.PreferenciasUsuario

/**
 * Estado de la pantalla de Ajustes.
 * `cargando` es true hasta que DataStore entrega la primera lectura.
 */
data class AjustesUiState(
    val preferencias: PreferenciasUsuario = PreferenciasUsuario(),
    val cargando: Boolean = true,
    val mensajeError: String? = null
)

package com.example.recordatorios.ui.ajustes

import com.example.recordatorios.data.preferencias.PreferenciasUsuario

/**
 * Estado de la pantalla de Ajustes.
 * `cargando` es true hasta que DataStore entrega la primera lectura.
 */
data class AjustesUiState(
    val preferencias: PreferenciasUsuario = PreferenciasUsuario(),
    val cargando: Boolean = true,
    val mensajeError: String? = null,
    /** Email de la cuenta activa. */
    val cuenta: String? = null,
    /** uid de la cuenta: solo se muestra en el entorno dev (pruebas de autorización). */
    val uidVisible: String? = null,
    val cierreSesion: CierreSesionUiState = CierreSesionUiState.Inactivo
)

/** Flujo de cierre de sesión (mutuamente excluyentes). */
sealed interface CierreSesionUiState {
    data object Inactivo : CierreSesionUiState

    /** Hay cambios sin sincronizar: se pide confirmación. */
    data class Confirmando(val pendientes: Int) : CierreSesionUiState

    /** Sincronizando y/o limpiando los datos locales. */
    data object Cerrando : CierreSesionUiState
}

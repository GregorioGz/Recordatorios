package com.example.recordatorios.model

/** Identidad autenticada, sin tipos del proveedor (nunca FirebaseUser). */
data class Usuario(
    val uid: String,
    val email: String?
)

/**
 * Estado de la sesión que observan el repositorio y la navegación.
 *  - Verificando: todavía no se sabe si hay sesión (inicio de la app).
 *  - SinSesion: nadie inició sesión (o la cerró).
 *  - Activa: hay una cuenta autenticada; sus datos se asocian a `usuario.uid`.
 *  - Invalida: la sesión existía pero el proveedor la rechazó (cuenta deshabilitada,
 *    eliminada o token revocado). Se limpian los datos locales y se vuelve al acceso.
 */
sealed interface EstadoSesion {
    data object Verificando : EstadoSesion
    data object SinSesion : EstadoSesion
    data class Activa(val usuario: Usuario) : EstadoSesion
    data class Invalida(val motivo: String) : EstadoSesion
}

/** uid de la sesión activa, o null en cualquier otro estado. */
val EstadoSesion.uidActivo: String?
    get() = (this as? EstadoSesion.Activa)?.usuario?.uid

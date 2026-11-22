package com.example.recordatorios.data.auth

import com.example.recordatorios.model.EstadoSesion
import kotlinx.coroutines.flow.Flow

/**
 * Contrato de autenticación. Lo usan el repositorio de recordatorios (para obtener
 * el uid activo) y los ViewModel de acceso y sesión. No expone tipos de Firebase.
 */
interface AuthRepository {
    /** Estado de la sesión: emite solo cuando cambia. */
    val sesion: Flow<EstadoSesion>

    suspend fun registrar(email: String, contrasena: String): ResultadoAuth
    suspend fun iniciarSesion(email: String, contrasena: String): ResultadoAuth

    /** Cierra la sesión en el proveedor. La limpieza local la coordina el repositorio de recordatorios. */
    suspend fun cerrarSesion()

    /**
     * Comprueba con el servidor que la sesión guardada siga siendo válida.
     * Sin red no invalida nada (la app sigue funcionando offline).
     */
    suspend fun verificarSesion()

    /** Otra fuente (por ejemplo Firestore con UNAUTHENTICATED) detectó que la sesión ya no es aceptada. */
    suspend fun invalidarSesion(motivo: String)
}

package com.example.recordatorios.data.auth

import com.example.recordatorios.model.EstadoSesion
import com.example.recordatorios.model.Usuario
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * Implementación con Firebase Authentication. Es la ÚNICA clase que conoce
 * FirebaseAuth/FirebaseUser: hacia afuera solo salen [EstadoSesion], [Usuario] y [ErrorAuth].
 * Firebase guarda y renueva los tokens: la app no almacena contraseñas ni tokens.
 */
class AuthRepositoryFirebase(
    private val auth: FirebaseAuth,
    alcance: CoroutineScope
) : AuthRepository {

    /** Motivo de la última invalidación; se limpia al volver a iniciar sesión o al cerrarla. */
    private val motivoInvalida = MutableStateFlow<String?>(null)

    /** Cambios de sesión del proveedor (inicio, cierre, cuenta eliminada en este dispositivo). */
    private val sesionDelProveedor: Flow<EstadoSesion> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.let { EstadoSesion.Activa(it.aUsuario()) } ?: EstadoSesion.SinSesion)
        }
        auth.addAuthStateListener(listener)   // Firebase informa enseguida el estado guardado
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /**
     * Una sola suscripción al proveedor, compartida por toda la app.
     * Empieza en Verificando hasta que Firebase informa la sesión guardada.
     */
    override val sesion: StateFlow<EstadoSesion> =
        combine(sesionDelProveedor, motivoInvalida) { estado, motivo ->
            if (motivo != null && estado !is EstadoSesion.Activa) EstadoSesion.Invalida(motivo) else estado
        }
            .distinctUntilChanged()
            .stateIn(alcance, SharingStarted.Eagerly, EstadoSesion.Verificando)

    override suspend fun registrar(email: String, contrasena: String): ResultadoAuth =
        ejecutar { auth.createUserWithEmailAndPassword(email.trim(), contrasena).await().user }

    override suspend fun iniciarSesion(email: String, contrasena: String): ResultadoAuth =
        ejecutar { auth.signInWithEmailAndPassword(email.trim(), contrasena).await().user }

    override suspend fun cerrarSesion() {
        motivoInvalida.value = null
        auth.signOut()
    }

    override suspend fun verificarSesion() {
        val usuario = auth.currentUser ?: return
        try {
            withTimeout(TIMEOUT_MS) {
                usuario.reload().await()            // detecta cuenta deshabilitada o eliminada
                usuario.getIdToken(true).await()    // detecta token revocado
            }
        } catch (e: TimeoutCancellationException) {
            // Sin respuesta (offline): se conserva la sesión
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = traducir(e, enSesionAbierta = true)
            if (error.invalidaLaSesion) invalidarSesion(error.mensaje)
        }
    }

    override suspend fun invalidarSesion(motivo: String) {
        // Firebase puede haber cerrado la sesión por su cuenta (cuenta eliminada): igual se informa el motivo
        motivoInvalida.value = motivo
        if (auth.currentUser != null) auth.signOut()
    }

    private suspend fun ejecutar(operacion: suspend () -> FirebaseUser?): ResultadoAuth =
        try {
            val usuario = withTimeout(TIMEOUT_MS) { operacion() }
            if (usuario != null) {
                motivoInvalida.value = null
                ResultadoAuth.Exito(usuario.aUsuario())
            } else {
                ResultadoAuth.Error(ErrorAuth.DESCONOCIDO)
            }
        } catch (e: TimeoutCancellationException) {
            ResultadoAuth.Error(ErrorAuth.SIN_CONEXION)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultadoAuth.Error(traducir(e, enSesionAbierta = false))
        }

    /** Extrae el código del proveedor y delega en el único traductor: [ErrorAuth.desdeCodigo]. */
    private fun traducir(error: Exception, enSesionAbierta: Boolean): ErrorAuth {
        val codigo = when (error) {
            is FirebaseNetworkException -> "ERROR_NETWORK_REQUEST_FAILED"
            is FirebaseTooManyRequestsException -> "ERROR_TOO_MANY_REQUESTS"
            is FirebaseAuthException -> error.errorCode
            else -> null
        }
        return ErrorAuth.desdeCodigo(codigo, enSesionAbierta)
    }

    private fun FirebaseUser.aUsuario() = Usuario(uid = uid, email = email)

    private companion object {
        const val TIMEOUT_MS = 15_000L
    }
}

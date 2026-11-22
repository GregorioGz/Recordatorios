package com.example.recordatorios.data.remote

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Implementación con Cloud Firestore. Es la ÚNICA clase que conoce el SDK de Firestore.
 * Estructura: users/{uid}/reminders/{reminderId}, con reminderId = el UUID de Room.
 * Los datos entran y salen como Map (sin toObject() por reflexión, seguro con R8).
 */
class FuenteRemotaFirestore(
    private val firestore: FirebaseFirestore,
    private val timeoutMs: Long = TIMEOUT_MS
) : FuenteRemotaRecordatorios, PruebaDeAutorizacion {

    private fun coleccion(uid: String): CollectionReference =
        firestore.collection(COLECCION_USUARIOS).document(uid).collection(COLECCION_RECORDATORIOS)

    override suspend fun descargar(uid: String): ResultadoRemoto<List<DocumentoRemoto>> = ejecutar {
        // Source.SERVER: siempre del servidor (Room ya es la caché local)
        coleccion(uid).get(Source.SERVER).await().documents.map { DocumentoRemoto(it.id, it.data.orEmpty()) }
    }

    override suspend fun guardar(uid: String, id: String, datos: Map<String, Any?>): ResultadoRemoto<Unit> = ejecutar {
        coleccion(uid).document(id).set(datos).await()
        Unit
    }

    override suspend fun eliminar(uid: String, id: String): ResultadoRemoto<Unit> = ejecutar {
        coleccion(uid).document(id).delete().await()
        Unit
    }

    override fun observarCambios(uid: String): Flow<ResultadoRemoto<List<CambioRemoto>>> = callbackFlow {
        val registro = coleccion(uid).addSnapshotListener { instantanea, error ->
            if (error != null) {
                trySend(resultadoDesdeCodigoFirestore(error.code.name))
                close()
                return@addSnapshotListener
            }
            val cambios = instantanea?.documentChanges.orEmpty()
                // Las escrituras propias sin confirmar ya están en Room: se ignoran
                .filter { !it.document.metadata.hasPendingWrites() }
                .map { cambio ->
                    if (cambio.type == DocumentChange.Type.REMOVED) {
                        CambioRemoto.Eliminado(cambio.document.id)
                    } else {
                        CambioRemoto.Guardado(DocumentoRemoto(cambio.document.id, cambio.document.data))
                    }
                }
            if (cambios.isNotEmpty()) trySend(ResultadoRemoto.Exito(cambios))
        }
        awaitClose { registro.remove() }   // se cancela al cerrar sesión
    }

    // ---------- Prueba de autorización (solo dev) ----------
    override suspend fun leerDeOtraCuenta(otroUid: String): ResultadoRemoto<Int> = ejecutar {
        coleccion(otroUid).get(Source.SERVER).await().size()
    }

    override suspend fun escribirEnOtraCuenta(otroUid: String, id: String, datos: Map<String, Any?>): ResultadoRemoto<Unit> =
        ejecutar {
            coleccion(otroUid).document(id).set(datos).await()
            Unit
        }

    /** Todas las operaciones pasan por el mismo traductor de errores. */
    private suspend fun <T> ejecutar(operacion: suspend () -> T): ResultadoRemoto<T> =
        ejecutarRemoto(timeoutMs, codigoDe = { (it as? FirebaseFirestoreException)?.code?.name }, operacion)

    companion object {
        const val COLECCION_USUARIOS = "users"
        const val COLECCION_RECORDATORIOS = "reminders"
        private const val TIMEOUT_MS = 10_000L

        /**
         * Firestore con caché SOLO en memoria: Room ya es la fuente local offline;
         * una segunda caché persistente competiría con ella y guardaría otra copia en disco.
         */
        fun crearInstancia(): FirebaseFirestore = FirebaseFirestore.getInstance().apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
        }
    }
}

package com.example.recordatorios.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.example.recordatorios.model.EstadoSync
import kotlinx.coroutines.flow.Flow

/**
 * Acceso a la tabla de recordatorios.
 * - TODAS las lecturas y escrituras masivas reciben el uid de la sesión activa y
 *   filtran por `uidPropietario`: una cuenta nunca ve ni modifica filas de otra.
 * - Consultas para la interfaz: Flow (Room vuelve a emitir cuando la tabla cambia).
 * - Operaciones de escritura y consultas de sincronización: suspend.
 * Los registros PENDIENTE_ELIMINAR quedan ocultos para la interfaz.
 */
@Dao
interface RecordatorioDao {

    // ---------- Consultas observables (interfaz) ----------
    @Query(
        "SELECT * FROM recordatorios WHERE uidPropietario = :uid " +
            "AND estadoSync != 'PENDIENTE_ELIMINAR' ORDER BY fechaCreacion DESC"
    )
    fun observarTodos(uid: String): Flow<List<RecordatorioEntity>>

    /** Filtro por prioridad (clave: ALTA / MEDIA / BAJA). */
    @Query(
        "SELECT * FROM recordatorios WHERE uidPropietario = :uid AND prioridad = :prioridad " +
            "AND estadoSync != 'PENDIENTE_ELIMINAR' ORDER BY fechaCreacion DESC"
    )
    fun observarPorPrioridad(uid: String, prioridad: String): Flow<List<RecordatorioEntity>>

    @Query(
        "SELECT * FROM recordatorios WHERE uidPropietario = :uid AND id = :id " +
            "AND estadoSync != 'PENDIENTE_ELIMINAR'"
    )
    fun observarPorId(uid: String, id: String): Flow<RecordatorioEntity?>

    @Query("SELECT COUNT(*) FROM recordatorios WHERE uidPropietario = :uid AND estadoSync != 'SINCRONIZADO'")
    fun observarCantidadPendientes(uid: String): Flow<Int>

    // ---------- Consultas puntuales (repositorio / sincronización) ----------
    @Query("SELECT * FROM recordatorios WHERE uidPropietario = :uid AND id = :id")
    suspend fun obtenerPorId(uid: String, id: String): RecordatorioEntity?

    @Query("SELECT COUNT(*) FROM recordatorios WHERE uidPropietario = :uid AND estadoSync != 'SINCRONIZADO'")
    suspend fun contarPendientes(uid: String): Int

    @Query("SELECT COUNT(*) FROM recordatorios WHERE uidPropietario = :uid AND estadoSync != 'PENDIENTE_ELIMINAR'")
    suspend fun contarVisibles(uid: String): Int

    /** Todo lo que el servidor todavía no confirmó (incluye ERROR, para reintentar). */
    @Query(
        "SELECT * FROM recordatorios WHERE uidPropietario = :uid AND estadoSync != 'SINCRONIZADO' " +
            "ORDER BY fechaActualizacion ASC"
    )
    suspend fun obtenerPendientes(uid: String): List<RecordatorioEntity>

    @Query("SELECT id FROM recordatorios WHERE uidPropietario = :uid AND estadoSync = 'SINCRONIZADO'")
    suspend fun idsSincronizados(uid: String): List<String>

    // ---------- Escrituras ----------
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(recordatorio: RecordatorioEntity)

    /** Devuelve -1 si el id ya existía (no se inserta nada). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertarSiNoExiste(recordatorio: RecordatorioEntity): Long

    /** Inserta o reemplaza por id: nunca genera una segunda fila con el mismo id. */
    @Upsert
    suspend fun upsert(recordatorio: RecordatorioEntity)

    @Update
    suspend fun actualizar(recordatorio: RecordatorioEntity): Int

    @Query("DELETE FROM recordatorios WHERE uidPropietario = :uid AND id = :id")
    suspend fun eliminarPorId(uid: String, id: String): Int

    /** Limpieza al cerrar sesión: borra los datos de esa cuenta. */
    @Query("DELETE FROM recordatorios WHERE uidPropietario = :uid")
    suspend fun eliminarDePropietario(uid: String): Int

    /**
     * Limpieza al iniciar sesión: borra cualquier fila que no sea de la cuenta activa
     * (restos de otra cuenta o filas sin dueño migradas desde la versión 1).
     */
    @Query("DELETE FROM recordatorios WHERE uidPropietario != :uid")
    suspend fun eliminarDeOtrosPropietarios(uid: String): Int

    /**
     * Actualiza el estado de sincronización de LA MISMA fila, solo si no fue
     * modificada mientras viajaba al servidor (misma fechaActualizacion).
     * Devuelve la cantidad de filas actualizadas (0 = hubo un cambio local más nuevo).
     */
    @Query(
        "UPDATE recordatorios SET estadoSync = :estadoSync, errorSync = :errorSync " +
            "WHERE uidPropietario = :uid AND id = :id AND fechaActualizacion = :fechaActualizacion"
    )
    suspend fun marcarEstadoSync(
        uid: String,
        id: String,
        fechaActualizacion: Long,
        estadoSync: EstadoSync,
        errorSync: String?
    ): Int

    /** Solo para la prueba de sincronización concurrente del menú de pruebas. */
    @Query(
        "UPDATE recordatorios SET estadoSync = 'PENDIENTE_ACTUALIZAR' " +
            "WHERE uidPropietario = :uid AND estadoSync = 'SINCRONIZADO'"
    )
    suspend fun marcarSincronizadosComoPendientes(uid: String): Int
}

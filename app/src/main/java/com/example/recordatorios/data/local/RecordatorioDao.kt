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
 * - Consultas para la interfaz: Flow (Room vuelve a emitir cuando la tabla cambia).
 * - Operaciones de escritura y consultas de sincronización: suspend.
 * Los registros PENDIENTE_ELIMINAR quedan ocultos para la interfaz.
 */
@Dao
interface RecordatorioDao {

    // ---------- Consultas observables (interfaz) ----------
    @Query("SELECT * FROM recordatorios WHERE estadoSync != 'PENDIENTE_ELIMINAR' ORDER BY fechaCreacion DESC")
    fun observarTodos(): Flow<List<RecordatorioEntity>>

    /** Filtro por prioridad (clave: ALTA / MEDIA / BAJA). */
    @Query(
        "SELECT * FROM recordatorios WHERE prioridad = :prioridad " +
            "AND estadoSync != 'PENDIENTE_ELIMINAR' ORDER BY fechaCreacion DESC"
    )
    fun observarPorPrioridad(prioridad: String): Flow<List<RecordatorioEntity>>

    /** Filtro por estado del recordatorio (clave: PENDIENTE / EN_CURSO / COMPLETADO). */
    @Query(
        "SELECT * FROM recordatorios WHERE estado = :estado " +
            "AND estadoSync != 'PENDIENTE_ELIMINAR' ORDER BY fechaCreacion DESC"
    )
    fun observarPorEstado(estado: String): Flow<List<RecordatorioEntity>>

    @Query("SELECT * FROM recordatorios WHERE id = :id AND estadoSync != 'PENDIENTE_ELIMINAR'")
    fun observarPorId(id: String): Flow<RecordatorioEntity?>

    @Query("SELECT COUNT(*) FROM recordatorios WHERE estadoSync != 'SINCRONIZADO'")
    fun observarCantidadPendientes(): Flow<Int>

    // ---------- Consultas puntuales (repositorio / sincronización) ----------
    @Query("SELECT * FROM recordatorios WHERE id = :id")
    suspend fun obtenerPorId(id: String): RecordatorioEntity?

    /** Todo lo que el servidor todavía no confirmó (incluye ERROR, para reintentar). */
    @Query("SELECT * FROM recordatorios WHERE estadoSync != 'SINCRONIZADO' ORDER BY fechaActualizacion ASC")
    suspend fun obtenerPendientes(): List<RecordatorioEntity>

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

    @Query("DELETE FROM recordatorios WHERE id = :id")
    suspend fun eliminarPorId(id: String): Int

    /**
     * Actualiza el estado de sincronización de LA MISMA fila, solo si no fue
     * modificada mientras viajaba al servidor (misma fechaActualizacion).
     * Devuelve la cantidad de filas actualizadas (0 = hubo un cambio local más nuevo).
     */
    @Query(
        "UPDATE recordatorios SET estadoSync = :estadoSync, errorSync = :errorSync " +
            "WHERE id = :id AND fechaActualizacion = :fechaActualizacion"
    )
    suspend fun marcarEstadoSync(
        id: String,
        fechaActualizacion: Long,
        estadoSync: EstadoSync,
        errorSync: String?
    ): Int

    /** Solo para la prueba de sincronización concurrente del menú de pruebas. */
    @Query("UPDATE recordatorios SET estadoSync = 'PENDIENTE_ACTUALIZAR' WHERE estadoSync = 'SINCRONIZADO'")
    suspend fun marcarSincronizadosComoPendientes(): Int
}

package com.example.recordatorios.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.example.recordatorios.model.EstadoSync

/**
 * Esquema de Room (tabla "recordatorios"). Es un modelo SOLO de la capa de datos:
 * nunca llega a las funciones componibles.
 *  - id: UUID generado en el dispositivo -> el mismo id del documento en Firestore.
 *  - uidPropietario: uid de la cuenta dueña del registro. Todas las consultas filtran
 *    por él (defensa adicional: una cuenta nunca lee filas de otra).
 *  - prioridad / estado: se guardan como String usando la `clave` del dominio.
 *  - estadoSync: enum convertido explícitamente con [ConvertidoresRoom].
 */
@Entity(
    tableName = "recordatorios",
    indices = [Index("estadoSync"), Index("prioridad"), Index("uidPropietario")]
)
data class RecordatorioEntity(
    @PrimaryKey val id: String,
    // DEFAULT '' coincide con la columna que agrega la migración 1 -> 2
    @ColumnInfo(defaultValue = "''") val uidPropietario: String,
    val titulo: String,
    val descripcion: String?,
    val categoriaId: Int,
    val categoriaNombre: String,
    val prioridad: String,
    val estado: String,
    val ubicacionNombre: String?,
    val ubicacionLatitud: Double?,
    val ubicacionLongitud: Double?,
    val avisoMinutosAntes: Int?,
    val fechaCreacion: Long,
    val fechaActualizacion: Long,
    val estadoSync: EstadoSync,
    val errorSync: String?
)

/** Conversión EXPLÍCITA del enum de sincronización: se guarda su nombre como texto. */
class ConvertidoresRoom {
    @TypeConverter
    fun estadoSyncATexto(estado: EstadoSync): String = estado.name

    /** Un valor desconocido (por ejemplo, de una versión futura) se trata como ERROR para reintentarlo. */
    @TypeConverter
    fun textoAEstadoSync(texto: String): EstadoSync =
        EstadoSync.entries.firstOrNull { it.name == texto } ?: EstadoSync.ERROR
}

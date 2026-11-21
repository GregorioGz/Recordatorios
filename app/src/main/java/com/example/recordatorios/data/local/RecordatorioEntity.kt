package com.example.recordatorios.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.example.recordatorios.model.EstadoSync

/**
 * Esquema de Room (tabla "recordatorios"). Es un modelo SOLO de la capa de datos:
 * nunca llega a las funciones componibles.
 *  - id: UUID generado en el dispositivo -> estable entre Room y el servidor.
 *  - prioridad / estado: se guardan como String usando la `clave` del dominio.
 *  - estadoSync: enum convertido explícitamente con [ConvertidoresRoom].
 */
@Entity(
    tableName = "recordatorios",
    indices = [Index("estadoSync"), Index("prioridad")]
)
data class RecordatorioEntity(
    @PrimaryKey val id: String,
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

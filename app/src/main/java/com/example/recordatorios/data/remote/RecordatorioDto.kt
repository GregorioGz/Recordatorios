package com.example.recordatorios.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO: representa el CONTRATO JSON del servicio remoto (nombres en snake_case).
 * Solo se usa en la capa de datos; se transforma en Entity antes de llegar a Room
 * y nunca llega a las funciones componibles.
 */
@Serializable
data class RecordatorioDto(
    @SerialName("id") val id: String,
    @SerialName("titulo") val titulo: String,
    @SerialName("descripcion") val descripcion: String? = null,
    @SerialName("categoria_id") val categoriaId: Int,
    @SerialName("categoria_nombre") val categoriaNombre: String,
    @SerialName("prioridad") val prioridad: String,
    @SerialName("estado") val estado: String,
    @SerialName("ubicacion_nombre") val ubicacionNombre: String? = null,
    @SerialName("ubicacion_latitud") val ubicacionLatitud: Double? = null,
    @SerialName("ubicacion_longitud") val ubicacionLongitud: Double? = null,
    @SerialName("aviso_minutos_antes") val avisoMinutosAntes: Int? = null,
    @SerialName("fecha_creacion") val fechaCreacion: Long,
    @SerialName("fecha_actualizacion") val fechaActualizacion: Long
)

/** Cuerpo de error que devuelve el servicio (400 / 500). */
@Serializable
data class ErrorDto(
    @SerialName("error") val error: String
)

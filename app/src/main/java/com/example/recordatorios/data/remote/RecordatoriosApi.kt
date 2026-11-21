package com.example.recordatorios.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Interfaz Retrofit del servicio remoto. Todas las funciones son suspend y
 * devuelven Response<...> para poder leer el código HTTP: la interpretación
 * se hace en un único lugar ([llamarApi]), nunca en las pantallas.
 */
interface RecordatoriosApi {

    @GET("recordatorios")
    suspend fun obtenerTodos(): Response<List<RecordatorioDto>>

    /**
     * Crear O actualizar (upsert idempotente): el id lo genera el dispositivo
     * y el servidor lo usa como clave. Reenviar el mismo PUT no duplica nada.
     * 201 = creado, 200 = actualizado.
     */
    @PUT("recordatorios/{id}")
    suspend fun guardar(
        @Path("id") id: String,
        @Body recordatorio: RecordatorioDto
    ): Response<RecordatorioDto>

    /** 204 = eliminado; 404 = no existía (también se considera confirmado). */
    @DELETE("recordatorios/{id}")
    suspend fun eliminar(@Path("id") id: String): Response<Unit>
}

package com.example.recordatorios.data

import com.example.recordatorios.data.local.RecordatorioEntity
import com.example.recordatorios.data.remote.RecordatorioDto
import com.example.recordatorios.model.BorradorRecordatorio
import com.example.recordatorios.model.EstadoRecordatorio
import com.example.recordatorios.model.EstadoSync
import com.example.recordatorios.model.Prioridad
import com.example.recordatorios.model.Recordatorio
import com.example.recordatorios.model.Ubicacion
import com.example.recordatorios.model.Validador

/**
 * Funciones de mapeo EXPLÍCITAS entre las capas:
 *   Entity (Room)  -> Dominio
 *   Dominio        -> Entity
 *   Borrador       -> Entity (nuevo o editado)
 *   Entity         -> DTO  (envío al servidor)
 *   DTO            -> Entity (descarga desde el servidor)
 */

// ---------- Entity -> Dominio ----------
fun RecordatorioEntity.aDominio(): Recordatorio = Recordatorio(
    id = id,
    titulo = titulo,
    categoria = DatosSimulados.categoriaPara(categoriaId, categoriaNombre),
    // Clave desconocida -> valor predeterminado (nunca rompe la lectura)
    prioridad = Prioridad.desdeClave(prioridad) ?: Prioridad.Media,
    estado = EstadoRecordatorio.desdeClave(estado) ?: EstadoRecordatorio.Pendiente,
    descripcion = descripcion,
    ubicacion = if (ubicacionNombre != null && ubicacionLatitud != null && ubicacionLongitud != null) {
        Ubicacion(ubicacionNombre, ubicacionLatitud, ubicacionLongitud)
    } else null,
    avisoMinutosAntes = avisoMinutosAntes,
    fechaCreacion = fechaCreacion,
    fechaActualizacion = fechaActualizacion,
    estadoSync = estadoSync,
    errorSync = errorSync
)

// ---------- Dominio -> Entity ----------
fun Recordatorio.aEntity(): RecordatorioEntity = RecordatorioEntity(
    id = id,
    titulo = titulo,
    descripcion = descripcion,
    categoriaId = categoria.id,
    categoriaNombre = categoria.nombre,
    prioridad = prioridad.clave,
    estado = estado.clave,
    ubicacionNombre = ubicacion?.nombre,
    ubicacionLatitud = ubicacion?.latitud,
    ubicacionLongitud = ubicacion?.longitud,
    avisoMinutosAntes = avisoMinutosAntes,
    fechaCreacion = fechaCreacion,
    fechaActualizacion = fechaActualizacion,
    estadoSync = estadoSync,
    errorSync = errorSync
)

// ---------- Borrador -> Entity ----------
/** Recordatorio NUEVO: id generado en el dispositivo y pendiente de crear en el servidor. */
fun BorradorRecordatorio.aEntityNueva(id: String, ahora: Long): RecordatorioEntity = RecordatorioEntity(
    id = id,
    titulo = titulo,
    descripcion = descripcion,
    categoriaId = categoria.id,
    categoriaNombre = categoria.nombre,
    prioridad = prioridad.clave,
    estado = estado.clave,
    ubicacionNombre = null,
    ubicacionLatitud = null,
    ubicacionLongitud = null,
    avisoMinutosAntes = null,
    fechaCreacion = ahora,
    fechaActualizacion = ahora,
    estadoSync = EstadoSync.PENDIENTE_CREAR,
    errorSync = null
)

/** Aplica los cambios del formulario sobre la MISMA fila (mismo id, misma fecha de creación). */
fun RecordatorioEntity.conCambios(borrador: BorradorRecordatorio, ahora: Long): RecordatorioEntity = copy(
    titulo = borrador.titulo,
    descripcion = borrador.descripcion,
    categoriaId = borrador.categoria.id,
    categoriaNombre = borrador.categoria.nombre,
    prioridad = borrador.prioridad.clave,
    estado = borrador.estado.clave,
    fechaActualizacion = nuevaFechaActualizacion(ahora),
    estadoSync = estadoSync.trasModificacionLocal(),
    errorSync = null
)

/** La fecha de actualización siempre avanza, aunque el reloj no cambie (sirve como versión). */
fun RecordatorioEntity.nuevaFechaActualizacion(ahora: Long): Long = maxOf(ahora, fechaActualizacion + 1)

/**
 * Transición del estado de sincronización ante una edición local:
 * si el servidor todavía no lo conoce, sigue siendo una creación pendiente;
 * en cualquier otro caso pasa a actualización pendiente.
 */
fun EstadoSync.trasModificacionLocal(): EstadoSync =
    if (this == EstadoSync.PENDIENTE_CREAR) EstadoSync.PENDIENTE_CREAR else EstadoSync.PENDIENTE_ACTUALIZAR

// ---------- Entity -> DTO (lo que se envía al servidor) ----------
fun RecordatorioEntity.aDto(): RecordatorioDto = RecordatorioDto(
    id = id,
    titulo = titulo,
    descripcion = descripcion,
    categoriaId = categoriaId,
    categoriaNombre = categoriaNombre,
    prioridad = prioridad,
    estado = estado,
    ubicacionNombre = ubicacionNombre,
    ubicacionLatitud = ubicacionLatitud,
    ubicacionLongitud = ubicacionLongitud,
    avisoMinutosAntes = avisoMinutosAntes,
    fechaCreacion = fechaCreacion,
    fechaActualizacion = fechaActualizacion
)

// ---------- DTO -> Entity (lo que llega del servidor) ----------
/** Lo que llega del servidor ya está confirmado: se guarda como SINCRONIZADO. */
fun RecordatorioDto.aEntity(): RecordatorioEntity = RecordatorioEntity(
    id = id,
    titulo = titulo,
    descripcion = descripcion,
    categoriaId = categoriaId,
    categoriaNombre = categoriaNombre,
    // Claves desconocidas se normalizan al valor predeterminado antes de guardar
    prioridad = (Prioridad.desdeClave(prioridad) ?: Prioridad.Media).clave,
    estado = (EstadoRecordatorio.desdeClave(estado) ?: EstadoRecordatorio.Pendiente).clave,
    ubicacionNombre = ubicacionNombre,
    ubicacionLatitud = ubicacionLatitud,
    ubicacionLongitud = ubicacionLongitud,
    avisoMinutosAntes = avisoMinutosAntes?.takeIf { it > 0 },
    fechaCreacion = fechaCreacion,
    fechaActualizacion = fechaActualizacion,
    estadoSync = EstadoSync.SINCRONIZADO,
    errorSync = null
)

/**
 * Un DTO que no respeta las reglas del dominio se descarta al descargar:
 * así un dato remoto mal formado nunca rompe la lectura de Room.
 */
fun RecordatorioDto.esValido(): Boolean =
    id.isNotBlank() &&
        Validador.validarTitulo(titulo) == null &&
        titulo.length <= Validador.MAX_TITULO &&
        (descripcion == null || Validador.validarDescripcion(descripcion) == null)

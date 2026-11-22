package com.example.recordatorios.data

import com.example.recordatorios.data.local.RecordatorioEntity
import com.example.recordatorios.data.remote.DocumentoRemoto
import com.example.recordatorios.model.BorradorRecordatorio
import com.example.recordatorios.model.EstadoRecordatorio
import com.example.recordatorios.model.EstadoSync
import com.example.recordatorios.model.Prioridad
import com.example.recordatorios.model.Recordatorio
import com.example.recordatorios.model.Ubicacion
import com.example.recordatorios.model.Validador

/**
 * Funciones de mapeo EXPLÍCITAS entre las capas:
 *   Entity (Room)        -> Dominio
 *   Dominio              -> Entity
 *   Borrador             -> Entity (nuevo o editado)
 *   Entity               -> Map de Firestore (envío)
 *   Documento Firestore  -> Entity (descarga, con validación)
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
fun Recordatorio.aEntity(uidPropietario: String): RecordatorioEntity = RecordatorioEntity(
    id = id,
    uidPropietario = uidPropietario,
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
/** Recordatorio NUEVO: id generado en el dispositivo, de la cuenta activa y pendiente de crear. */
fun BorradorRecordatorio.aEntityNueva(id: String, uidPropietario: String, ahora: Long): RecordatorioEntity =
    RecordatorioEntity(
        id = id,
        uidPropietario = uidPropietario,
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
 * si la nube todavía no lo conoce, sigue siendo una creación pendiente;
 * en cualquier otro caso pasa a actualización pendiente.
 */
fun EstadoSync.trasModificacionLocal(): EstadoSync =
    if (this == EstadoSync.PENDIENTE_CREAR) EstadoSync.PENDIENTE_CREAR else EstadoSync.PENDIENTE_ACTUALIZAR

// ---------- Firestore ----------
/** Nombres de los campos del documento users/{uid}/reminders/{id}. */
object CamposFirestore {
    const val ID = "id"
    const val TITULO = "titulo"
    const val DESCRIPCION = "descripcion"
    const val CATEGORIA_ID = "categoriaId"
    const val CATEGORIA_NOMBRE = "categoriaNombre"
    const val PRIORIDAD = "prioridad"
    const val ESTADO = "estado"
    const val FECHA_CREACION = "fechaCreacion"
    const val FECHA_ACTUALIZACION = "fechaActualizacion"
}

/**
 * Entity -> Map que se escribe en Firestore. Sin uid (está en la ruta) ni estado de
 * sincronización (es información local del dispositivo).
 */
fun RecordatorioEntity.aMapaFirestore(): Map<String, Any?> = mapOf(
    CamposFirestore.ID to id,
    CamposFirestore.TITULO to titulo,
    CamposFirestore.DESCRIPCION to descripcion,
    CamposFirestore.CATEGORIA_ID to categoriaId.toLong(),
    CamposFirestore.CATEGORIA_NOMBRE to categoriaNombre,
    CamposFirestore.PRIORIDAD to prioridad,
    CamposFirestore.ESTADO to estado,
    CamposFirestore.FECHA_CREACION to fechaCreacion,
    CamposFirestore.FECHA_ACTUALIZACION to fechaActualizacion
)

/**
 * Documento de Firestore -> Entity de la cuenta [uidPropietario], ya confirmado (SINCRONIZADO).
 * Devuelve null si el documento no respeta las reglas del dominio: los inválidos se descartan
 * y nunca rompen la lectura de Room.
 */
fun DocumentoRemoto.aEntity(uidPropietario: String): RecordatorioEntity? {
    if (id.isBlank() || datos[CamposFirestore.ID] != id) return null
    val titulo = datos[CamposFirestore.TITULO] as? String ?: return null
    if (Validador.validarTitulo(titulo) != null || titulo.length > Validador.MAX_TITULO) return null
    val descripcion = when (val valor = datos[CamposFirestore.DESCRIPCION]) {
        null -> null
        is String -> valor.takeIf { Validador.validarDescripcion(it) == null } ?: return null
        else -> return null
    }
    val categoriaId = (datos[CamposFirestore.CATEGORIA_ID] as? Number)?.toInt() ?: return null
    val categoriaNombre = datos[CamposFirestore.CATEGORIA_NOMBRE] as? String ?: return null
    val prioridad = Prioridad.desdeClave(datos[CamposFirestore.PRIORIDAD] as? String) ?: return null
    val estado = EstadoRecordatorio.desdeClave(datos[CamposFirestore.ESTADO] as? String) ?: return null
    val fechaCreacion = (datos[CamposFirestore.FECHA_CREACION] as? Number)?.toLong() ?: return null
    val fechaActualizacion = (datos[CamposFirestore.FECHA_ACTUALIZACION] as? Number)?.toLong() ?: return null

    return RecordatorioEntity(
        id = id,
        uidPropietario = uidPropietario,
        titulo = titulo,
        descripcion = descripcion,
        categoriaId = categoriaId,
        categoriaNombre = categoriaNombre,
        prioridad = prioridad.clave,
        estado = estado.clave,
        ubicacionNombre = null,
        ubicacionLatitud = null,
        ubicacionLongitud = null,
        avisoMinutosAntes = null,
        fechaCreacion = fechaCreacion,
        fechaActualizacion = fechaActualizacion,
        estadoSync = EstadoSync.SINCRONIZADO,
        errorSync = null
    )
}

/** Los datos que solo existen en el dispositivo (ubicación, aviso) se conservan al aplicar la versión remota. */
fun RecordatorioEntity.conservandoDatosLocalesDe(local: RecordatorioEntity?): RecordatorioEntity =
    if (local == null) this else copy(
        ubicacionNombre = local.ubicacionNombre,
        ubicacionLatitud = local.ubicacionLatitud,
        ubicacionLongitud = local.ubicacionLongitud,
        avisoMinutosAntes = local.avisoMinutosAntes
    )

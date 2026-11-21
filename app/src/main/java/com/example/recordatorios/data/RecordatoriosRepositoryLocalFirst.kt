package com.example.recordatorios.data

import com.example.recordatorios.data.local.RecordatorioDao
import com.example.recordatorios.data.local.RecordatorioEntity
import com.example.recordatorios.data.remote.EscenarioRemoto
import com.example.recordatorios.data.remote.RecordatorioDto
import com.example.recordatorios.data.remote.RecordatoriosApi
import com.example.recordatorios.data.remote.ResultadoRemoto
import com.example.recordatorios.data.remote.ServidorSimuladoInterceptor
import com.example.recordatorios.data.remote.esTransitorio
import com.example.recordatorios.data.remote.llamarApi
import com.example.recordatorios.data.remote.mensajeParaUsuario
import com.example.recordatorios.model.BorradorRecordatorio
import com.example.recordatorios.model.Categoria
import com.example.recordatorios.model.EstadoRecordatorio
import com.example.recordatorios.model.EstadoSincronizacion
import com.example.recordatorios.model.EstadoSync
import com.example.recordatorios.model.Prioridad
import com.example.recordatorios.model.Recordatorio
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Repositorio LOCAL-FIRST: coordina Room (fuente de verdad) y el servicio remoto.
 *
 * - Lecturas: siempre desde Room (Flow), funcionen o no la red y el servidor.
 * - Escrituras: primero en Room, marcadas como pendientes; después se sincroniza
 *   en segundo plano (sin bloquear la interfaz).
 * - Sincronización: un Mutex garantiza que nunca corran dos a la vez; el envío es un
 *   PUT idempotente por UUID y la confirmación actualiza LA MISMA fila.
 * - Errores remotos: se interpretan en [llamarApi]; los datos locales nunca se pierden.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecordatoriosRepositoryLocalFirst(
    private val dao: RecordatorioDao,
    private val api: RecordatoriosApi,
    private val alcanceExterno: CoroutineScope,
    private val servidorSimulado: ServidorSimuladoInterceptor? = null,
    private val reloj: () -> Long = System::currentTimeMillis,
    private val generarId: () -> String = { UUID.randomUUID().toString() }
) : RecordatoriosRepository, SimuladorDeEscenarios {

    /** Solo UNA sincronización a la vez: los demás disparadores esperan su turno. */
    private val mutexSincronizacion = Mutex()

    /** Serializa las escrituras locales (usuario y sincronización) para que no se pisen. */
    private val mutexEscritura = Mutex()

    // ---------- Simulaciones locales (menú de pruebas) ----------
    private val listaVaciaSimulada = MutableStateFlow(false)
    private val fallarProximaLectura = AtomicBoolean(false)
    private val fallarProximoGuardado = AtomicBoolean(false)

    // ---------- Estado global de sincronización ----------
    private val hayConexion = MutableStateFlow(true)
    private val sincronizando = MutableStateFlow(false)
    private val ultimoError = MutableStateFlow<String?>(null)

    override val estadoSincronizacion: StateFlow<EstadoSincronizacion> = combine(
        hayConexion, sincronizando, dao.observarCantidadPendientes(), ultimoError
    ) { conexion, enCurso, pendientes, error ->
        EstadoSincronizacion(conexion, enCurso, pendientes, error)
    }.stateIn(alcanceExterno, SharingStarted.WhileSubscribed(5_000), EstadoSincronizacion())

    /**
     * Escucha la conectividad: cada vez que el dispositivo pasa a "conectado"
     * (también al iniciar la app) se dispara una sincronización.
     */
    fun observarConectividad(conectividad: Flow<Boolean>) {
        alcanceExterno.launch {
            var anterior: Boolean? = null
            conectividad.collect { conectado ->
                hayConexion.value = conectado
                if (conectado && anterior != true) sincronizarEnSegundoPlano()
                anterior = conectado
            }
        }
    }

    // ---------- Lecturas (Room) ----------
    override fun observarRecordatorios(prioridad: Prioridad?): Flow<List<Recordatorio>> = flow {
        if (fallarProximaLectura.getAndSet(false)) {
            throw IOException("Error simulado al leer la base de datos local")
        }
        val desdeRoom = if (prioridad == null) dao.observarTodos() else dao.observarPorPrioridad(prioridad.clave)
        emitAll(
            listaVaciaSimulada.flatMapLatest { vacia ->
                if (vacia) flowOf(emptyList()) else desdeRoom.map { lista -> lista.map { it.aDominio() } }
            }
        )
    }

    override fun observarRecordatorio(id: String): Flow<Recordatorio?> =
        dao.observarPorId(id).map { it?.aDominio() }

    // ---------- Escrituras (primero Room, después sincronización) ----------
    override suspend fun crear(borrador: BorradorRecordatorio): Recordatorio {
        if (fallarProximoGuardado.getAndSet(false)) {
            throw IllegalStateException("No se pudo guardar el recordatorio. Intentá nuevamente.")
        }
        val entidad = borrador.aEntityNueva(id = generarId(), ahora = reloj())
        mutexEscritura.withLock { dao.insertar(entidad) }
        sincronizarEnSegundoPlano()
        return entidad.aDominio()
    }

    override suspend fun actualizar(id: String, borrador: BorradorRecordatorio): Recordatorio {
        if (fallarProximoGuardado.getAndSet(false)) {
            throw IllegalStateException("No se pudieron guardar los cambios. Intentá nuevamente.")
        }
        val actualizado = mutexEscritura.withLock {
            val actual = obtenerVisible(id)
            actual.conCambios(borrador, reloj()).also { dao.actualizar(it) }
        }
        sincronizarEnSegundoPlano()
        return actualizado.aDominio()
    }

    override suspend fun cambiarEstado(id: String, estado: EstadoRecordatorio) {
        mutexEscritura.withLock {
            val actual = obtenerVisible(id)
            if (actual.estado == estado.clave) return
            dao.actualizar(
                actual.copy(
                    estado = estado.clave,
                    fechaActualizacion = actual.nuevaFechaActualizacion(reloj()),
                    estadoSync = actual.estadoSync.trasModificacionLocal(),
                    errorSync = null
                )
            )
        }
        sincronizarEnSegundoPlano()
    }

    override suspend fun eliminar(id: String) {
        mutexEscritura.withLock {
            val actual = dao.obtenerPorId(id) ?: return
            // Si nunca llegó al servidor (y no hay un envío en curso que pueda estar creándolo)
            // se borra directamente; si no, se marca y se borra cuando el servidor confirme.
            if (actual.estadoSync == EstadoSync.PENDIENTE_CREAR && !mutexSincronizacion.isLocked) {
                dao.eliminarPorId(id)
            } else {
                dao.actualizar(
                    actual.copy(
                        fechaActualizacion = actual.nuevaFechaActualizacion(reloj()),
                        estadoSync = EstadoSync.PENDIENTE_ELIMINAR,
                        errorSync = null
                    )
                )
            }
        }
        sincronizarEnSegundoPlano()
    }

    private suspend fun obtenerVisible(id: String): RecordatorioEntity =
        dao.obtenerPorId(id)?.takeIf { it.estadoSync != EstadoSync.PENDIENTE_ELIMINAR }
            ?: throw NoSuchElementException("El recordatorio ya no existe.")

    // ---------- Sincronización ----------
    override suspend fun sincronizar() {
        mutexSincronizacion.withLock {
            if (!hayConexion.value) {
                ultimoError.value = ResultadoRemoto.SinConexion.mensajeParaUsuario()
                return
            }
            sincronizando.value = true
            try {
                var problema: ResultadoRemoto<*>? = null

                // 1) Subida: cada pendiente se envía UNA vez por sincronización
                for (pendiente in dao.obtenerPendientes()) {
                    val resultado = if (pendiente.estadoSync == EstadoSync.PENDIENTE_ELIMINAR) {
                        enviarEliminacion(pendiente)
                    } else {
                        enviarGuardado(pendiente)
                    }
                    if (resultado !is ResultadoRemoto.Exito) {
                        if (problema == null) problema = resultado
                        if (resultado.esTransitorio) break // el servidor o la red no responden: se corta
                    }
                }

                // 2) Descarga: solo si la subida no se cortó por un problema transitorio
                if (problema?.esTransitorio != true) {
                    when (val descarga = llamarApi { api.obtenerTodos() }) {
                        is ResultadoRemoto.Exito -> aplicarDescarga(descarga.datos.orEmpty())
                        else -> if (problema == null) problema = descarga
                    }
                }
                ultimoError.value = problema?.mensajeParaUsuario()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Errores de almacenamiento local: se informan, los datos no se tocan
                ultimoError.value = "No se pudo completar la sincronización. Reintentá más tarde."
            } finally {
                sincronizando.value = false
            }
        }
    }

    /** PUT idempotente; la respuesta actualiza el estado de la MISMA fila (nunca inserta otra). */
    private suspend fun enviarGuardado(entidad: RecordatorioEntity): ResultadoRemoto<*> {
        val resultado = llamarApi { api.guardar(entidad.id, entidad.aDto()) }
        val (nuevoEstado, mensaje) = when (resultado) {
            is ResultadoRemoto.Exito -> EstadoSync.SINCRONIZADO to null
            // Problemas de red: sigue pendiente y se reintenta solo al recuperar la conexión
            ResultadoRemoto.SinConexion, ResultadoRemoto.Timeout ->
                entidad.estadoSync to resultado.mensajeParaUsuario()
            // El servidor respondió con error: queda en ERROR, con opción de reintentar
            else -> EstadoSync.ERROR to resultado.mensajeParaUsuario()
        }
        mutexEscritura.withLock {
            // Si la fila cambió mientras viajaba (otra edición), no se marca como sincronizada:
            // conserva su estado pendiente y se enviará en la próxima vuelta.
            dao.marcarEstadoSync(entidad.id, entidad.fechaActualizacion, nuevoEstado, mensaje)
        }
        return resultado
    }

    /** DELETE: 204 o 404 confirman que el servidor ya no lo tiene -> se borra de Room. */
    private suspend fun enviarEliminacion(entidad: RecordatorioEntity): ResultadoRemoto<*> {
        val resultado = llamarApi { api.eliminar(entidad.id) }
        val confirmado = resultado is ResultadoRemoto.Exito ||
            (resultado is ResultadoRemoto.SolicitudInvalida && resultado.codigo == 404)
        mutexEscritura.withLock {
            if (confirmado) {
                if (dao.obtenerPorId(entidad.id)?.estadoSync == EstadoSync.PENDIENTE_ELIMINAR) {
                    dao.eliminarPorId(entidad.id)
                }
            } else {
                // Sigue oculto y pendiente de eliminar: se reintenta en la próxima sincronización
                dao.marcarEstadoSync(
                    entidad.id, entidad.fechaActualizacion,
                    EstadoSync.PENDIENTE_ELIMINAR, resultado.mensajeParaUsuario()
                )
            }
        }
        return if (confirmado) ResultadoRemoto.Exito(Unit) else resultado
    }

    /**
     * Upsert por id de lo que tiene el servidor, SIN pisar los registros que tienen
     * cambios locales pendientes (esos ganan hasta que se envíen).
     */
    private suspend fun aplicarDescarga(remotos: List<RecordatorioDto>) {
        mutexEscritura.withLock {
            remotos.filter { it.esValido() }.forEach { dto ->
                val local = dao.obtenerPorId(dto.id)
                if (local == null || local.estadoSync == EstadoSync.SINCRONIZADO) {
                    dao.upsert(dto.aEntity())
                }
            }
        }
    }

    override fun sincronizarEnSegundoPlano() {
        alcanceExterno.launch { sincronizar() }
    }

    override fun categoriasDisponibles(): Set<Categoria> = DatosSimulados.categorias

    // ---------- Simulador (menú de pruebas) ----------
    override fun simularListaVacia() { listaVaciaSimulada.value = true }
    override fun simularErrorEnProximaLectura() = fallarProximaLectura.set(true)
    override fun simularErrorEnProximoGuardado() = fallarProximoGuardado.set(true)

    override suspend fun restaurarDatosDeEjemplo() {
        listaVaciaSimulada.value = false
        fallarProximaLectura.set(false)
        fallarProximoGuardado.set(false)
        mutexEscritura.withLock {
            DatosSimulados.recordatorios.forEach {
                dao.insertarSiNoExiste(it.aEntity().copy(estadoSync = EstadoSync.PENDIENTE_CREAR))
            }
        }
        sincronizarEnSegundoPlano()
    }

    override val escenarioRemoto: StateFlow<EscenarioRemoto?> =
        servidorSimulado?.escenario ?: MutableStateFlow<EscenarioRemoto?>(null).asStateFlow()

    override fun cambiarEscenarioRemoto(escenario: EscenarioRemoto): Boolean {
        val servidor = servidorSimulado ?: return false
        servidor.cambiarEscenario(escenario)
        return true
    }

    override suspend fun probarSincronizacionConcurrente(cantidad: Int): String {
        val servidor = servidorSimulado ?: return "Esta prueba solo está disponible con el servidor simulado."
        if (!hayConexion.value) return "Sin conexión: conectate a una red para hacer la prueba."
        mutexEscritura.withLock { dao.marcarSincronizadosComoPendientes() }
        val pendientes = dao.obtenerPendientes().count { it.estadoSync != EstadoSync.PENDIENTE_ELIMINAR }
        servidor.reiniciarContadorDePut()

        // Varios disparadores al mismo tiempo (como al recuperar la red)
        coroutineScope { repeat(cantidad) { launch { sincronizar() } } }

        return "$cantidad sincronizaciones simultáneas · $pendientes pendientes · " +
            "${servidor.cantidadDePutRecibidos()} PUT recibidos · " +
            "${servidor.cantidadDeRegistros()} registros en el servidor"
    }
}

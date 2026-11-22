package com.example.recordatorios.data

import com.example.recordatorios.data.auth.AuthRepository
import com.example.recordatorios.data.local.RecordatorioDao
import com.example.recordatorios.data.local.RecordatorioEntity
import com.example.recordatorios.data.remote.CambioRemoto
import com.example.recordatorios.data.remote.DocumentoRemoto
import com.example.recordatorios.data.remote.FuenteRemotaRecordatorios
import com.example.recordatorios.data.remote.PruebaDeAutorizacion
import com.example.recordatorios.data.remote.ResultadoRemoto
import com.example.recordatorios.data.remote.esTransitorio
import com.example.recordatorios.data.remote.mensajeParaUsuario
import com.example.recordatorios.model.BorradorRecordatorio
import com.example.recordatorios.model.Categoria
import com.example.recordatorios.model.EstadoRecordatorio
import com.example.recordatorios.model.EstadoSesion
import com.example.recordatorios.model.EstadoSincronizacion
import com.example.recordatorios.model.EstadoSync
import com.example.recordatorios.model.Prioridad
import com.example.recordatorios.model.Recordatorio
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
import java.util.concurrent.atomic.AtomicInteger

/**
 * Repositorio LOCAL-FIRST: coordina Room (fuente de verdad), Firestore y la sesión.
 *
 * - Sesión: el uid sale de [AuthRepository]. Todas las lecturas y escrituras de Room se
 *   limitan a ese uid; sin sesión no se expone nada. Al cerrar o invalidar la sesión se
 *   cancelan la sincronización y el listener y se borran de Room los datos de esa cuenta.
 * - Lecturas: siempre desde Room (Flow), funcionen o no la red y la nube.
 * - Escrituras: primero en Room, marcadas como pendientes; después se sincroniza
 *   en segundo plano (sin bloquear la interfaz).
 * - Sincronización: un Mutex garantiza que nunca corran dos a la vez; el envío es un
 *   set() idempotente sobre el mismo UUID y la confirmación actualiza LA MISMA fila.
 * - Errores remotos: llegan ya interpretados como [ResultadoRemoto]; los datos locales nunca se pierden.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecordatoriosRepositoryLocalFirst(
    private val dao: RecordatorioDao,
    private val remoto: FuenteRemotaRecordatorios,
    private val auth: AuthRepository,
    private val alcanceExterno: CoroutineScope,
    private val pruebaAutorizacion: PruebaDeAutorizacion? = null,
    private val escucharEnTiempoReal: Boolean = true,
    private val reloj: () -> Long = System::currentTimeMillis,
    private val generarId: () -> String = { UUID.randomUUID().toString() }
) : RecordatoriosRepository, SimuladorDeEscenarios {

    /** Solo UNA sincronización a la vez: los demás disparadores esperan su turno. */
    private val mutexSincronizacion = Mutex()

    /** Serializa las escrituras locales (usuario, sincronización, limpieza) para que no se pisen. */
    private val mutexEscritura = Mutex()

    // ---------- Simulaciones locales (menú de pruebas) ----------
    private val listaVaciaSimulada = MutableStateFlow(false)
    private val fallarProximaLectura = AtomicBoolean(false)
    private val fallarProximoGuardado = AtomicBoolean(false)
    private val escriturasRemotas = AtomicInteger(0)

    // ---------- Sesión ----------
    /** uid de la cuenta activa; null = sin sesión (no se expone ningún dato). */
    private val uidActivo = MutableStateFlow<String?>(null)

    /** Trabajos ligados a la sesión (sincronizaciones, listener): se cancelan al cerrarla. */
    private var trabajosDeSesion: Job = SupervisorJob(alcanceExterno.coroutineContext[Job])
    private val alcanceSesion: CoroutineScope
        get() = CoroutineScope(alcanceExterno.coroutineContext + trabajosDeSesion)

    /** La primera descarga de cada sesión también quita lo que la nube ya no tiene. */
    private val descargaInicialHecha = AtomicBoolean(false)
    private val escuchandoCambios = AtomicBoolean(false)

    // ---------- Estado global de sincronización ----------
    private val hayConexion = MutableStateFlow(true)
    private val sincronizando = MutableStateFlow(false)
    private val ultimoError = MutableStateFlow<String?>(null)

    override val estadoSincronizacion: StateFlow<EstadoSincronizacion> = combine(
        hayConexion,
        sincronizando,
        uidActivo.flatMapLatest { uid -> if (uid == null) flowOf(0) else dao.observarCantidadPendientes(uid) },
        ultimoError
    ) { conexion, enCurso, pendientes, error ->
        EstadoSincronizacion(conexion, enCurso, pendientes, error)
    }.stateIn(alcanceExterno, SharingStarted.WhileSubscribed(5_000), EstadoSincronizacion())

    init {
        alcanceExterno.launch {
            auth.sesion.collect { sesion -> alCambiarSesion(sesion) }
        }
    }

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

    // ---------- Ciclo de vida de la sesión ----------
    private suspend fun alCambiarSesion(sesion: EstadoSesion) {
        when (sesion) {
            EstadoSesion.Verificando -> Unit
            is EstadoSesion.Activa -> {
                val uid = sesion.usuario.uid
                if (uid == uidActivo.value) return
                finalizarSesionLocal()
                iniciarSesionLocal(uid)
            }
            EstadoSesion.SinSesion, is EstadoSesion.Invalida -> finalizarSesionLocal()
        }
    }

    private suspend fun iniciarSesionLocal(uid: String) {
        // Defensa adicional: ninguna fila de otra cuenta (ni las migradas sin dueño) sobrevive
        mutexEscritura.withLock { dao.eliminarDeOtrosPropietarios(uid) }
        descargaInicialHecha.set(false)
        uidActivo.value = uid
        // Primera sincronización: sube lo pendiente y descarga users/{uid}/reminders
        sincronizarEnSegundoPlano()
    }

    /**
     * Cancela todo lo que trabaja para la sesión y borra sus datos de Room,
     * así la próxima cuenta nunca ve información de la anterior.
     */
    private suspend fun finalizarSesionLocal() {
        val uid = uidActivo.value ?: return
        uidActivo.value = null
        trabajosDeSesion.cancelAndJoin()
        trabajosDeSesion = SupervisorJob(alcanceExterno.coroutineContext[Job])
        mutexEscritura.withLock { dao.eliminarDePropietario(uid) }
        listaVaciaSimulada.value = false
        ultimoError.value = null
    }

    /** UNAUTHENTICATED de la nube: la sesión ya no sirve (solo si sigue siendo la misma cuenta). */
    private suspend fun alDetectarSesionInvalida(uid: String) {
        if (uidActivo.value == uid) auth.invalidarSesion(ResultadoRemoto.SesionInvalida.mensajeParaUsuario())
    }

    private fun uidRequerido(): String =
        uidActivo.value ?: throw IllegalStateException("No hay una sesión activa.")

    /** Ejecuta [bloque] con el lock de escritura solo si [uid] sigue siendo la cuenta activa. */
    private suspend inline fun escribirSiSigueActiva(uid: String, bloque: () -> Unit) {
        mutexEscritura.withLock { if (uidActivo.value == uid) bloque() }
    }

    // ---------- Lecturas (Room, solo de la cuenta activa) ----------
    override fun observarRecordatorios(prioridad: Prioridad?): Flow<List<Recordatorio>> = flow {
        if (fallarProximaLectura.getAndSet(false)) {
            throw IOException("Error simulado al leer la base de datos local")
        }
        emitAll(
            combine(uidActivo, listaVaciaSimulada) { uid, vacia -> uid.takeUnless { vacia } }
                .flatMapLatest { uid ->
                    when {
                        uid == null -> flowOf(emptyList())
                        prioridad == null -> dao.observarTodos(uid)
                        else -> dao.observarPorPrioridad(uid, prioridad.clave)
                    }.map { lista -> lista.map { it.aDominio() } }
                }
        )
    }

    override fun observarRecordatorio(id: String): Flow<Recordatorio?> =
        uidActivo.flatMapLatest { uid ->
            if (uid == null) flowOf(null) else dao.observarPorId(uid, id).map { it?.aDominio() }
        }

    // ---------- Escrituras (primero Room, después sincronización) ----------
    override suspend fun crear(borrador: BorradorRecordatorio): Recordatorio {
        if (fallarProximoGuardado.getAndSet(false)) {
            throw IllegalStateException("No se pudo guardar el recordatorio. Intentá nuevamente.")
        }
        val uid = uidRequerido()
        val entidad = borrador.aEntityNueva(id = generarId(), uidPropietario = uid, ahora = reloj())
        mutexEscritura.withLock {
            check(uidActivo.value == uid) { "La sesión cambió. Intentá nuevamente." }
            dao.insertar(entidad)
        }
        sincronizarEnSegundoPlano()
        return entidad.aDominio()
    }

    override suspend fun actualizar(id: String, borrador: BorradorRecordatorio): Recordatorio {
        if (fallarProximoGuardado.getAndSet(false)) {
            throw IllegalStateException("No se pudieron guardar los cambios. Intentá nuevamente.")
        }
        val uid = uidRequerido()
        val actualizado = mutexEscritura.withLock {
            val actual = obtenerVisible(uid, id)
            actual.conCambios(borrador, reloj()).also { dao.actualizar(it) }
        }
        sincronizarEnSegundoPlano()
        return actualizado.aDominio()
    }

    override suspend fun cambiarEstado(id: String, estado: EstadoRecordatorio) {
        val uid = uidRequerido()
        mutexEscritura.withLock {
            val actual = obtenerVisible(uid, id)
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
        val uid = uidRequerido()
        mutexEscritura.withLock {
            val actual = dao.obtenerPorId(uid, id) ?: return
            // Si nunca llegó a la nube (y no hay un envío en curso que pueda estar creándolo)
            // se borra directamente; si no, se marca y se borra cuando la nube confirme.
            if (actual.estadoSync == EstadoSync.PENDIENTE_CREAR && !mutexSincronizacion.isLocked) {
                dao.eliminarPorId(uid, id)
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

    /** Busca en la cuenta [uid]; una fila de otra cuenta es, para esta, inexistente. */
    private suspend fun obtenerVisible(uid: String, id: String): RecordatorioEntity =
        dao.obtenerPorId(uid, id)?.takeIf { it.estadoSync != EstadoSync.PENDIENTE_ELIMINAR }
            ?: throw NoSuchElementException("El recordatorio ya no existe.")

    // ---------- Sincronización ----------
    override suspend fun sincronizar() {
        mutexSincronizacion.withLock {
            val uid = uidActivo.value ?: return
            if (!hayConexion.value) {
                ultimoError.value = ResultadoRemoto.SinConexion.mensajeParaUsuario()
                return
            }
            sincronizando.value = true
            try {
                var problema: ResultadoRemoto<*>? = null

                // 1) Subida: cada pendiente se envía UNA vez por sincronización
                for (pendiente in dao.obtenerPendientes(uid)) {
                    val resultado = if (pendiente.estadoSync == EstadoSync.PENDIENTE_ELIMINAR) {
                        enviarEliminacion(uid, pendiente)
                    } else {
                        enviarGuardado(uid, pendiente)
                    }
                    if (resultado !is ResultadoRemoto.Exito) {
                        if (problema == null) problema = resultado
                        // La red, la nube o la sesión no responden: se corta y se reintenta después
                        if (resultado.esTransitorio || resultado == ResultadoRemoto.SesionInvalida) break
                    }
                }

                // 2) Descarga: solo si la subida no se cortó por un problema transitorio o de sesión
                if (problema?.esTransitorio != true && problema != ResultadoRemoto.SesionInvalida) {
                    when (val descarga = remoto.descargar(uid)) {
                        is ResultadoRemoto.Exito -> {
                            val inicial = !descargaInicialHecha.getAndSet(true)
                            aplicarDescarga(uid, descarga.datos, quitarAusentes = inicial)
                            iniciarEscuchaSiCorresponde(uid)
                        }
                        else -> if (problema == null) problema = descarga
                    }
                }
                if (problema == ResultadoRemoto.SesionInvalida) alDetectarSesionInvalida(uid)
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

    /** set() idempotente; la respuesta actualiza el estado de la MISMA fila (nunca inserta otra). */
    private suspend fun enviarGuardado(uid: String, entidad: RecordatorioEntity): ResultadoRemoto<*> {
        val resultado = remoto.guardar(uid, entidad.id, entidad.aMapaFirestore())
        escriturasRemotas.incrementAndGet()
        val (nuevoEstado, mensaje) = when (resultado) {
            is ResultadoRemoto.Exito -> EstadoSync.SINCRONIZADO to null
            // Red o sesión: sigue pendiente y se reintenta solo al recuperar la conexión
            ResultadoRemoto.SinConexion, ResultadoRemoto.Timeout, ResultadoRemoto.SesionInvalida ->
                entidad.estadoSync to resultado.mensajeParaUsuario()
            // La nube lo rechazó (PERMISSION_DENIED -> "Acceso denegado") o falló: ERROR, con reintento manual
            else -> EstadoSync.ERROR to resultado.mensajeParaUsuario()
        }
        escribirSiSigueActiva(uid) {
            // Si la fila cambió mientras viajaba (otra edición), no se marca como sincronizada:
            // conserva su estado pendiente y se enviará en la próxima vuelta.
            dao.marcarEstadoSync(uid, entidad.id, entidad.fechaActualizacion, nuevoEstado, mensaje)
        }
        return resultado
    }

    /** delete(): borrar en la nube un documento inexistente también confirma -> se borra de Room. */
    private suspend fun enviarEliminacion(uid: String, entidad: RecordatorioEntity): ResultadoRemoto<*> {
        val resultado = remoto.eliminar(uid, entidad.id)
        escribirSiSigueActiva(uid) {
            if (resultado is ResultadoRemoto.Exito) {
                if (dao.obtenerPorId(uid, entidad.id)?.estadoSync == EstadoSync.PENDIENTE_ELIMINAR) {
                    dao.eliminarPorId(uid, entidad.id)
                }
            } else {
                // Sigue oculto y pendiente de eliminar: se reintenta en la próxima sincronización
                dao.marcarEstadoSync(
                    uid, entidad.id, entidad.fechaActualizacion,
                    EstadoSync.PENDIENTE_ELIMINAR, resultado.mensajeParaUsuario()
                )
            }
        }
        return resultado
    }

    /**
     * Upsert por id de lo que tiene la nube, SIN pisar los registros con cambios locales
     * pendientes (esos ganan hasta que se envíen). Los documentos inválidos se descartan.
     * En la descarga inicial de la sesión, además, se quitan los sincronizados que la
     * nube ya no tiene (borrados desde otro dispositivo mientras la app estaba cerrada).
     */
    private suspend fun aplicarDescarga(uid: String, remotos: List<DocumentoRemoto>, quitarAusentes: Boolean) {
        escribirSiSigueActiva(uid) {
            remotos.forEach { documento -> aplicarDocumento(uid, documento) }
            if (quitarAusentes) {
                val idsRemotos = remotos.map { it.id }.toSet()
                dao.idsSincronizados(uid).filter { it !in idsRemotos }.forEach { dao.eliminarPorId(uid, it) }
            }
        }
    }

    private suspend fun aplicarDocumento(uid: String, documento: DocumentoRemoto) {
        val remota = documento.aEntity(uid) ?: return
        val local = dao.obtenerPorId(uid, remota.id)
        if (local == null || local.estadoSync == EstadoSync.SINCRONIZADO) {
            dao.upsert(remota.conservandoDatosLocalesDe(local))
        }
    }

    // ---------- Tiempo real (cambios hechos desde otro dispositivo) ----------
    private fun iniciarEscuchaSiCorresponde(uid: String) {
        if (!escucharEnTiempoReal || !escuchandoCambios.compareAndSet(false, true)) return
        alcanceSesion.launch {
            try {
                remoto.observarCambios(uid).collect { resultado ->
                    when (resultado) {
                        is ResultadoRemoto.Exito -> aplicarCambios(uid, resultado.datos)
                        ResultadoRemoto.SesionInvalida -> alDetectarSesionInvalida(uid)
                        else -> ultimoError.value = resultado.mensajeParaUsuario()
                    }
                }
            } finally {
                // Si el servicio cortó el listener, la próxima sincronización exitosa lo vuelve a abrir
                escuchandoCambios.set(false)
            }
        }
    }

    private suspend fun aplicarCambios(uid: String, cambios: List<CambioRemoto>) {
        escribirSiSigueActiva(uid) {
            cambios.forEach { cambio ->
                when (cambio) {
                    is CambioRemoto.Guardado -> aplicarDocumento(uid, cambio.documento)
                    is CambioRemoto.Eliminado -> {
                        // Solo se borra si no hay cambios locales pendientes sobre ese registro
                        if (dao.obtenerPorId(uid, cambio.id)?.estadoSync == EstadoSync.SINCRONIZADO) {
                            dao.eliminarPorId(uid, cambio.id)
                        }
                    }
                }
            }
        }
    }

    override fun sincronizarEnSegundoPlano() {
        if (uidActivo.value == null) return
        alcanceSesion.launch { sincronizar() }
    }

    // ---------- Cierre de sesión ----------
    override suspend fun cantidadPendientes(): Int = uidActivo.value?.let { dao.contarPendientes(it) } ?: 0

    override suspend fun cerrarSesion(sincronizarAntes: Boolean): ResultadoCierreSesion {
        val uid = uidActivo.value
        if (sincronizarAntes && uid != null) {
            sincronizar()
            val quedan = dao.contarPendientes(uid)
            if (quedan > 0) return ResultadoCierreSesion.QuedanPendientes(quedan, ultimoError.value)
        }
        finalizarSesionLocal()   // primero se limpia lo local...
        auth.cerrarSesion()      // ...después se cierra la sesión del proveedor
        return ResultadoCierreSesion.Cerrada
    }

    override fun categoriasDisponibles(): Set<Categoria> = DatosSimulados.categorias

    // ---------- Simulador (menú de pruebas, solo dev) ----------
    override fun simularListaVacia() { listaVaciaSimulada.value = true }
    override fun simularErrorEnProximaLectura() = fallarProximaLectura.set(true)
    override fun simularErrorEnProximoGuardado() = fallarProximoGuardado.set(true)

    override suspend fun cargarDatosDeEjemplo(): Int {
        listaVaciaSimulada.value = false
        fallarProximaLectura.set(false)
        fallarProximoGuardado.set(false)
        val uid = uidRequerido()
        var agregados = 0
        escribirSiSigueActiva(uid) {
            DatosSimulados.recordatorios.forEach {
                val entidad = it.aEntity(uid).copy(estadoSync = EstadoSync.PENDIENTE_CREAR)
                if (dao.insertarSiNoExiste(entidad) != -1L) agregados++
            }
        }
        sincronizarEnSegundoPlano()
        return agregados
    }

    override suspend fun probarSincronizacionConcurrente(cantidad: Int): String {
        val uid = uidActivo.value ?: return "No hay una sesión activa."
        if (!hayConexion.value) return "Sin conexión: conectate a una red para hacer la prueba."
        escribirSiSigueActiva(uid) { dao.marcarSincronizadosComoPendientes(uid) }
        val pendientes = dao.obtenerPendientes(uid).count { it.estadoSync != EstadoSync.PENDIENTE_ELIMINAR }
        escriturasRemotas.set(0)

        // Varios disparadores al mismo tiempo (como al recuperar la red)
        coroutineScope { repeat(cantidad) { launch { sincronizar() } } }

        val enLaNube = when (val descarga = remoto.descargar(uid)) {
            is ResultadoRemoto.Exito -> descarga.datos.size.toString()
            else -> "? (${descarga.mensajeParaUsuario()})"
        }
        return "$cantidad sincronizaciones simultáneas · $pendientes pendientes · " +
            "${escriturasRemotas.get()} escrituras enviadas · $enLaNube documentos en Firestore · " +
            "${dao.contarVisibles(uid)} en el dispositivo"
    }

    override suspend fun intentarLeerOtraCuenta(otroUid: String): String {
        val prueba = pruebaAutorizacion ?: return "Prueba no disponible en este entorno."
        val uid = otroUid.trim()
        if (!uidValido(uid)) return "Ingresá un uid válido (sin espacios ni \"/\")."
        val propia = uid == uidActivo.value
        return when (val resultado = prueba.leerDeOtraCuenta(uid)) {
            ResultadoRemoto.SinPermiso ->
                "Acceso denegado por las reglas de seguridad (PERMISSION_DENIED). " +
                    "Firestore no devolvió ningún dato de users/$uid/reminders."
            is ResultadoRemoto.Exito ->
                if (propia) "Lectura permitida: es tu propia cuenta (${resultado.datos} documentos)."
                else "ATENCIÓN: Firestore devolvió ${resultado.datos} documentos de otra cuenta. " +
                    "Las reglas de seguridad no están publicadas o permiten el acceso."
            else -> "No se pudo completar la prueba: ${resultado.mensajeParaUsuario()}"
        }
    }

    override suspend fun intentarEscribirEnOtraCuenta(otroUid: String): String {
        val prueba = pruebaAutorizacion ?: return "Prueba no disponible en este entorno."
        val uid = otroUid.trim()
        if (!uidValido(uid)) return "Ingresá un uid válido (sin espacios ni \"/\")."
        if (uid == uidActivo.value) return "Ese es tu propio uid: ingresá el de otra cuenta."
        val ahora = reloj()
        val intruso = BorradorRecordatorio(
            titulo = "Intento de escritura ajena",
            descripcion = null,
            categoria = DatosSimulados.trabajo,
            prioridad = Prioridad.Alta
        ).aEntityNueva(id = generarId(), uidPropietario = uid, ahora = ahora)
        return when (val resultado = prueba.escribirEnOtraCuenta(uid, intruso.id, intruso.aMapaFirestore())) {
            ResultadoRemoto.SinPermiso ->
                "Escritura denegada por las reglas de seguridad (PERMISSION_DENIED). " +
                    "No se creó ningún documento en users/$uid/reminders."
            is ResultadoRemoto.Exito ->
                "ATENCIÓN: se pudo escribir en otra cuenta. Las reglas de seguridad no están publicadas."
            else -> "No se pudo completar la prueba: ${resultado.mensajeParaUsuario()}"
        }
    }

    private fun uidValido(uid: String) = uid.isNotEmpty() && uid.none { it == '/' || it.isWhitespace() }
}

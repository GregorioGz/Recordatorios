package com.example.recordatorios

import com.example.recordatorios.data.RecordatoriosRepository
import com.example.recordatorios.data.RecordatoriosRepositoryEnMemoria
import com.example.recordatorios.data.SimuladorDeEscenarios

/**
 * Contenedor de dependencias simple (inyección manual).
 * Hay UNA sola instancia del repositorio, compartida por todos los ViewModel,
 * para que lo que se guarda en "Crear" aparezca en el listado y en el detalle.
 */
object AppContainer {
    private val repositorioEnMemoria = RecordatoriosRepositoryEnMemoria()

    // Los ViewModel reciben estas referencias tipadas como INTERFACES
    val repositorio: RecordatoriosRepository = repositorioEnMemoria
    val simulador: SimuladorDeEscenarios = repositorioEnMemoria
}

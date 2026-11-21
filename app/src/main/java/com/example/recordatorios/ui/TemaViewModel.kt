package com.example.recordatorios.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.recordatorios.AppContainer
import com.example.recordatorios.data.preferencias.PreferenciasRepository
import com.example.recordatorios.data.preferencias.Tema
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Expone el tema elegido en Ajustes para aplicarlo en RecordatoriosTheme (MainActivity). */
class TemaViewModel(
    preferencias: PreferenciasRepository
) : ViewModel() {

    private val _tema = MutableStateFlow(Tema.SISTEMA)
    val tema: StateFlow<Tema> = _tema.asStateFlow()

    init {
        viewModelScope.launch {
            preferencias.preferencias.collect { _tema.value = it.tema }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { TemaViewModel(AppContainer.preferencias) }
        }
    }
}

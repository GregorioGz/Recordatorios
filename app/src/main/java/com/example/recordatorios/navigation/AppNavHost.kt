package com.example.recordatorios.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.recordatorios.ui.crear.CrearRecordatorioRoute
import com.example.recordatorios.ui.detalle.DetalleRecordatorioRoute
import com.example.recordatorios.ui.lista.ListaRecordatoriosRoute
import com.example.recordatorios.ui.lista.ListaRecordatoriosViewModel

/** Rutas de la aplicación. Al detalle se envía SOLO el id, nunca el objeto completo. */
object Rutas {
    const val LISTA = "lista"
    const val CREAR = "crear"
    const val ARG_RECORDATORIO_ID = "recordatorioId"
    const val DETALLE = "detalle/{$ARG_RECORDATORIO_ID}"

    fun detalle(id: Int) = "detalle/$id"
}

/** Clave para avisarle al listado que se guardó un recordatorio y debe recargar. */
private const val CLAVE_RECARGAR = "recargar_lista"

/**
 * Grafo de navegación. El NavController vive acá (nivel superior de la app):
 * las pantallas y componentes NO lo reciben, solo exponen lambdas.
 */
@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Rutas.LISTA,
        modifier = modifier
    ) {
        // ---------- Destino inicial: listado ----------
        composable(Rutas.LISTA) { entry ->
            val listaViewModel: ListaRecordatoriosViewModel =
                viewModel(factory = ListaRecordatoriosViewModel.Factory)

            // Si "Crear" avisó que se guardó algo, se recarga el listado
            val debeRecargar by entry.savedStateHandle
                .getStateFlow(CLAVE_RECARGAR, false)
                .collectAsStateWithLifecycle()
            LaunchedEffect(debeRecargar) {
                if (debeRecargar) {
                    entry.savedStateHandle[CLAVE_RECARGAR] = false
                    listaViewModel.cargar()
                }
            }

            ListaRecordatoriosRoute(
                viewModel = listaViewModel,
                onCrearRecordatorio = {
                    if (entry.estaActiva()) navController.navigate(Rutas.CREAR) { launchSingleTop = true }
                },
                onRecordatorioClick = { id ->
                    if (entry.estaActiva()) navController.navigate(Rutas.detalle(id)) { launchSingleTop = true }
                }
            )
        }

        // ---------- Creación ----------
        composable(Rutas.CREAR) { entry ->
            CrearRecordatorioRoute(
                onGuardadoExitoso = {
                    navController.previousBackStackEntry?.savedStateHandle?.set(CLAVE_RECARGAR, true)
                    // popBackStack: vuelve al listado existente, sin crear una copia nueva
                    if (entry.estaActiva()) navController.popBackStack()
                },
                onCancelar = {
                    if (entry.estaActiva()) navController.popBackStack()
                }
            )
        }

        // ---------- Detalle: recibe el id como argumento Int ----------
        composable(
            route = Rutas.DETALLE,
            arguments = listOf(navArgument(Rutas.ARG_RECORDATORIO_ID) { type = NavType.IntType })
        ) { entry ->
            DetalleRecordatorioRoute(
                onVolver = {
                    if (entry.estaActiva()) navController.popBackStack()
                }
            )
        }
    }
}

/** Evita navegar dos veces si la persona toca rápido (doble toque). */
private fun NavBackStackEntry.estaActiva(): Boolean =
    lifecycle.currentState == Lifecycle.State.RESUMED

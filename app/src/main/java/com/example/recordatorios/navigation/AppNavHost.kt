package com.example.recordatorios.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.recordatorios.ui.ajustes.AjustesRoute
import com.example.recordatorios.ui.crear.CrearRecordatorioRoute
import com.example.recordatorios.ui.detalle.DetalleRecordatorioRoute
import com.example.recordatorios.ui.lista.ListaRecordatoriosRoute

/** Rutas de la aplicación. Al detalle se envía SOLO el id, nunca el objeto completo. */
object Rutas {
    const val LISTA = "lista"
    const val CREAR = "crear"
    const val AJUSTES = "ajustes"
    const val ARG_RECORDATORIO_ID = "recordatorioId"
    const val DETALLE = "detalle/{$ARG_RECORDATORIO_ID}"
    const val EDITAR = "editar/{$ARG_RECORDATORIO_ID}"

    fun detalle(id: String) = "detalle/$id"
    fun editar(id: String) = "editar/$id"
}

/**
 * Grafo de navegación. El NavController vive acá (nivel superior de la app):
 * las pantallas y componentes NO lo reciben, solo exponen lambdas.
 * No hace falta avisarle al listado que recargue: observa Room y se actualiza solo.
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
            ListaRecordatoriosRoute(
                onCrearRecordatorio = {
                    if (entry.estaActiva()) navController.navigate(Rutas.CREAR) { launchSingleTop = true }
                },
                onRecordatorioClick = { id ->
                    if (entry.estaActiva()) navController.navigate(Rutas.detalle(id)) { launchSingleTop = true }
                },
                onAbrirAjustes = {
                    if (entry.estaActiva()) navController.navigate(Rutas.AJUSTES) { launchSingleTop = true }
                }
            )
        }

        // ---------- Ajustes (preferencias de DataStore) ----------
        composable(Rutas.AJUSTES) { entry ->
            AjustesRoute(
                onVolver = {
                    if (entry.estaActiva()) navController.popBackStack()
                }
            )
        }

        // ---------- Creación ----------
        composable(Rutas.CREAR) { entry ->
            CrearRecordatorioRoute(
                onGuardadoExitoso = {
                    // popBackStack: vuelve al listado existente, sin crear una copia nueva
                    if (entry.estaActiva()) navController.popBackStack()
                },
                onCancelar = {
                    if (entry.estaActiva()) navController.popBackStack()
                }
            )
        }

        // ---------- Edición: reutiliza el formulario de creación en modo edición ----------
        composable(
            route = Rutas.EDITAR,
            arguments = listOf(navArgument(Rutas.ARG_RECORDATORIO_ID) { type = NavType.StringType })
        ) { entry ->
            CrearRecordatorioRoute(
                onGuardadoExitoso = {
                    // Vuelve al detalle, que observa Room y ya muestra los cambios
                    if (entry.estaActiva()) navController.popBackStack()
                },
                onCancelar = {
                    if (entry.estaActiva()) navController.popBackStack()
                }
            )
        }

        // ---------- Detalle: recibe el id (UUID) como argumento String ----------
        composable(
            route = Rutas.DETALLE,
            arguments = listOf(navArgument(Rutas.ARG_RECORDATORIO_ID) { type = NavType.StringType })
        ) { entry ->
            DetalleRecordatorioRoute(
                onVolver = {
                    if (entry.estaActiva()) navController.popBackStack()
                },
                onEditar = { id ->
                    if (entry.estaActiva()) navController.navigate(Rutas.editar(id)) { launchSingleTop = true }
                },
                onEliminado = {
                    // El detalle ya no tiene datos que mostrar: vuelve al listado
                    navController.popBackStack(Rutas.LISTA, inclusive = false)
                }
            )
        }
    }
}

/** Evita navegar dos veces si la persona toca rápido (doble toque). */
private fun NavBackStackEntry.estaActiva(): Boolean =
    lifecycle.currentState == Lifecycle.State.RESUMED

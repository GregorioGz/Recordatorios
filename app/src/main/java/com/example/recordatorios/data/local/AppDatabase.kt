package com.example.recordatorios.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.recordatorios.data.DatosSimulados
import com.example.recordatorios.data.aEntity
import com.example.recordatorios.model.EstadoSync

/** Base de datos local: fuente de verdad de los recordatorios. */
@Database(entities = [RecordatorioEntity::class], version = 1, exportSchema = false)
@TypeConverters(ConvertidoresRoom::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun recordatorioDao(): RecordatorioDao

    companion object {
        private const val NOMBRE_ARCHIVO = "recordatorios.db"

        @Volatile
        private var instancia: AppDatabase? = null

        /** ÚNICA instancia por proceso (doble verificación con synchronized). */
        fun obtener(context: Context): AppDatabase =
            instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    NOMBRE_ARCHIVO
                )
                    .addCallback(CargaInicial)
                    .build()
                    .also { instancia = it }
            }
    }

    /**
     * onCreate se ejecuta UNA sola vez: cuando el archivo de la base se crea.
     * En los inicios siguientes la base ya existe y no se vuelve a insertar nada.
     * Los ejemplos entran como PENDIENTE_CREAR para que la primera sincronización
     * los envíe al servidor.
     */
    private object CargaInicial : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            val convertidores = ConvertidoresRoom()
            DatosSimulados.recordatorios
                .map { it.aEntity().copy(estadoSync = EstadoSync.PENDIENTE_CREAR) }
                .forEach { entidad ->
                    db.insert("recordatorios", SQLiteDatabase.CONFLICT_IGNORE, entidad.aContentValues(convertidores))
                }
        }
    }
}

private fun RecordatorioEntity.aContentValues(convertidores: ConvertidoresRoom) = ContentValues().apply {
    put("id", id)
    put("titulo", titulo)
    put("descripcion", descripcion)
    put("categoriaId", categoriaId)
    put("categoriaNombre", categoriaNombre)
    put("prioridad", prioridad)
    put("estado", estado)
    put("ubicacionNombre", ubicacionNombre)
    put("ubicacionLatitud", ubicacionLatitud)
    put("ubicacionLongitud", ubicacionLongitud)
    put("avisoMinutosAntes", avisoMinutosAntes)
    put("fechaCreacion", fechaCreacion)
    put("fechaActualizacion", fechaActualizacion)
    put("estadoSync", convertidores.estadoSyncATexto(estadoSync))
    put("errorSync", errorSync)
}

package com.example.recordatorios.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Base de datos local: fuente de verdad de los recordatorios de la cuenta activa.
 * Versión 2: agrega `uidPropietario`. El esquema se exporta a app/schemas/ para
 * poder revisar cada versión.
 * Ya no se insertan datos de ejemplo al crear la base: se subirían a la cuenta
 * de cada persona. En dev se cargan desde el menú de pruebas.
 */
@Database(entities = [RecordatorioEntity::class], version = 2, exportSchema = true)
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
                    // Migración EXPLÍCITA: sin fallbackToDestructiveMigration, un cambio de
                    // esquema nunca borra en silencio cambios pendientes de sincronizar.
                    .addMigrations(MIGRACION_1_2)
                    .build()
                    .also { instancia = it }
            }
    }
}

/**
 * 1 -> 2: agrega la columna `uidPropietario` (con su índice).
 * Las filas existentes eran de la versión sin cuentas: quedan con uid vacío,
 * no son visibles para ninguna cuenta y se descartan al primer inicio de sesión
 * (así ninguna cuenta hereda datos que no son suyos).
 */
val MIGRACION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE recordatorios ADD COLUMN uidPropietario TEXT NOT NULL DEFAULT ''")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_recordatorios_uidPropietario ON recordatorios (uidPropietario)")
    }
}

# Recordatorios

Aplicación Android de recordatorios con **Jetpack Compose + Material 3**, persistencia local con **Room** y **DataStore**, sincronización **local-first** con un servicio remoto (**Retrofit + OkHttp**) y funcionamiento offline.

## Funcionalidades

- Listado, creación, detalle, **edición** y **eliminación** de recordatorios (Room como fuente de verdad).
- **Ajustes persistentes** (DataStore): tema (sistema/claro/oscuro), mostrar completados, orden del listado y filtro de prioridad.
- **Sincronización controlada**: los cambios se guardan primero en el dispositivo y se envían al servidor cuando hay red, sin duplicados.
- Indicadores de sincronización por recordatorio ("Sincronizado", "Pendiente de sincronizar", "Error: reintentar") y un banner global (sin conexión, sincronizando, pendientes, último error).
- **Servidor simulado** integrado para probar respuestas normales, error 500, error 400, timeout y falta de conexión sin un backend externo.

## Requisitos

- Android Studio (versión compatible con AGP 9.5).
- JDK: Gradle usa una *toolchain* Java 25 (`gradle/gradle-daemon-jvm.properties`); si no está instalada, el plugin *foojay* la descarga automáticamente.
- Android SDK con la plataforma **API 37** (compileSdk/targetSdk 37). `minSdk` 24.
- Emulador o dispositivo con Android 7.0 o superior.

## Configuración del servicio remoto

La URL base se lee de `local.properties` (archivo local, no versionado) con la clave `API_BASE_URL` y se expone como `BuildConfig.API_BASE_URL`.

- **Sin configurar nada** → se usa `https://api.recordatorios.example/` y se activa el **servidor simulado** (backend en memoria dentro de la app). Es la opción recomendada para probar.
- **Servidor propio** → copiá la línea de [`api.properties.example`](api.properties.example) en `local.properties`:

  ```properties
  API_BASE_URL=https://mi-servidor.ejemplo.com/api/
  ```

  El servidor debe exponer `GET /recordatorios`, `PUT /recordatorios/{id}` (upsert idempotente) y `DELETE /recordatorios/{id}` con el JSON de `RecordatorioDto`.

No se incluyen secretos ni credenciales en el código ni en los archivos de ejemplo.

## Compilar y ejecutar

Desde la raíz del proyecto (`Recordatorios/`):

```powershell
.\gradlew.bat assembleDebug        # genera app\build\outputs\apk\debug\app-debug.apk
.\gradlew.bat installDebug         # instala en el emulador/dispositivo conectado
```

También se puede abrir la carpeta en Android Studio y ejecutar la configuración **app**.

## Tests

Tests unitarios JVM (no requieren emulador):

```powershell
.\gradlew.bat testDebugUnitTest
```

El reporte HTML queda en `app\build\reports\tests\testDebugUnitTest\index.html`. Cubren:

| Clase | Qué verifica |
|---|---|
| `MapeosTest` | Mapeos DTO ↔ Entity ↔ Dominio, claves desconocidas, transiciones de estado al editar |
| `PreferenciasTest` | Lectura de preferencias: valores válidos, ausentes, inválidos y error de lectura |
| `ResultadoRemotoTest` | 200/201/204, 400/404, 500/503, timeout, sin conexión → `ResultadoRemoto` |
| `SincronizacionTest` | Sincronizaciones concurrentes sin duplicados, errores, eliminación, descarga sin pisar pendientes |

## Menú de pruebas (⋮ en el listado)

Opciones pensadas solo para probar la app:

| Opción | Efecto |
|---|---|
| Simular lista vacía | El listado muestra el estado vacío (no borra datos) |
| Simular error de lectura local | La próxima lectura de Room falla → estado de error con "Reintentar" |
| Simular error en el próximo guardado | El próximo crear/editar falla una vez → error con "Reintentar" |
| Restaurar datos de ejemplo | Quita las simulaciones locales y vuelve a insertar los ejemplos que falten |
| Guardar tema inválido (DataStore) | Guarda un valor desconocido en la clave del tema → la app usa el predeterminado |
| Servidor: normal / error 500 / solicitud inválida 400 / timeout / sin conexión | Cambia la respuesta del servidor simulado (la opción activa tiene ✓) |
| Probar 3 sincronizaciones simultáneas | Marca todo como pendiente, lanza 3 sincronizaciones a la vez y muestra los PUT recibidos y los registros del servidor |

Otros accesos: ⟳ en la barra superior = **Sincronizar ahora**; ⚙ = **Ajustes**.

> El servidor simulado guarda sus datos en memoria: al cerrar la app se vacía, pero los recordatorios siguen en Room (el dispositivo es la fuente de verdad).

## Estructura

```
app/src/main/java/com/example/recordatorios/
├── AppContainer.kt, RecordatoriosApp.kt, MainActivity.kt
├── model/                 Modelo de dominio y validaciones
├── data/
│   ├── preferencias/      DataStore (preferencias de usuario)
│   ├── local/             Room: Entity, DAO, AppDatabase
│   ├── remote/            Retrofit/OkHttp: DTO, API, ResultadoRemoto, servidor simulado, conectividad
│   ├── Mapeos.kt          Mapeos entre capas
│   └── RecordatoriosRepository*.kt  Repositorio local-first
├── navigation/            AppNavHost (rutas)
└── ui/                    Pantallas Compose, ViewModels y componentes
```

El informe técnico está en [`INFORME_Actividad3.md`](INFORME_Actividad3.md).

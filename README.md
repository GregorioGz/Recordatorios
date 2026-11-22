# Recordatorios

Aplicación Android de recordatorios con **Jetpack Compose + Material 3**, persistencia local con **Room** y **DataStore**, cuentas de usuario con **Firebase Authentication** y sincronización **local-first** con **Cloud Firestore**. Funciona sin conexión y sincroniza cuando vuelve la red.

## Funcionalidades

- **Cuentas:** registro e inicio de sesión con email y contraseña, sesión persistente (reabrir la app entra directo al listado), detección de sesión inválida y cierre de sesión con aviso de cambios pendientes.
- **Recordatorios:** listado, creación, detalle, edición y eliminación. Room es la fuente de verdad y cada cuenta ve solo sus datos.
- **Sincronización con Firestore** en `users/{uid}/reminders/{reminderId}`, con el mismo UUID en Room y en Firestore. Los reintentos no duplican.
- **Tiempo real:** los cambios hechos desde otro dispositivo llegan mientras la sesión está abierta.
- **Indicadores:** cada recordatorio muestra "Sincronizado", "Pendiente de sincronizar" o "Error: reintentar". Un banner global muestra conexión, pendientes y último error.
- **Ajustes persistentes (DataStore):** tema, mostrar completados, orden y filtro de prioridad. Son del dispositivo y se conservan al cambiar de cuenta.
- **Reglas de seguridad** de Firestore: cada cuenta solo puede leer y escribir sus propios documentos.

## Requisitos

- Android Studio con soporte para AGP 9.5.
- JDK: Gradle usa una *toolchain* Java 25 (`gradle/gradle-daemon-jvm.properties`). Si no está instalada, el plugin *foojay* la descarga.
- Android SDK con la plataforma **API 37** (`compileSdk` y `targetSdk` 37) y `build-tools` (para `apksigner`). `minSdk` 24.
- Emulador o dispositivo con Android 7.0 o superior y **Google Play Services**.
- Un proyecto de Firebase (ver abajo).

## Configuración de Firebase

1. **Crear el proyecto** en la [consola de Firebase](https://console.firebase.google.com/).
2. **Registrar las dos apps Android** del mismo proyecto:
   - `com.example.recordatorios` (variante `prod`)
   - `com.example.recordatorios.dev` (variante `dev`)
3. **Descargar `google-services.json`** (Configuración del proyecto → Tus apps → `google-services.json`) y copiarlo en `app/`. El archivo tiene que incluir los dos `package_name`. No se versiona: está en `.gitignore`.
4. **Authentication** → Método de acceso → habilitar **Correo electrónico/contraseña**.
5. **Firestore Database** → crear la base en **modo producción** (todo denegado por defecto).
6. **Publicar las reglas** de [`firestore.rules`](firestore.rules):
   - Firestore Database → pestaña **Reglas**.
   - Reemplazar todo el contenido por el del archivo y tocar **Publicar**.
   - Para probarlas antes, usar el **Rules Playground** de esa pestaña: una lectura de `users/UID_A/reminders/x` autenticada como `UID_B` tiene que dar *denied*.

Firestore se configura con **caché en memoria**: Room es la única caché persistente. La app nunca guarda contraseñas ni tokens; Firebase administra la sesión.

## Variantes (flavors × build types)

| Variante | applicationId | Nombre visible | versionName | Herramientas de prueba |
|---|---|---|---|---|
| `devDebug` | `com.example.recordatorios.dev` | Recordatorios DEV | 4.0.0-rc1-dev | Sí (menú ⋮, uid en Ajustes) |
| `devRelease` | `com.example.recordatorios.dev` | Recordatorios DEV | 4.0.0-rc1-dev | Sí |
| `prodDebug` | `com.example.recordatorios` | Recordatorios | 4.0.0-rc1 | No |
| `prodRelease` | `com.example.recordatorios` | Recordatorios | 4.0.0-rc1 | No (versión candidata) |

- Todas las variantes tienen `versionCode = 4`.
- Cada flavor define `BuildConfig.ENTORNO` y `BuildConfig.HERRAMIENTAS_DE_PRUEBA`. No hay que cambiar nada a mano antes de compilar.
- `dev` y `prod` se pueden instalar a la vez en el mismo dispositivo.
- En `release` está activado R8 (reducción y optimización de código). Firebase trae sus propias reglas de R8, y el mapeo de Firestore es explícito (`Map`), sin reflexión.

## Firma de la versión release

La clave y sus contraseñas **nunca** se guardan en el repositorio.

1. Crear la clave con Android Studio: **Build → Generate Signed App Bundle or APK → Create new…**. Guardar el `.jks` **fuera del proyecto**.
2. Copiar [`keystore.properties.example`](keystore.properties.example) como `keystore.properties` en la raíz del proyecto (está en `.gitignore`) y completar sus valores:

   ```properties
   storeFile=C:/ruta/fuera/del/proyecto/mi-clave-release.jks
   storePassword=...
   keyAlias=...
   keyPassword=...
   ```

   En `storeFile` usá barras `/`: en un `.properties`, la barra invertida `\` es un carácter de escape.
3. Si falta el archivo (o alguna clave), las variantes `release` fallan con un mensaje claro y las `debug` siguen compilando.

`.gitignore` excluye `keystore.properties`, `*.jks`, `*.keystore`, `google-services.json`, `local.properties` y `.kotlin/`.

## Compilar

Desde la raíz del proyecto:

```powershell
.\gradlew.bat assembleDevDebug                      # APK de desarrollo
.\gradlew.bat installDevDebug                       # instalar en el emulador o dispositivo
.\gradlew.bat assembleProdRelease bundleProdRelease # APK y AAB release firmados
```

| Artefacto | Ruta |
|---|---|
| APK debug (dev) | `app\build\outputs\apk\dev\debug\app-dev-debug.apk` |
| APK release (prod) | `app\build\outputs\apk\prod\release\app-prod-release.apk` |
| AAB release (prod) | `app\build\outputs\bundle\prodRelease\app-prod-release.aab` |

Para verificar la firma y la integridad del APK:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.0.0\apksigner.bat" verify --print-certs app\build\outputs\apk\prod\release\app-prod-release.apk
Get-FileHash app\build\outputs\apk\prod\release\app-prod-release.apk -Algorithm SHA256
```

## Tests

Tests unitarios en la JVM (no requieren emulador ni Firebase):

```powershell
.\gradlew.bat testDevDebugUnitTest
```

El reporte queda en `app\build\reports\tests\testDevDebugUnitTest\index.html`.

| Clase | Qué verifica |
|---|---|
| `MapeosTest` | Mapeos Dominio ↔ Entity ↔ `Map` de Firestore y descarte de documentos inválidos |
| `ResultadoRemotoTest` | Códigos de Firestore (`UNAVAILABLE`, `PERMISSION_DENIED`, `UNAUTHENTICATED`…), timeout y cancelación → `ResultadoRemoto` |
| `AutenticacionTest` | Códigos de Firebase Auth → `ErrorAuth`, cuándo se invalida la sesión y validación del formulario |
| `SincronizacionTest` | Sincronizaciones concurrentes, reintentos sin duplicados, sin conexión, acceso denegado, sesión inválida, eliminación y descarga sin pisar pendientes |
| `SesionTest` | Solo se exponen datos del uid activo, cierre de sesión con limpieza, recuperación al iniciar sesión, cambio de cuenta y cambios en tiempo real |
| `PreferenciasTest` | Lectura de preferencias de DataStore: valores válidos, ausentes, inválidos y error de lectura |

Las traducciones de errores reciben el **nombre del código** del proveedor, así los tests no dependen del SDK de Firebase. Para las fuentes de datos se usan fakes de `RecordatorioDao`, `FuenteRemotaRecordatorios` y `AuthRepository`.

## Menú de pruebas (⋮ en el listado, solo `dev`)

| Opción | Efecto |
|---|---|
| Simular lista vacía / error de lectura local / error en el próximo guardado | Estados de la interfaz (vacío, error con "Reintentar") |
| Cargar datos de ejemplo | Agrega los recordatorios de ejemplo a la cuenta activa (ya no se cargan solos) |
| Guardar tema inválido (DataStore) | La app vuelve al tema predeterminado sin cerrarse |
| Probar 3 sincronizaciones simultáneas | Marca todo como pendiente, sincroniza 3 veces a la vez y compara escrituras, documentos en Firestore y filas en el dispositivo |
| Intentar leer datos de otra cuenta | `get()` directo a `users/{otroUid}/reminders` en Firestore: las reglas lo rechazan ("Acceso denegado por las reglas de seguridad") |
| Intentar escribir en otra cuenta | `set()` directo en la colección de otra cuenta: las reglas lo rechazan |

El uid de la cuenta activa se ve en **Ajustes** (solo en `dev`) y se puede seleccionar para copiarlo.

## Datos locales y privacidad

- **Al cerrar la sesión:**
  - Si hay cambios sin sincronizar, la app avisa con *Sincronizar y salir* / *Salir igual* / *Cancelar*.
  - Se cancelan la sincronización y el listener de Firestore, y se borran de Room los recordatorios de la cuenta.
  - Las preferencias de DataStore se conservan porque son del dispositivo, no de la cuenta.
- **Sesión inválida** (cuenta deshabilitada o eliminada, token revocado, `UNAUTHENTICATED`): se borran los datos locales de la cuenta y se vuelve a la pantalla de acceso con el motivo.
- **Backup:** `android:allowBackup="false"`. Además, las reglas de backup y de transferencia entre dispositivos excluyen la base de Room, la sesión y los archivos locales. Los datos se recuperan desde Firestore al iniciar sesión.
- **Logs:** no hay logs de red ni de datos. Las contraseñas no se guardan ni se registran.

## Estructura

```
app/src/main/java/com/example/recordatorios/
├── AppContainer.kt, RecordatoriosApp.kt, MainActivity.kt
├── model/                 Modelo de dominio, sesión y validaciones
├── data/
│   ├── auth/              AuthRepository (Firebase Auth) y ErrorAuth
│   ├── preferencias/      DataStore (preferencias del dispositivo)
│   ├── local/             Room: Entity, DAO, AppDatabase y migración 1 → 2
│   ├── remote/            FuenteRemotaRecordatorios (Firestore), ResultadoRemoto, conectividad
│   ├── Mapeos.kt          Mapeos entre capas (incluido el Map de Firestore)
│   └── RecordatoriosRepository*.kt  Repositorio local-first por cuenta
├── navigation/            AppNavHost (navegación según la sesión)
└── ui/                    Pantallas Compose, ViewModels y componentes
app/schemas/               Esquemas exportados de Room (uno por versión)
firestore.rules            Reglas de seguridad de Firestore
keystore.properties.example
```

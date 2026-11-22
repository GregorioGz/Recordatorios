import java.io.StringReader
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

/**
 * Lectura de keystore.properties (raíz del proyecto, NO versionado) compatible con
 * configuration cache: el archivo queda registrado como entrada del build.
 * Claves: storeFile, storePassword, keyAlias, keyPassword. Ver keystore.properties.example.
 */
class DatosDeFirma(private val propiedades: Properties?) {
    private val claves = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    fun valor(clave: String): String = propiedades?.getProperty(clave)?.trim().orEmpty()
    val faltantes: List<String> = claves.filter { valor(it).isEmpty() }
    val completa: Boolean = propiedades != null && faltantes.isEmpty()
}

val firma = DatosDeFirma(
    providers.fileContents(rootProject.layout.projectDirectory.file("keystore.properties"))
        .asText.orNull
        ?.let { texto -> Properties().apply { load(StringReader(texto)) } }
)

android {
    namespace = "com.example.recordatorios"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.recordatorios"
        minSdk = 24
        targetSdk = 37
        // Versión candidata: cada entrega a testers sube el versionCode
        versionCode = 4
        versionName = "4.0.0-rc1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // ---------- Entornos: cada variante sale configurada del build, sin cambios manuales ----------
    flavorDimensions += "entorno"
    productFlavors {
        create("dev") {
            dimension = "entorno"
            applicationIdSuffix = ".dev"          // com.example.recordatorios.dev: se instala junto a prod
            versionNameSuffix = "-dev"
            resValue("string", "app_name", "Recordatorios DEV")
            buildConfigField("String", "ENTORNO", "\"dev\"")
            buildConfigField("boolean", "HERRAMIENTAS_DE_PRUEBA", "true")
        }
        create("prod") {
            dimension = "entorno"
            resValue("string", "app_name", "Recordatorios")
            buildConfigField("String", "ENTORNO", "\"prod\"")
            buildConfigField("boolean", "HERRAMIENTAS_DE_PRUEBA", "false")
        }
    }

    // ---------- Firma release: credenciales fuera del repositorio (keystore.properties) ----------
    signingConfigs {
        if (firma.completa) {
            create("release") {
                storeFile = file(firma.valor("storeFile"))
                storePassword = firma.valor("storePassword")
                keyAlias = firma.valor("keyAlias")
                keyPassword = firma.valor("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8: reduce y optimiza el código (Firebase incluye sus propias reglas de consumidor)
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**", "com.example.recordatorios.**")
            }
            if (firma.completa) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
}

// Room exporta cada versión del esquema (sirve para revisar y probar migraciones)
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.kotlinx.coroutines.play.services)
    testImplementation(libs.kotlinx.coroutines.test)
}

// Sin keystore.properties completo, las variantes release fallan con un mensaje claro
// (las debug no se ven afectadas). Solo se capturan valores simples: compatible con configuration cache.
val mensajeSinFirma: String? = if (firma.completa) null else
    "No se puede firmar la versión release: falta keystore.properties en la raíz del proyecto" +
        (if (firma.faltantes.size < 4) " o le faltan las claves ${firma.faltantes}" else "") +
        ". Copiá keystore.properties.example como keystore.properties y completalo."
val verificarFirmaRelease = tasks.register("verificarFirmaRelease") {
    group = "verification"
    description = "Comprueba que la firma release esté configurada."
    val mensaje = mensajeSinFirma
    doLast { if (mensaje != null) throw GradleException(mensaje) }
}
tasks.configureEach {
    if (name.matches(Regex("pre(Dev|Prod)ReleaseBuild"))) dependsOn(verificarFirmaRelease)
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

import java.net.URI
import java.util.Properties

val debugApiBaseUrl = providers.gradleProperty("clinicaSerena.debugApiBaseUrl").get()
val releaseApiBaseUrl = providers.gradleProperty("clinicaSerena.releaseApiBaseUrl").get()

/** URL de producción: HTTPS obligatorio, con host, sin credenciales y terminada en `/` (Retrofit lo exige). */
fun validateReleaseUrl(url: String) {
    val uri = runCatching { URI(url) }.getOrElse { throw GradleException("clinicaSerena.releaseApiBaseUrl no es una URL válida") }
    if (uri.scheme != "https") throw GradleException("clinicaSerena.releaseApiBaseUrl debe usar HTTPS")
    if (uri.host.isNullOrBlank()) throw GradleException("clinicaSerena.releaseApiBaseUrl no tiene host")
    if (uri.userInfo != null) throw GradleException("clinicaSerena.releaseApiBaseUrl no debe incluir usuario ni contraseña")
    if (!url.endsWith("/")) throw GradleException("clinicaSerena.releaseApiBaseUrl debe terminar en /")
}
validateReleaseUrl(releaseApiBaseUrl)
if (URI(releaseApiBaseUrl).host.endsWith(".invalid")) {
    logger.warn("Aviso: URL de producción no definida (clinicaSerena.releaseApiBaseUrl usa un dominio .invalid).")
}

/**
 * Firma del release desde un archivo .properties FUERA del repo (storeFile, storePassword, keyAlias,
 * keyPassword). Su ruta llega por -PclinicaSerena.signingProperties, ~/.gradle/gradle.properties o la
 * variable CLINICA_SERENA_SIGNING_PROPERTIES. Sin configurar, el release sale sin firma. Los mensajes
 * nombran el campo, nunca su valor.
 */
data class ReleaseSigning(val storeFile: File, val storePassword: String, val keyAlias: String, val keyPassword: String)

val releaseSigning: ReleaseSigning? = run {
    val path = providers.gradleProperty("clinicaSerena.signingProperties").orNull
        ?: providers.environmentVariable("CLINICA_SERENA_SIGNING_PROPERTIES").orNull
    if (path.isNullOrBlank()) {
        logger.warn("Aviso: firma de release no configurada; el APK de release saldrá sin firmar.")
        return@run null
    }
    val file = File(path)
    if (!file.isFile) throw GradleException("Firma: no existe el archivo indicado en clinicaSerena.signingProperties")
    val projectDir = rootDir.canonicalFile
    if (file.canonicalFile.startsWith(projectDir)) {
        throw GradleException("Firma: el archivo de firma debe estar fuera del repositorio")
    }
    val props = Properties().apply { file.inputStream().use { load(it) } }
    fun required(key: String): String =
        props.getProperty(key)?.takeIf { it.isNotBlank() } ?: throw GradleException("Firma: falta el campo '$key' en el archivo de firma")
    val store = File(required("storeFile"))
    if (!store.isFile) throw GradleException("Firma: no existe la keystore indicada en 'storeFile'")
    if (store.canonicalFile.startsWith(projectDir)) throw GradleException("Firma: la keystore debe estar fuera del repositorio")
    ReleaseSigning(store, required("storePassword"), required("keyAlias"), required("keyPassword"))
}

android {
    namespace = "com.clinicaserena.app"
    // Las versiones estables actuales de Compose, core, lifecycle, navigation y OkHttp exigen compilar contra 37.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.clinicaserena.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        releaseSigning?.let { signing ->
            create("release") {
                storeFile = signing.storeFile
                storePassword = signing.storePassword
                keyAlias = signing.keyAlias
                keyPassword = signing.keyPassword
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"$debugApiBaseUrl\"")
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"$releaseApiBaseUrl\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
        // Igual que release (R8, sin logging ni herramientas de depuración, no depurable), pero contra el
        // backend local y con la llave debug para instalarlo junto al release. Solo para pruebas.
        create("qaLocal") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
            applicationIdSuffix = ".qa"
            buildConfigField("String", "API_BASE_URL", "\"$debugApiBaseUrl\"")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    sourceSets {
        // qaLocal reutiliza las implementaciones de release (sin registro HTTP ni herramientas de depuración).
        getByName("qaLocal") { kotlin.directories += "src/release/java" }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    debugImplementation(libs.okhttp.logging.interceptor)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver3)
    testImplementation(libs.okhttp.logging.interceptor)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}

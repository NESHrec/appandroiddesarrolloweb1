// AGP 9 trae Kotlin integrado: no se aplica org.jetbrains.kotlin.android.
// Declarar los plugins de Kotlin aquí fija la versión de Kotlin (KGP) del proyecto.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// URL del backend desplegado en Vercel.
val urlVercel = "https://agendasalud-backend.vercel.app/"

// local.properties no se versiona: cada uno elige ahí si usa el backend local.
val localProps = Properties().apply {
    val archivo = rootProject.file("local.properties")
    if (archivo.exists()) archivo.inputStream().use { load(it) }
}

android {
    namespace = "ar.edu.fguerrero.da1.agendasalud"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "ar.edu.fguerrero.da1.agendasalud"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Por defecto, el backend desplegado en Vercel.
            // Para usar el backend local (TPO1_backend con Docker o uvicorn en el puerto 8000),
            // agregar `backendLocal=true` en local.properties o compilar con -PbackendLocal=true.
            // 10.0.2.2 es la compu vista desde el emulador.
            val usarLocal = (project.findProperty("backendLocal") as String?
                ?: localProps.getProperty("backendLocal")) == "true"
            buildConfigField(
                "String", "URL_SERVIDOR",
                if (usarLocal) "\"http://10.0.2.2:8000/\"" else "\"$urlVercel\""
            )
        }
        release {
            // URL del deploy en Vercel (siempre HTTPS).
            buildConfigField("String", "URL_SERVIDOR", "\"$urlVercel\"")
            // R8: reduce, optimiza y ofusca el código, y quita los recursos que no se usan.
            // En debug queda desactivado para compilar más rápido y poder depurar.
            optimization {
                enable = true
                keepRules {
                    files.add(file("proguard-rules.pro"))
                }
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // URL_SERVIDOR distinta en debug y release
        buildConfig = true
    }
}

ksp {
    // Esquema exportado para versionar migraciones de Room
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.sqlite)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
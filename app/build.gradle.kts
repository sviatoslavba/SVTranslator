plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.svtranslator5"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.svtranslator5"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // ML Kit для розпізнавання тексту
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.0")
    implementation("com.google.android.gms:play-services-mlkit-language-id:17.0.0")

    // Для кирилиці та інших скриптів
    implementation("com.google.mlkit:text-recognition-devanagari:16.0.0")
    implementation("com.google.mlkit:text-recognition-japanese:16.0.0")
    implementation("com.google.mlkit:text-recognition-korean:16.0.0")

    implementation("com.rmtheis:tess-two:9.1.0")

    // Google Translate
    implementation("com.google.mlkit:translate:17.0.2")

    // Для роботи з зображеннями
    implementation("com.github.bumptech.glide:glide:4.15.1")
    implementation("androidx.exifinterface:exifinterface:1.3.6")

    // Для роботи з файлами
    implementation("androidx.documentfile:documentfile:1.0.1")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
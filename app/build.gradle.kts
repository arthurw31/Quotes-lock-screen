plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Numéro de version fourni par GitHub Actions (il augmente à chaque compilation),
// ce qui permet d'installer chaque nouvelle version par-dessus l'ancienne.
val ciVersionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1

android {
    namespace = "io.github.arthurw31.citations"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.arthurw31.citations"
        minSdk = 31
        targetSdk = 36
        versionCode = ciVersionCode
        versionName = "1.0.$ciVersionCode"
    }

    // Clé de signature unique et fixe : toutes les versions sont signées pareil,
    // donc Android accepte les mises à jour sans désinstaller (réglages conservés).
    // La clé n'est pas dans le dépôt : GitHub Actions la reçoit via les Secrets du dépôt.
    val keystorePath = System.getenv("SIGNING_KEYSTORE_PATH")
    val keystorePassword = System.getenv("SIGNING_PASSWORD")
    val hasSigningKey = keystorePath != null && keystorePassword != null && file(keystorePath).exists()

    signingConfigs {
        if (hasSigningKey) {
            create("perso") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePassword
                keyAlias = "citations"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Sans clé (compilation locale), l'APK est signé avec la clé de debug.
            signingConfig = if (hasSigningKey) signingConfigs.getByName("perso") else signingConfigs.getByName("debug")
        }
    }

    // La liste de citations du dépôt est intégrée à l'appli comme liste de secours hors ligne.
    sourceSets {
        getByName("main") {
            assets.srcDir(rootProject.file("citations"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            test.systemProperty("robolectric.graphicsMode", "NATIVE")
            System.getenv("RENDER_OUT")?.let { test.systemProperty("renderOut", it) }
            System.getenv("ROBOLECTRIC_REPO_URL")?.let { test.systemProperty("robolectric.dependency.repo.url", it) }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.work:work-runtime-ktx:2.10.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}

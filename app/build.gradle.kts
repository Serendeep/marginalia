plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    `maven-publish`
}

val appVersion = "0.2.0" // x-release-please-version
val signingStorePath = providers.environmentVariable("SIGNING_KEYSTORE_PATH").orNull
val signingStorePassword = providers.environmentVariable("SIGNING_STORE_PASSWORD").orNull
val signingKeyAlias = providers.environmentVariable("SIGNING_KEY_ALIAS").orNull
val signingKeyPassword = providers.environmentVariable("SIGNING_KEY_PASSWORD").orNull
val hasReleaseSigning = listOf(
    signingStorePath,
    signingStorePassword,
    signingKeyAlias,
    signingKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.serendeep.marginalia"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.serendeep.marginalia"
        minSdk = 29
        targetSdk = 35
        // GitHub run numbers are monotonic, so each CI-built release can update
        // an installed APK. Local builds retain the initial version code.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = appVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.create("release") {
                    storeFile = file(signingStorePath!!)
                    storePassword = signingStorePassword
                    keyAlias = signingKeyAlias
                    keyPassword = signingKeyPassword
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        // pdfiumandroid is built with a newer Kotlin; its bytecode is compatible,
        // so let this compiler read its metadata instead of rejecting the version.
        freeCompilerArgs += "-Xskip-metadata-version-check"
    }

    buildFeatures {
        compose = true
    }
}

val prepareReleaseApkForPublication by tasks.registering {
    dependsOn("assembleRelease")
}

publishing {
    publications {
        register<MavenPublication>("releaseApk") {
            groupId = "com.serendeep.marginalia"
            artifactId = "marginalia"
            version = appVersion

            artifact(layout.buildDirectory.file("outputs/apk/release/app-release.apk")) {
                builtBy(prepareReleaseApkForPublication)
                extension = "apk"
            }

            pom {
                name.set("Marginalia")
                description.set("Handwritten lecture notes next to PDF slides for Android tablets.")
                url.set("https://github.com/Serendeep/marginalia")
                packaging = "apk"
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/${System.getenv("GITHUB_REPOSITORY") ?: "Serendeep/marginalia"}")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android.sourceSets.getByName("androidTest") {
    assets.srcDir("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.haze)
    implementation(libs.coil.compose)
    implementation(libs.graphics.shapes)
    implementation(libs.composables.core)
    implementation(libs.material.icons.extended)
    implementation(libs.pdfium)
    implementation(libs.emoji2.emojipicker)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.ink.strokes)
    implementation(libs.ink.storage)
    implementation(libs.ink.brush)
    implementation(libs.ink.authoring)
    implementation(libs.ink.rendering)
    implementation(libs.ink.geometry)
    implementation(libs.input.motionprediction)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
}

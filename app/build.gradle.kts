plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.baselineprofile)
    `maven-publish`
}

val appVersion = "1.1.0" // x-release-please-version
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

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(signingStorePath!!)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
                // v3 carries a signing lineage, so the key can be rotated later without breaking updates.
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            ndk { abiFilters += "arm64-v8a" }
        }
        // A release-like build that installs beside the real app, for recording demos with sample data.
        // -PdemoDebuggable makes it inspectable so sample data can be seeded; reinstall without it to record.
        create("demo") {
            initWith(getByName("release"))
            applicationIdSuffix = ".demo"
            signingConfig = signingConfigs.getByName(if (hasReleaseSigning) "release" else "debug")
            isDebuggable = project.hasProperty("demoDebuggable")
            matchingFallbacks += listOf("release")
            ndk { abiFilters.clear(); abiFilters += "arm64-v8a" }
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
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
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

composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_reports")
    metricsDestination = layout.buildDirectory.dir("compose_reports")
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
    implementation(libs.androidx.webkit)

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
    implementation(libs.mlkit.digital.ink)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    baselineProfile(project(":baselineprofile"))

    testImplementation(libs.junit)
    testImplementation("org.json:json:20250517")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
}

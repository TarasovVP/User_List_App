import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kover)
    alias(libs.plugins.screenshot)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.firebase.performance)
}

val releaseSigningPropertiesFile = rootProject.file("keystore.properties")
val useModularAccount = providers.gradleProperty("useModularAccount").orElse("true")
val releaseSigningProperties = Properties().apply {
    if (releaseSigningPropertiesFile.exists()) {
        releaseSigningPropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.example.userlistapp"
    compileSdk = 37
    dynamicFeatures += setOf(":settings")

    signingConfigs {
        if (releaseSigningPropertiesFile.exists()) {
            create("release") {
                storeFile = file(requireNotNull(releaseSigningProperties.getProperty("storeFile")))
                storePassword =
                    requireNotNull(releaseSigningProperties.getProperty("storePassword"))
                keyAlias = requireNotNull(releaseSigningProperties.getProperty("keyAlias"))
                keyPassword = requireNotNull(releaseSigningProperties.getProperty("keyPassword"))
            }
        }
    }

    defaultConfig {
        applicationId = "com.example.userlistapp"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "com.example.userlistapp.HiltTestRunner"
        buildConfigField("String", "API_BASE_URL", "\"https://dummyjson.com/\"")
        buildConfigField("boolean", "USE_MODULAR_ACCOUNT", useModularAccount.get())
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            // Override with a staging URL when a non-production environment becomes available.
            buildConfigField("String", "API_BASE_URL", "\"https://dummyjson.com/\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseSigningPropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    experimentalProperties["android.experimental.enableScreenshotTest"] = true
    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    testOptions {
        unitTests.isIncludeAndroidResources = true
        screenshotTests.imageDifferenceThreshold = 0.0001f
    }
    sourceSets.getByName("androidTest").assets.directories.add("$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

kover {
    reports {
        filters {
            excludes {
                annotatedBy(
                    "androidx.compose.runtime.Composable",
                    "dagger.Module",
                    "dagger.hilt.InstallIn",
                )
                classes(
                    "*.BuildConfig",
                    "*.Hilt_*",
                    "*.*_Factory",
                    "*.*_MembersInjector",
                    "*.*JsonAdapter",
                    "com.example.userlistapp.MainActivity",
                    "com.example.userlistapp.UserListApplication",
                    "com.example.userlistapp.feature.*.*ScreenKt",
                    "com.example.userlistapp.feature.*.components.*",
                    "com.example.userlistapp.navigation.*",
                )
            }
        }
        verify {
            rule("Application line coverage") {
                minBound(40)
            }
        }
    }
}

dependencies {
    implementation(project(":core:navigation"))
    implementation(project(":feature:account"))

    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.metrics.performance)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.performance)
    implementation(libs.firebase.crashlytics)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    androidTestImplementation(libs.androidx.room.testing)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.play.feature.delivery)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotest.property)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
    kspAndroidTest(libs.androidx.room.compiler)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4.accessibility)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    screenshotTestImplementation(platform(libs.androidx.compose.bom))
    screenshotTestImplementation(libs.androidx.compose.ui.tooling)
    screenshotTestImplementation(libs.screenshot.validation.api)
}

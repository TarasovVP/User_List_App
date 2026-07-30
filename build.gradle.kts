plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.dynamic.feature) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.screenshot) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.firebase.performance) apply false
}

dependencies {
    kover(project(":app"))
    kover(project(":settings"))
}

kover {
    currentProject {
        createVariant("aggregatedDebug") {}
    }
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
                    "*.*_Factory*",
                    "*.*_MembersInjector*",
                    "*.*_HiltModules*",
                    "*.*_AssistedFactory*",
                    "*.*JsonAdapter",
                    "dagger.hilt.internal.aggregatedroot.codegen.*",
                    "hilt_aggregated_deps.*",
                    "com.example.userlistapp.di.*",
                    "com.example.userlistapp.MainActivity",
                    "com.example.userlistapp.UserListApplication",
                    "com.example.userlistapp.feature.*.*Activity",
                    "com.example.userlistapp.feature.*.*ScreenKt",
                    "com.example.userlistapp.feature.*.components.*",
                    "com.example.userlistapp.navigation.*",
                )
            }
        }
        variant("aggregatedDebug") {
            verify {
                rule("Aggregated debug line coverage") {
                    minBound(40)
                }
            }
        }
    }
}

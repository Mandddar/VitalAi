////import androidx.compose.ui.graphics.vector.group
////
////// settings.gradle.kts
////dependencyResolutionManagement {
////    repositories {
////        google()
////        mavenCentral()
////    }
////    resolutionStrategy {
////        eachDependency {
////            if (requested.group == "com.squareup" && requested.name == "javapoet") {
////                useVersion("1.13.0")
////            }
////        }
////    }
////}
//pluginManagement {
//    repositories {
//        google {
//            content {
//                includeGroupByRegex("com\\.android.*")
//                includeGroupByRegex("com\\.google.*")
//                includeGroupByRegex("androidx.*")
//            }
//        }
//        mavenCentral()
//        gradlePluginPortal()
//    }
//}
//dependencyResolutionManagement {
//    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
//    repositories {
//        google()
//        mavenCentral()
//
//        // ── JitPack ───────────────────────────────────────────────────────
//        // Required for MPAndroidChart (com.github.PhilJay:MPAndroidChart)
//        // and any other GitHub-hosted libraries not published to Maven Central.
//        maven { url = uri("https://jitpack.io") }
//
//        // ── SQLCipher ─────────────────────────────────────────────────────
//        // net.zetetic:android-database-sqlcipher is published to Zetetic's
//        // own maven repo, not Maven Central.
//        maven { url = uri("https://www.zetetic.net/maven") }
//    }
//}
//
//rootProject.name = "VitalAI"
//include(":app")




pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://www.zetetic.net/maven") }
    }
}

rootProject.name = "VitalAI"
include(":app")
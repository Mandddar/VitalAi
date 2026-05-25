//import androidx.compose.ui.graphics.colorspace.connect

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.google.services)
    // app/build.gradle.kts
//    alias(libs.plugins.hilt.android) // Ensure this points to 2.51.1
    alias(libs.plugins.firebase.crashlytics)
}

android {
    namespace = "com.vitalai.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.vitalai.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Room schema export directory
        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
                arguments["room.incremental"] = "true"
                arguments["room.expandProjection"] = "true"
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    // Allow duplicate META-INF files (common with Firebase + Kotlin)
    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/*.kotlin_module",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1"
            )
        }
    }

    // JUnit 5 support for unit tests
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.useJUnitPlatform()
            }
        }
    }
}

dependencies {

    // ─────────────────────────────────────────────
    // Kotlin & Core AndroidX
    // ─────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // ─────────────────────────────────────────────
    // Material Design 3
    // ─────────────────────────────────────────────
    implementation(libs.material)                         // com.google.android.material:material:1.11.0

    // ─────────────────────────────────────────────
    // Hilt — Dependency Injection (2.51.1)
    // ─────────────────────────────────────────────
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    // ─────────────────────────────────────────────
    // Room — Local Database (2.6.1)
    // ─────────────────────────────────────────────
    implementation(libs.room.runtime)                     // androidx.room:room-runtime:2.6.1
    implementation(libs.room.ktx)                         // androidx.room:room-ktx:2.6.1
    implementation(libs.room.rxjava3)                     // androidx.room:room-rxjava3:2.6.1
    kapt(libs.room.compiler)                              // androidx.room:room-compiler:2.6.1

    // ─────────────────────────────────────────────
    // Navigation Component (2.7.6)
    // ─────────────────────────────────────────────
    implementation(libs.navigation.fragment.ktx)          // androidx.navigation:navigation-fragment-ktx:2.7.6
    implementation(libs.navigation.ui.ktx)                // androidx.navigation:navigation-ui-ktx:2.7.6

    // ─────────────────────────────────────────────
    // Retrofit 2 + Gson Converter (2.9.0)
    // ─────────────────────────────────────────────
    implementation(libs.retrofit)                         // com.squareup.retrofit2:retrofit:2.9.0
    implementation(libs.retrofit.converter.gson)          // com.squareup.retrofit2:converter-gson:2.9.0
    implementation(libs.retrofit.adapter.rxjava3)         // com.squareup.retrofit2:adapter-rxjava3:2.9.0
    implementation(libs.okhttp)                           // com.squareup.okhttp3:okhttp
    implementation(libs.okhttp.logging.interceptor)       // com.squareup.okhttp3:logging-interceptor

    // ─────────────────────────────────────────────
    // RxJava 3 (3.1.8)
    // ─────────────────────────────────────────────
    implementation(libs.rxjava3)                          // io.reactivex.rxjava3:rxjava:3.1.8
    implementation(libs.rxandroid)                        // io.reactivex.rxjava3:rxandroid:3.0.2
    implementation(libs.rxkotlin)                         // io.reactivex.rxjava3:rxkotlin:3.0.1

    // ─────────────────────────────────────────────
    // Firebase (BOM manages inter-library versions)
    // ─────────────────────────────────────────────
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)                // com.google.firebase:firebase-auth-ktx:22.3.1
    implementation(libs.firebase.firestore.ktx)           // com.google.firebase:firebase-firestore-ktx:24.10.1
    implementation(libs.firebase.messaging.ktx)           // com.google.firebase:firebase-messaging-ktx:23.4.1
    implementation(libs.firebase.crashlytics.ktx)
    implementation(libs.firebase.analytics.ktx)

    // ─────────────────────────────────────────────
    // Google Sign-In (20.7.0)
    // ─────────────────────────────────────────────
    implementation(libs.play.services.auth)               // com.google.android.gms:play-services-auth:20.7.0

    // ─────────────────────────────────────────────
    // TensorFlow Lite (2.15.0)
    // ─────────────────────────────────────────────
    implementation(libs.tensorflow.lite)                  // org.tensorflow:tensorflow-lite:2.15.0
    implementation(libs.tensorflow.lite.support)          // org.tensorflow:tensorflow-lite-support:0.4.4
    implementation(libs.tensorflow.lite.metadata)         // org.tensorflow:tensorflow-lite-metadata:0.4.4
    implementation(libs.tensorflow.lite.gpu) {            // GPU delegate — optional acceleration
        exclude(group = "org.tensorflow", module = "tensorflow-lite")
    }

    // ─────────────────────────────────────────────
    // Health Connect (1.1.0-alpha07)
    // ─────────────────────────────────────────────
    implementation(libs.health.connect)                   // androidx.health.connect:connect-client:1.1.0-alpha07

    // ─────────────────────────────────────────────
    // Biometric (1.2.0-alpha05)
    // ─────────────────────────────────────────────
    implementation(libs.biometric)                        // androidx.biometric:biometric:1.2.0-alpha05

    // ─────────────────────────────────────────────
    // WorkManager (2.9.0)
    // ─────────────────────────────────────────────
    implementation(libs.work.runtime.ktx)                 // androidx.work:work-runtime-ktx:2.9.0
    implementation(libs.work.rxjava3)                     // androidx.work:work-rxjava3:2.9.0

    // UPDATED to 1.2.0 to solve version conflicts with Hilt 2.51.1
    implementation("androidx.hilt:hilt-work:1.2.0")
    kapt("androidx.hilt:hilt-compiler:1.2.0")

    // ─────────────────────────────────────────────
    // MPAndroidChart (v3.1.0)
    // ─────────────────────────────────────────────
    implementation(libs.mpandroidchart)                   // com.github.PhilJay:MPAndroidChart:v3.1.0

    // ─────────────────────────────────────────────
    // Glide — Image Loading (4.16.0)
    // ─────────────────────────────────────────────
    implementation(libs.glide)                            // com.github.bumptech.glide:glide:4.16.0
    kapt(libs.glide.compiler)                             // com.github.bumptech.glide:compiler:4.16.0

    // ─────────────────────────────────────────────
    // SQLCipher — Encrypted SQLite (4.5.6)
    // ─────────────────────────────────────────────
    implementation(libs.sqlcipher)                        // net.zetetic:sqlcipher-android:4.6.1
    implementation(libs.sqlite.ktx)                       // androidx.sqlite:sqlite-ktx (required by Room + SQLCipher)

    // ─────────────────────────────────────────────
    // OpenCSV (5.9)
    // ─────────────────────────────────────────────
    implementation(libs.opencsv)                          // com.opencsv:opencsv:5.9

    // ─────────────────────────────────────────────
    // LeakCanary — Memory Leak Detection (2.13)
    // ─────────────────────────────────────────────
    debugImplementation(libs.leakcanary)                  // com.squareup.leakcanary:leakcanary-android:2.13

    // ─────────────────────────────────────────────
    // Unit Testing — JUnit 5 + Mockito
    // ─────────────────────────────────────────────
    testImplementation(libs.junit.jupiter.api)            // org.junit.jupiter:junit-jupiter-api:5.10.1
    testImplementation(libs.junit.jupiter.params)         // org.junit.jupiter:junit-jupiter-params:5.10.1
    testRuntimeOnly(libs.junit.jupiter.engine)            // org.junit.jupiter:junit-jupiter-engine:5.10.1
    testImplementation(libs.mockito.core)                 // org.mockito:mockito-core:5.8.0
    testImplementation(libs.mockito.kotlin)               // org.mockito.kotlin:mockito-kotlin:5.2.1
    testImplementation(libs.mockito.inline)               // org.mockito:mockito-inline:5.2.0
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.room.testing)                 // androidx.room:room-testing:2.6.1

    // ─────────────────────────────────────────────
    // Android Instrumented Testing — Espresso
    // ─────────────────────────────────────────────
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.espresso.core)         // androidx.test.espresso:espresso-core:3.5.1
    androidTestImplementation(libs.espresso.contrib)      // androidx.test.espresso:espresso-contrib:3.5.1
    androidTestImplementation(libs.espresso.intents)      // androidx.test.espresso:espresso-intents:3.5.1
//    androidTestImplementation(libs.hilt.android.testing)  // com.google.dagger:hilt-android-testing:2.51.1
    kaptAndroidTest(libs.hilt.compiler)
    androidTestImplementation(libs.navigation.testing)    // androidx.navigation:navigation-testing:2.7.6
    androidTestImplementation(libs.work.testing)          // androidx.work:work-testing:2.9.0
}

kapt {
    correctErrorTypes = true
    arguments {
        arg("dagger.hilt.android.internal.disableAndroidSuperclassValidation", "true")
    }
}

// FORCE JAVAPOET VERSION TO FIX CANONICALNAME() ERROR
configurations.all {
    resolutionStrategy {
        force("com.squareup:javapoet:1.13.0")
    }
}
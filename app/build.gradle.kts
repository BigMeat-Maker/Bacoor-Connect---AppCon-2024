plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.example.bacoorconnect"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.bacoorconnect"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
        dataBinding = true
        viewBinding = true
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

    packaging {
        resources {
            excludes += "mozilla/public-suffix-list.txt"
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt"
            )
            pickFirsts += setOf(
                "META-INF/io.netty.versions.properties",
                "META-INF/native-image/io.netty/*"
            )
        }
    }

}

dependencies {

    implementation("com.google.firebase:firebase-config:21.6.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.google.firebase:firebase-auth:22.3.0")
    implementation (libs.azure.ai.vision.imageanalysis)
    implementation ("com.google.cloud:google-cloud-vision:3.11.0")
    implementation (libs.azure.azure.ai.formrecognizer)
    implementation (libs.play.services.location)
    implementation (libs.firebase.appcheck.playintegrity)
    implementation (libs.retrofit)
    implementation (libs.converter.gson)
    implementation (libs.firebase.appcheck.playintegrity.v1700)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.google.firebase.database)
    implementation(libs.google.firebase.storage)
    implementation (libs.work.runtime)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.osmdroid)
    implementation(libs.navigation.runtime)
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation(libs.monitor)
    implementation(libs.ext.junit)
    implementation(libs.play.services.location)
    implementation(libs.firebase.crashlytics.buildtools)
    testImplementation(libs.junit.junit)
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")
    implementation("com.google.android.gms:play-services-auth:20.7.0")
    implementation("com.sun.mail:android-mail:1.6.2")
    implementation("com.sun.mail:android-activation:1.6.2")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation ("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor ("com.github.bumptech.glide:compiler:4.16.0")
    implementation ("com.azure:azure-ai-vision-face:1.0.0-beta.2")
    implementation ("com.azure:azure-core:1.55.2")
    implementation ("com.squareup.okhttp3:okhttp:4.9.3")

    implementation ("com.squareup.retrofit2:retrofit:2.9.0")
    implementation ("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation ("com.squareup.okhttp3:okhttp:4.9.0")

    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")
}


apply(plugin = "com.google.gms.google-services")
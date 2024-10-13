plugins {
    id("com.android.application")
    id("com.chaquo.python")
}

android {
    namespace = "com.example.test"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.test"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a","x86")
        }
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}
chaquopy {
    defaultConfig {
        buildPython("C:/Users/fpt/AppData/Local/Programs/Python/Python310/python.exe")
        pip {
            install("numpy")
            install("scipy")
            install ("librosa==0.9.2")
            install ("resampy==0.3.1")
            install ("tensorflow==2.1.0")
        }
    }


}
buildscript {
    dependencies {
        classpath ("com.android.tools.build:gradle:8.2.0")
    }
}

dependencies {

    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
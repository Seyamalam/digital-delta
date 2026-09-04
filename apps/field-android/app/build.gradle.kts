import com.google.protobuf.gradle.id

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.protobuf)
  alias(libs.plugins.ksp)
  alias(libs.plugins.hilt)
}

android {
    namespace = "com.example.digitaldelta"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.example.digitaldelta"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        ndk {
            // The fair device set is ARM64; avoid shipping three unused ONNX native runtimes.
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    sourceSets.getByName("androidTest").assets.directories.add("$projectDir/schemas")
    sourceSets.getByName("main").assets.directories.add("$projectDir/../../../packages/scenario")

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.zxing.core)
  implementation(libs.protobuf.javalite)
  implementation(libs.grpc.okhttp)
  implementation(libs.grpc.protobuf.lite)
  implementation(libs.grpc.stub)
  compileOnly(libs.javax.annotation)
  protobuf(files("../../../packages/proto"))
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.datastore)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.hilt.android)
  implementation(libs.play.services.nearby)
  implementation(libs.onnxruntime.android)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.androidx.camera.mlkit.vision)
  implementation(libs.mlkit.barcode.scanning)
  implementation(libs.androidx.work.runtime)
  implementation(libs.maplibre.android)
  ksp(libs.hilt.compiler)
  ksp(libs.androidx.room.compiler)
  androidTestImplementation(libs.androidx.room.testing)
  androidTestImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.truth)
  androidTestImplementation(libs.truth)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)
}

ksp {
  arg("room.schemaLocation", file("$projectDir/schemas").path)
}

protobuf {
  protoc {
    artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
  }
  plugins {
    id("grpc") {
      artifact = "io.grpc:protoc-gen-grpc-java:${libs.versions.grpc.get()}"
    }
  }
  generateProtoTasks {
    all().configureEach {
      builtins {
        id("java") {
          option("lite")
        }
      }
      plugins {
        id("grpc") {
          option("lite")
        }
      }
    }
  }
}

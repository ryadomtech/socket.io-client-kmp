import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    id("com.android.application")
    id("kotlin-multiplatform")

    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidTarget()

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    jvm("desktop")

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "SocketIOSample"
        browser {
            val rootDirPath = project.rootDir.path
            val projectDirPath = project.projectDir.path
            commonWebpackConfig {
                outputFileName = "SocketIOSample.js"
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    static = (static ?: mutableListOf()).apply {
                        // Serve sources to debug inside browser
                        add(rootDirPath)
                        add(projectDirPath)
                    }
                }
            }
        }

        binaries.executable()
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.activity.compose)
        }

        commonMain.dependencies {
            implementation(projects.lib)

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.material3)
            implementation(compose.components.resources)

            implementation(libs.logging)
        }

        named("desktopMain") {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

android {
    namespace = "tech.ryadom.kio.sample"
    compileSdk = 36

    defaultConfig {
        applicationId = "tech.ryadom.kio.sample"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
}

compose.desktop {
    application {
        mainClass = "tech.ryadom.socketio.client.sample.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "tech.ryadom.kio.sample"
            packageVersion = "1.0.0"
        }
    }
}

afterEvaluate {
    tasks {
        val configureJs: Task.() -> Unit = {
            dependsOn(named("wasmJsDevelopmentExecutableCompileSync"))
            dependsOn(named("wasmJsProductionExecutableCompileSync"))
            dependsOn(named("wasmJsTestTestDevelopmentExecutableCompileSync"))
        }

        named("wasmJsBrowserProductionWebpack").configure(configureJs)
    }
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = JavaVersion.VERSION_11.toString()
    targetCompatibility = JavaVersion.VERSION_11.toString()
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions.jvmTarget = JvmTarget.JVM_11
}
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.publishing)
}

private val rootVersion = "0.0.3"

kotlin {
    jvm()

    iosArm64()
    iosSimulatorArm64()
    iosX64()

    js(IR) {
        browser {
        }
        binaries.executable()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        nodejs()
        binaries.executable()
    }

    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
                }
            }
        }

        publishLibraryVariants("release")
    }

    applyDefaultHierarchyTemplate()
    sourceSets {
        all {
            languageSettings.optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
            languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
        }

        commonMain {
            dependencies {
                implementation(libs.socketioParser)
                api(libs.ktor.client.core)
                api(libs.ktor.client.logging)
                api(libs.ktor.client.websockets)
                implementation(libs.atomicfu)
            }
        }

        androidMain {
            dependencies {
                implementation(libs.ktor.client.okhttp)
            }
        }

        jvmMain {
            dependencies {
                implementation(libs.ktor.client.cio)
            }
        }

        appleMain {
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }

        jsMain {
            dependencies {
                implementation(libs.ktor.client.js)
            }
        }

        wasmJsMain {
            dependencies {
                implementation(libs.ktor.client.wasm)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}

android {
    namespace = "tech.ryadom.kio"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)

    signAllPublications()

    coordinates(
        groupId = "tech.ryadom",
        artifactId = "kio",
        version = rootVersion
    )

    pom {
        name.set("Socket IO client for Kotlin Multiplatform")
        description.set("Socket IO client for Kotlin Multiplatform")
        inceptionYear.set("2025")
        url.set("https://github.com/ryadomtech/kmp-socketio-client")

        licenses {
            license {
                name = "MIT"
                url = "https://opensource.org/licenses/MIT"
                distribution = "https://opensource.org/licenses/MIT"
            }
        }

        developers {
            developer {
                id.set("adkozlovskiy")
                name.set("Alexey Kozlovsky")
                email.set("adkozlovskiy@gmail.com")
            }
        }

        scm {
            url.set("https://github.com/ryadomtech/kmp-socketio-client")
            connection.set("scm:git:git://github.com/ryadomtech/kmp-socketio-client.git")
            developerConnection.set("scm:git:ssh://git@github.com/ryadomtech/kmp-socketio-client.git")
        }
    }
}
# Not official Socket IO lib. for usage in Kotlin Multiplatform

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.0-blue.svg?style=flat&logo=kotlin)](https://kotlinlang.org)
[![Maven Central](https://img.shields.io/maven-central/v/tech.ryadom/kio?color=blue)](https://central.sonatype.com/artifact/tech.ryadom/kio)

![badge-android](http://img.shields.io/badge/platform-android-6EDB8D.svg?style=flat)
![badge-ios](http://img.shields.io/badge/platform-ios-CDCDCD.svg?style=flat)
![badge-desktop](https://img.shields.io/badge/platform-desktop-3474eb.svg?style=flat)
![badge-js](https://img.shields.io/badge/platform-js-fcba03.svg?style=flat)
![badge-wasm](https://img.shields.io/badge/platform-wasm-331f06.svg?style=flat)

## Supported targets

| Target          | Implemented | Tested |
|-----------------|-------------|--------|
| **Android**     | ☑           | ☑      |
| **iOS**         | ☑           | ☑      |
| **JVM Desktop** | ☑           | ☑      |
| **JS**          | ☑           | ☑      |
| **WasmJS**      | ☑           | ☑      |

### Implementation

In your shared module's build.gradle.kts add:

```Gradle Kotlin DSL
kotlin.sourceSets.commonMain.dependencies {
  implementation("tech.ryadom:kio:0.0.5")
}
```

### Usage
```Kotlin
val socket: Socket = kioSocket("https://yourdomain.com") {
    
    // Configure min. log level or set custom logger
    logging {
        level = LogLevel.INFO
    }

    // Configure socket.io options
    options {
        isSecure = true
        allowCredentials = true
    }
}

socket.on("event") {} // For specific event
socket.once("event") {} // Once for specific event
socket.onAny {} // For any events
```

### Support

If you find a bug or want to contribute an improvement, please create an Issue or send an email to
opensource@ryadom.tech.
Any support will be appreciated.

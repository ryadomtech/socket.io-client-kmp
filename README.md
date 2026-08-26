# Not official Socket IO lib. for usage in Kotlin Multiplatform

[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.0-blue.svg?style=flat&logo=kotlin)](https://kotlinlang.org)
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
  implementation("tech.ryadom:kio:1.0.0")
}
```

### Creation
```Kotlin
val socket: Socket = kioSocket("https://yourdomain.com") {
    // Configure min. log level or set custom logger
    logging {
        logLevel(LogLevel.INFO)
    }

    // Configure socket.io options
    options {
        auth = mapOf("token" to "your_auth_token")
        reconnectionAttempts = 5
    }
}

socket.open()
```

The scheme, host and port are taken from the URI. Its path, if any, is used as the namespace,
so `https://yourdomain.com/admin` connects to the `/admin` namespace.

### Listening
```Kotlin
// For specific event
socket.on("connect") {
}

// Once for specific event
socket.once("event") {
}

// For any events
socket.onAny {
}
```

### Sending
```Kotlin
val args = // create your packet
socket.emit("event", args)

// WARN: Do not send json like this. It will be sent as a plain string
socket.emit("event", Json.encodeToString(args))

// Do like that:
socket.emit("event", Json.encodeToJsonElement(args))
```

Binary payloads are sent as `ByteString` and arrive as `ByteString`:

```Kotlin
socket.emit("upload", "avatar.png".encodeToByteString())
```

### Acknowledgements
```Kotlin
// Ask the server to acknowledge an event
socket.emit("event", args, Ack { response ->
    // response holds whatever the server answered with
})

// Acknowledge an event the server expects an answer for.
// The Ack is appended as the last argument of the event
socket.on("event") { args ->
    (args.last() as Ack).call("done")
}
```

### Support

If you find a bug or want to contribute an improvement, please create an Issue or send an email to
opensource@ryadom.tech.
Any support will be appreciated.

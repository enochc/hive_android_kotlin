# hive_android_kotlin

[Hive](https://github.com/enochc/hive) for Android: a library that runs a hive inside your app and keeps it connected, and a small app for trying it against a real one. Built for Hive 0.3.0.

The library does not reimplement Hive's protocol. It loads the Rust crate itself (`libhive.so`) and talks to it through JNI, so it speaks whatever the crate speaks, including TLS and shared-secret auth when the library is built with them.

| Module | What it is |
|---|---|
| `hive` | The library. `HiveService`, `HiveClient`, `HiveConfig`, and `NativeHive`, the thin JNI class under them. |
| `app` | "Hive Tester", one screen that uses the service: connect, watch and edit properties, list peers, send messages. |

## Building

The native half comes from the hive crate and is not checked in. Build it once, and again whenever the crate changes:

```bash
./build-native.sh            # expects the crate in ../hive, or pass its path
```

That needs a Rust toolchain with [cargo-ndk](https://github.com/bbqsrc/cargo-ndk), the Android targets and an NDK:

```bash
cargo install cargo-ndk
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android i686-linux-android
```

It writes `libhive.so` for all four ABIs to `hive/src/main/jniLibs` and refreshes `NativeHive.kt` from the crate, since the two have to match. `HIVE_FEATURES=tls,auth ./build-native.sh` turns on TLS and auth. Gradle refuses to build while the libraries are missing.

After that it is an ordinary Android project: open it in Android Studio, or

```bash
./gradlew :app:installDebug
```

## Using the service

Add the `hive` module to your app. Its manifest brings the service declaration and the INTERNET permission with it.

```kotlin
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val service = HiveServiceConnection(app).also { it.bind() }
    val hive: StateFlow<HiveClient?> = service.client   // null until bound
    override fun onCleared() = service.close()
}
```

```kotlin
model.hive.filterNotNull().collect { hive ->
    hive.start(HiveConfig(name = "Phone", connect = "192.168.1.10:3000", names = listOf("lightValue")))

    launch { hive.state.collect { /* Stopped, Connecting, Running(peers), Failed(reason) */ } }
    launch { hive.properties.collect { list -> /* HiveProperty(id, name, value) */ } }
    launch { hive.peers.collect { /* the server and its other clients */ } }
    launch { hive.messages.collect { text -> } }
}

hive.set("lightValue", 42)          // String, Boolean, Int/Long or Float/Double
hive.delete("lightValue")
hive.sendToPeer("Server", "hello")
```

`HiveService` keeps the hive alive for as long as something is bound to it, so bind from a ViewModel or the Application rather than an Activity. `HiveClient` is the same API without the service, if you would rather own the lifetime yourself.

Things worth knowing:

- **Properties arrive without names.** Hives exchange property ids (a hash of the name), never the names. A property is reported with its name only if this hive was told it: in `HiveConfig.properties` or `names`, by `set(name, …)`, or by `registerName`. Otherwise `name` is null and `label` is the id in hex. You can still set or delete it by id.
- **It is a mirror.** `set` and `delete` ask the hive; `properties` changes when the hive reports the result, the same as for changes made elsewhere.
- **Reconnecting.** A client that loses its server goes to `Connecting` and reconnects by itself. While the server is unreachable it retries every 10 seconds.
- **Not a foreground service.** The hive stops with the last binding, and Android may stop the process once the app is off screen.
- **Shrinking.** The library ships a consumer rule that keeps `com.enochc.hive`, which the native code looks up by name.

## Trying it

Run a hive on your computer. The crate has an example for this, which prints what happens and takes commands on stdin:

```bash
cargo run --example serve -- examples/listen_3000.toml
```

```
lightValue = 42          set a property
-lightValue              delete it
@Android hello there     send a message to the hive called Android
```

Start the app on an emulator and press Connect. The default address, `10.0.2.2:3000`, is how an emulator reaches the computer it runs on; on a phone enter the computer's address, and have the hive listen on it rather than on `127.0.0.1`.

## Tests

`hive/src/androidTest` runs the service against a second hive inside the test process, on the device's loopback, so it needs no server:

```bash
./gradlew :hive:connectedDebugAndroidTest
```

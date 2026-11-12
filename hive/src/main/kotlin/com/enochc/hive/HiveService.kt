package com.enochc.hive

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Keeps one hive running for as long as anything in the app is bound to
 * it, so the connection belongs to the app rather than to whichever screen
 * happens to be showing. Bind with [HiveServiceConnection] and use the
 * [HiveClient] it hands over.
 *
 * The library's manifest declares the service, not exported: it is for the
 * app it is built into. It is not a foreground service, so the hive goes
 * down with the last binding, and Android may stop the process soon after
 * the app leaves the screen.
 */
class HiveService : Service() {
    /** The hive this service runs. Start it with [HiveClient.start]. */
    val client = HiveClient()

    inner class LocalBinder : Binder() {
        val client: HiveClient get() = this@HiveService.client
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        client.close()
        super.onDestroy()
    }
}

/**
 * A binding to the app's [HiveService].
 *
 * Hold one somewhere that outlives configuration changes, a ViewModel or
 * the Application; bound from an Activity directly, the hive would be torn
 * down and reconnected on every rotation.
 *
 *     val hive = HiveServiceConnection(context).also { it.bind() }
 *     hive.client.filterNotNull().collect { client ->
 *         client.start(HiveConfig(name = "Phone", connect = "192.168.1.10:3000"))
 *         client.properties.collect { ... }
 *     }
 */
class HiveServiceConnection(context: Context) : AutoCloseable {
    private val context = context.applicationContext
    private val _client = MutableStateFlow<HiveClient?>(null)
    private var bound = false

    /** The service's client, once bound; null before that and after [close]. */
    val client: StateFlow<HiveClient?> = _client.asStateFlow()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            _client.value = (service as HiveService.LocalBinder).client
        }

        override fun onServiceDisconnected(name: ComponentName) {
            _client.value = null
        }
    }

    /** Bind, creating the service if this is its first binding. Call from the main thread. */
    fun bind() {
        if (!bound) {
            bound = context.bindService(Intent(context, HiveService::class.java), connection, Context.BIND_AUTO_CREATE)
        }
    }

    /** Unbind. The service, and its hive, stop once nothing else is bound. */
    override fun close() {
        if (bound) {
            context.unbindService(connection)
            bound = false
            _client.value = null
        }
    }
}

package com.enochc.hive

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

/**
 * A property and its current value: a [String], [Boolean], [Long] or
 * [Double]. [name] is null while the hive hasn't learned it, which with
 * [HiveConfig.syncNames] is only until the server has answered.
 */
data class HiveProperty(val id: Long, val name: String?, val value: Any) {
    /** The name, or the id in hex for a property this hive doesn't know the name of. */
    val label: String get() = name ?: "#" + java.lang.Long.toHexString(id)
}

/** Where a [HiveClient] stands. */
sealed interface HiveState {
    /** Not started, or stopped. */
    data object Stopped : HiveState

    /** A client looking for its server: before the first connection and after losing one. */
    data object Connecting : HiveState

    /**
     * Up. A client is connected to its server. A server is listening, with
     * [peers] clients attached (possibly none).
     */
    data class Running(val peers: Int) : HiveState

    /** The hive would not start, usually because of its config. */
    data class Failed(val reason: String) : HiveState
}

/**
 * A hive and everything it knows, as flows. This is the API of
 * [HiveService], and can equally be used on its own.
 *
 * It is a mirror: [set] and [delete] only ask, and [properties] changes
 * when the hive reports the result, the same way it does for changes made
 * by other hives.
 *
 * A client that loses its server goes back to [HiveState.Connecting] and is
 * restarted after [reconnectDelayMs], until [stop]. Its properties start
 * over from the config each time, then fill in from the server.
 *
 * All methods can be called from any thread and return without waiting for
 * the network. The flows can be collected anywhere.
 */
class HiveClient(private val reconnectDelayMs: Long = 2000) : AutoCloseable {
    private val _properties = MutableStateFlow<List<HiveProperty>>(emptyList())
    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    private val _state = MutableStateFlow<HiveState>(HiveState.Stopped)
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Every property the hive has, in the order they first appeared. */
    val properties: StateFlow<List<HiveProperty>> = _properties.asStateFlow()

    /**
     * The other hives on a client's server, the server first, or nothing
     * while disconnected. Stays empty for a hive that listens.
     */
    val peers: StateFlow<List<Peer>> = _peers.asStateFlow()

    val state: StateFlow<HiveState> = _state.asStateFlow()

    /**
     * Messages other hives sent to this one. Only delivered to collectors
     * active at the time; a slow one loses the oldest beyond 64.
     */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    // Starting and stopping happen on this one thread, so they can't
    // interleave, and never block a caller on a hive shutting down.
    private val worker = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "hive-client").apply { isDaemon = true }
    }

    // Worker thread only.
    private var config: HiveConfig? = null

    // Written on the worker thread, read anywhere.
    @Volatile private var hive: NativeHive? = null
    @Volatile private var generation = 0

    private val names = ConcurrentHashMap<Long, String>()
    private val table = LinkedHashMap<Long, HiveProperty>()

    /** Start a hive from [config], replacing the one running, if any. */
    fun start(config: HiveConfig) {
        worker.execute {
            shutdown()
            names.clear()
            this.config = config
            launch()
        }
    }

    /** Disconnect. [properties] and [peers] empty out. */
    fun stop() {
        worker.execute {
            shutdown()
            config = null
            reset(HiveState.Stopped)
        }
    }

    /** [stop], for good: a closed client can't be started again. */
    override fun close() {
        stop()
        worker.shutdown()
    }

    /**
     * Set a property, creating it if it doesn't exist. [value] is a
     * [String], [Boolean], [Int]/[Long] or [Float]/[Double]. Does nothing
     * while the hive isn't started.
     */
    fun set(name: String, value: Any) {
        val hive = hive ?: return
        names[NativeHive.propertyId(name)] = name
        when (value) {
            is String -> hive.set(name, value)
            is Boolean -> hive.set(name, value)
            is Byte, is Short, is Int, is Long -> hive.set(name, (value as Number).toLong())
            is Float, is Double -> hive.set(name, (value as Number).toDouble())
            else -> throw unsupported(value)
        }
    }

    /** [set] for a property known only by id, as [HiveProperty.id] gives it. */
    fun set(id: Long, value: Any) {
        val hive = hive ?: return
        when (value) {
            is String -> hive.set(id, value)
            is Boolean -> hive.set(id, value)
            is Byte, is Short, is Int, is Long -> hive.set(id, (value as Number).toLong())
            is Float, is Double -> hive.set(id, (value as Number).toDouble())
            else -> throw unsupported(value)
        }
    }

    /** Delete a property here and on every connected hive. */
    fun delete(name: String) {
        hive?.delete(name)
    }

    fun delete(id: Long) {
        hive?.delete(id)
    }

    /**
     * Tell the hive what a property is called. One already in [properties]
     * under its id gets the name straight away. Returns the property's id.
     */
    fun registerName(name: String): Long {
        val id = NativeHive.propertyId(name)
        names[id] = name
        hive?.registerName(name)
        synchronized(table) {
            val known = table[id]
            if (known != null && known.name == null) {
                table[id] = known.copy(name = name)
                _properties.value = table.values.toList()
            }
        }
        return id
    }

    /**
     * Send [message] to the hive called [peer], one of [peers]. Delivery
     * isn't confirmed. False if the hive isn't started.
     */
    fun sendToPeer(peer: String, message: String): Boolean = hive?.sendToPeer(peer, message) ?: false

    private fun unsupported(value: Any) = IllegalArgumentException(
        "${value.javaClass.simpleName} is not a Hive value; use a String, Boolean, Long or Double"
    )

    private fun launch() {
        val config = config ?: return
        val mine = ++generation
        reset(if (config.listen != null) HiveState.Running(0) else HiveState.Connecting)
        try {
            for (name in config.properties.keys + config.names) {
                names[NativeHive.propertyId(name)] = name
            }
            val started = NativeHive(config.toToml(), Events(mine, reconnect = config.listen == null))
            hive = started
            config.names.forEach { started.registerName(it) }
            if (config.connect != null) started.requestPeers()
            if (config.syncNames) started.requestNames()
        } catch (e: IllegalArgumentException) {
            fail(e.message ?: "the config was refused")
        } catch (e: LinkageError) {
            fail("libhive.so could not be loaded: ${e.message}")
        }
    }

    private fun fail(reason: String) {
        config = null
        _state.value = HiveState.Failed(reason)
    }

    /** Stop the running hive. Its queued events are delivered before this returns. */
    private fun shutdown() {
        hive?.close()
        hive = null
        generation++
    }

    private fun reset(state: HiveState) {
        synchronized(table) {
            table.clear()
            _properties.value = emptyList()
        }
        _peers.value = emptyList()
        _state.value = state
    }

    /** One started hive's events. Those of a hive that has since been replaced are dropped. */
    private inner class Events(private val mine: Int, private val reconnect: Boolean) : HiveListener {
        override fun onProperty(id: Long, name: String?, value: Any?) {
            if (mine != generation) return
            synchronized(table) {
                if (value == null) {
                    table.remove(id)
                } else {
                    table[id] = HiveProperty(id, name ?: names[id], value)
                }
                _properties.value = table.values.toList()
            }
        }

        override fun onPropertyName(id: Long, name: String) {
            if (mine != generation) return
            names[id] = name
            synchronized(table) {
                val known = table[id]
                if (known != null && known.name == null) {
                    table[id] = known.copy(name = name)
                    _properties.value = table.values.toList()
                }
            }
        }

        override fun onPeers(peers: List<Peer>) {
            if (mine == generation) _peers.value = peers
        }

        override fun onPeerMessage(message: String) {
            if (mine == generation) _messages.tryEmit(message)
        }

        override fun onConnection(connected: Boolean, peers: Int) {
            if (mine != generation) return
            if (connected || !reconnect) {
                _state.value = HiveState.Running(peers)
                return
            }
            // A hive doesn't go looking for its server again; a new one does.
            // Peers first, so whoever reacts to the state finds them gone.
            _peers.value = emptyList()
            _state.value = HiveState.Connecting
            try {
                worker.schedule({
                    if (mine == generation) {
                        shutdown()
                        launch()
                    }
                }, reconnectDelayMs, TimeUnit.MILLISECONDS)
            } catch (_: RejectedExecutionException) {
                // Closed in the meantime; nothing to reconnect.
            }
        }
    }
}

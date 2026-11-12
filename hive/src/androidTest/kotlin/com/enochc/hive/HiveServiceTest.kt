package com.enochc.hive

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The service and client against a real hive: a second hive in this same
 * process, listening on the device's loopback. Everything goes through
 * libhive.so, so this is also the test that the native library loads and
 * its JNI layer works on Android.
 */
@RunWith(AndroidJUnit4::class)
class HiveServiceTest {
    // A client that finds nothing listening tries again 10 seconds later.
    private val limitMs = 30_000L

    private val server = HiveClient()
    private val binding = HiveServiceConnection(ApplicationProvider.getApplicationContext<Context>())

    @After
    fun tearDown() {
        onMain { binding.close() }
        server.close()
    }

    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    private fun <T> StateFlow<T>.await(what: String, accept: (T) -> Boolean): T = runBlocking {
        withTimeoutOrNull(limitMs) { first(accept) } ?: throw AssertionError("never saw $what; last was $value")
    }

    /** Run [send], then return the next thing [flow] emits. */
    private fun <T> next(flow: Flow<T>, send: () -> Unit): T = runBlocking {
        val received = async(start = CoroutineStart.UNDISPATCHED) { flow.first() }
        send()
        withTimeout(limitMs) { received.await() }
    }

    private fun List<HiveProperty>.value(name: String) = firstOrNull { it.id == NativeHive.propertyId(name) }?.value

    private fun bindPhone(): HiveClient {
        onMain { binding.bind() }
        return binding.client.await("the service to bind") { it != null }!!
    }

    @Test
    fun aClientMirrorsItsServerThroughTheService() {
        val served = mapOf("light" to 1, "label" to "hi", "ratio" to 1.5, "on" to true)
        server.start(HiveConfig(name = "Serv", listen = "127.0.0.1:35120", properties = served))
        server.properties.await("the server's own properties") { it.size == served.size }

        val phone = bindPhone()
        assertEquals(HiveState.Stopped, phone.state.value)
        phone.start(HiveConfig(name = "Phone", connect = "127.0.0.1:35120", names = listOf("light")))
        phone.state.await("the connection") { it == HiveState.Running(1) }
        server.state.await("the server counting the phone") { it == HiveState.Running(1) }

        // The snapshot, typed, with a name only where the phone was given one.
        val snapshot = phone.properties.await("the server's properties") { it.size == served.size }
        assertEquals(1L, snapshot.value("light"))
        assertEquals("hi", snapshot.value("label"))
        assertEquals(1.5, snapshot.value("ratio"))
        assertEquals(true, snapshot.value("on"))
        assertEquals(listOf("light"), snapshot.mapNotNull { it.name })
        assertTrue(snapshot.first { it.value == "hi" }.label.startsWith("#"))

        // Naming a property applies to what is already there.
        phone.registerName("label")
        assertEquals("label", phone.properties.value.first { it.value == "hi" }.name)

        // Changes travel both ways, by name and by id.
        phone.set("volume", 7)
        phone.properties.await("its own new property") { list -> list.any { it.name == "volume" && it.value == 7L } }
        server.properties.await("the phone's property") { it.value("volume") == 7L }
        phone.set(NativeHive.propertyId("ratio"), 2.5)
        server.properties.await("a change by id") { it.value("ratio") == 2.5 }
        server.set("label", "there")
        phone.properties.await("the server's change") { it.value("label") == "there" }
        phone.set("on", false)
        server.properties.await("a boolean") { it.value("on") == false }

        phone.delete("light")
        server.properties.await("the delete") { it.value("light") == null }
        assertNull(phone.properties.await("its own delete") { it.value("light") == null }.value("light"))

        // Peers and messages.
        assertEquals(listOf("Serv", "Phone"), phone.peers.await("the peer list") { it.size == 2 }.map { it.name })
        assertEquals("hello server", next(server.messages) { assertTrue(phone.sendToPeer("Serv", "hello server")) })
        assertEquals("hello phone", next(phone.messages) { assertTrue(server.sendToPeer("Phone", "hello phone")) })

        // Lose the server: the client says so, then finds it again by itself.
        server.stop()
        phone.state.await("the lost connection") { it == HiveState.Connecting }
        assertTrue(phone.peers.value.isEmpty())
        server.start(HiveConfig(name = "Serv", listen = "127.0.0.1:35120", properties = mapOf("light" to 5)))
        phone.state.await("the reconnection") { it == HiveState.Running(1) }
        phone.properties.await("the new server's properties") { it.value("light") == 5L }

        phone.stop()
        phone.state.await("the stop") { it == HiveState.Stopped }
        assertTrue(phone.properties.value.isEmpty())
        server.state.await("the server losing the phone") { it == HiveState.Running(0) }
    }

    @Test
    fun aRefusedConfigIsReportedAsFailed() {
        // This build of libhive.so has no TLS, which the hive refuses to ignore.
        server.start(HiveConfig(name = "Serv", listen = "127.0.0.1:35121", extraToml = "[TLS]\ncert = \"a\"\nkey = \"b\"\n"))
        val failed = server.state.await("the failure") { it is HiveState.Failed } as HiveState.Failed
        assertTrue(failed.reason, failed.reason.contains("tls"))
    }

    @Test
    fun configValuesSurviveBeingWrittenAsToml() {
        val odd = mapOf(
            "we\"ird \\ name" to "two\nlines, a \"quote\" and a ☃",
            "big" to Long.MIN_VALUE,
            "tiny" to 1e-9,
            "whole" to 2.0f,
        )
        server.start(HiveConfig(name = "Se\"rv", listen = "127.0.0.1:35122", properties = odd))
        val held = server.properties.await("the properties") { it.size == odd.size }.associate { it.name to it.value }
        assertEquals(
            mapOf(
                "we\"ird \\ name" to "two\nlines, a \"quote\" and a ☃",
                "big" to Long.MIN_VALUE,
                "tiny" to 1e-9,
                "whole" to 2.0,
            ),
            held,
        )
    }
}

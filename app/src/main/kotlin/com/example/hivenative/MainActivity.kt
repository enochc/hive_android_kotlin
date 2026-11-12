package com.example.hivenative

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.enochc.hive.HiveClient
import com.enochc.hive.HiveConfig
import com.enochc.hive.HiveProperty
import com.enochc.hive.HiveServiceConnection
import com.enochc.hive.HiveState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Holds the binding to the Hive service, so the hive outlives rotation. */
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val service = HiveServiceConnection(app).also { it.bind() }
    val client: StateFlow<HiveClient?> = service.client

    override fun onCleared() = service.close()
}

/**
 * One screen for trying the Hive service against a real hive: connect,
 * watch and change properties, see who else is there and message them.
 */
class MainActivity : AppCompatActivity() {
    private val model: MainViewModel by viewModels()
    private val client: HiveClient? get() = model.client.value

    private lateinit var server: EditText
    private lateinit var hiveName: EditText
    private lateinit var connect: Button
    private lateinit var status: TextView
    private lateinit var received: TextView
    private lateinit var properties: PropertyAdapter
    private lateinit var peers: ArrayAdapter<String>
    private var messageCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        server = findViewById(R.id.server)
        hiveName = findViewById(R.id.hive_name)
        connect = findViewById(R.id.connect)
        status = findViewById(R.id.status)
        received = findViewById(R.id.received)

        // 10.0.2.2 is the machine an emulator runs on.
        val saved = getPreferences(Context.MODE_PRIVATE)
        server.setText(saved.getString("server", "10.0.2.2:3000"))
        hiveName.setText(saved.getString("name", "Android"))

        connect.setOnClickListener {
            val client = client ?: return@setOnClickListener
            when (client.state.value) {
                HiveState.Stopped, is HiveState.Failed -> {
                    val address = server.text.toString().trim()
                    val name = hiveName.text.toString().trim().ifEmpty { "Android" }
                    saved.edit().putString("server", address).putString("name", name).apply()
                    client.start(HiveConfig(name = name, connect = address))
                }
                else -> client.stop()
            }
        }

        val propertyName = findViewById<EditText>(R.id.property_name)
        val propertyValue = findViewById<EditText>(R.id.property_value)
        findViewById<Button>(R.id.property_set).setOnClickListener {
            val name = propertyName.text.toString().trim()
            val value = propertyValue.text.toString()
            if (name.isEmpty()) return@setOnClickListener
            if (value.isEmpty()) {
                // Names normally come from the server. One older than
                // Hive 0.3.0 doesn't send them, and its properties show as
                // #id until they are named here.
                client?.registerName(name)
            } else {
                val current = properties.items.firstOrNull { it.name == name }?.value
                client?.set(name, parse(value, like = current))
                propertyValue.setText("")
            }
        }

        properties = PropertyAdapter(this)
        findViewById<ListView>(R.id.properties).apply {
            adapter = properties
            setOnItemClickListener { _, _, position, _ -> edit(properties.items[position]) }
            setOnItemLongClickListener { _, _, position, _ ->
                confirmDelete(properties.items[position])
                true
            }
        }

        peers = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, ArrayList<String>())
        val peerPicker = findViewById<Spinner>(R.id.peers).apply { adapter = peers }
        val message = findViewById<EditText>(R.id.message)
        findViewById<Button>(R.id.send).setOnClickListener {
            val to = peerPicker.selectedItem as String?
            val text = message.text.toString()
            if (to != null && text.isNotEmpty() && client?.sendToPeer(to, text) == true) {
                message.setText("")
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.client.collectLatest { client ->
                    if (client == null) {
                        status.text = "Starting the Hive service"
                        connect.isEnabled = false
                    } else {
                        connect.isEnabled = true
                        watch(client)
                    }
                }
            }
        }
    }

    private suspend fun watch(client: HiveClient) = coroutineScope {
        launch {
            client.state.collect { state ->
                status.text = when (state) {
                    HiveState.Stopped -> "Disconnected"
                    HiveState.Connecting -> "Connecting"
                    is HiveState.Running -> "Connected"
                    is HiveState.Failed -> "Failed: ${state.reason}"
                }
                val stopped = state is HiveState.Stopped || state is HiveState.Failed
                connect.setText(if (stopped) R.string.connect else R.string.disconnect)
                server.isEnabled = stopped
                hiveName.isEnabled = stopped
            }
        }
        launch { client.properties.collect { properties.show(it) } }
        launch {
            client.peers.collect { list ->
                val me = hiveName.text.toString().trim()
                peers.clear()
                peers.addAll(list.map { it.name }.filter { it != me })
                peers.notifyDataSetChanged()
            }
        }
        launch {
            client.messages.collect { text ->
                messageCount++
                received.text = "Message $messageCount: $text"
            }
        }
    }

    /** Booleans flip on a tap; anything else gets a dialog to type the new value into. */
    private fun edit(property: HiveProperty) {
        val value = property.value
        if (value is Boolean) {
            set(property, !value)
            return
        }
        val input = EditText(this).apply {
            setText(value.toString())
            setSingleLine()
            selectAll()
        }
        AlertDialog.Builder(this)
            .setTitle(property.label)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ -> set(property, parse(input.text.toString(), like = value)) }
            .show()
    }

    private fun confirmDelete(property: HiveProperty) {
        AlertDialog.Builder(this)
            .setTitle("Delete ${property.label}?")
            .setMessage("It is deleted on every connected hive.")
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = property.name
                if (name != null) client?.delete(name) else client?.delete(property.id)
            }
            .show()
    }

    private fun set(property: HiveProperty, value: Any) {
        val name = property.name
        if (name != null) client?.set(name, value) else client?.set(property.id, value)
    }
}

/**
 * Read typed text as a Hive value. A property keeps its type when the text
 * fits it (so a string holding "72" stays a string); a new property is a
 * boolean, an integer, a float or a string, whichever the text reads as
 * first.
 */
fun parse(text: String, like: Any?): Any {
    val trimmed = text.trim()
    return when (like) {
        is String -> text
        is Double -> trimmed.toDoubleOrNull() ?: text
        is Long -> trimmed.toLongOrNull() ?: trimmed.toDoubleOrNull() ?: text
        else -> when (trimmed.lowercase()) {
            "true" -> true
            "false" -> false
            else -> trimmed.toLongOrNull() ?: trimmed.toDoubleOrNull() ?: text
        }
    }
}

private class PropertyAdapter(context: Context) :
    ArrayAdapter<HiveProperty>(context, android.R.layout.simple_list_item_2, android.R.id.text1, ArrayList()) {

    var items: List<HiveProperty> = emptyList()
        private set

    fun show(properties: List<HiveProperty>) {
        items = properties
        clear()
        addAll(properties)
        notifyDataSetChanged()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val row = super.getView(position, convertView, parent)
        val property = items[position]
        row.findViewById<TextView>(android.R.id.text1).text = property.label
        row.findViewById<TextView>(android.R.id.text2).text = "${property.value}   (${property.value.javaClass.simpleName})"
        return row
    }
}

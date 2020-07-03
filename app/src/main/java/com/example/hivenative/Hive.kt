package com.example.hivenative

import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.moandjiezana.toml.Toml
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import java.io.OutputStream
import java.net.ConnectException
import java.net.Socket
import java.net.SocketException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

// name, property, index
class PropType(val name:String, val property:Hive.Property, val doRemove:Int? = null)

val TAG ="Hive <<"
fun debug(s:String) = Log.d(TAG, s)

class Hive {
    private var connection: Socket? = null
    var connected: Boolean = false
    private var writer: OutputStream? = null

    val propertyChannel:Channel<PropType> = Channel()

    fun disconnect() {

        connected = false
        connection?.close()
    }


    suspend fun connect(address: String, port: Int): Flow<PropType> {
        if (!connected) {
            try {
                connection = Socket(address, port)
                connected = true
                setOrAddProperty(PropType("connected", Property("$address:$port")))
                writer = connection?.getOutputStream()
            } catch (e: ConnectException) {
                return flow {
                    setOrAddProperty(PropType("connected", Property("Failed to connect")))
                }
            }

        }

        debug("Connected to server at $address on port $port")

        // starts the messages consumer that needs to run in a coroutine scope to collect
        // messages over the socket
        GlobalScope.launch {
            messages().collect{
                debug( "socket message: $it")
            }
        }

        return properties()
    }

    // this reads from the channel
    suspend fun properties():Flow<PropType> {
        return flow {
            for (p in _properties){
                emit(p)
            }

            propertyChannel.consumeEach {
                setOrAddProperty(it)
                emit(it)
            }
        }
    }


    @ExperimentalUnsignedTypes
    private fun messages(): Flow<String> {
        val inputStream = connection?.getInputStream()

        return flow {
            while (connected) {
                try {
                    val bytes = ByteArray(4)
                    inputStream?.read(bytes)
                    val size = byteArrayToInt(*bytes)
                    val msgBytes = ByteArray(size)
                    inputStream?.read(msgBytes)

                    var msg = msgBytes.toString(Charset.defaultCharset())
                    if(msg.isEmpty()){
                        // no data received is usually a sign that the socket has been disconnected
                        throw SocketException()
                    }
                    debug("data received: $msg")

                    msg.substring(0,3)
                    msg = msg.substring(3)

                    val toml = Toml().read(msg)
                    for ((name, value) in toml.entrySet()) {
                        val prop = PropType(name, Property(value))
                        propertyChannel.send(prop)
                    }

                    emit(msg)
                } catch (e: SocketException) {
                    println("Socket Closed")
                    _properties.clear()
                    connected = false
                    propertyChannel.send(PropType("connected", Property("Closed"), doRemove = 0))

                }
            }
        }
    }


    fun write(message: String) {
        if (connected) {
            GlobalScope.launch {
                withContext(Dispatchers.IO){
                    val msgByts = (message).toByteArray(Charset.defaultCharset());
                    println("writing: ${msgByts.size}")
                    val sBytes = intToByteArray(msgByts.size)
                    writer?.write(sBytes)
                    writer?.write(msgByts)
                    writer?.flush()
                    println("written")
                }
            }
        }
    }

    @ExperimentalUnsignedTypes
    private fun byteArrayToInt(vararg byte: Byte): Int {
        return (byte[0].toUByte().toInt().shl(24) +
                byte[1].toUByte().toInt().shl(16) +
                byte[2].toUByte().toInt().shl(8) +
                byte[3].toUByte().toInt().shl(0)).toInt()
    }

    private fun intToByteArray(value: Int): ByteArray {
        val buffer = ByteBuffer.allocate(4)
        buffer.order(ByteOrder.BIG_ENDIAN) // BIG_ENDIAN is default byte order, so it is not necessary.
        buffer.putInt(value)
        return buffer.array()
    }

    //    private val _properties:HashMap<String, Property> = hashMapOf<String, Property>()
    private val _properties: MutableList<PropType> = mutableListOf(PropType("connected", Property("no")))
    val propertyList:List<PropType>
        get() {
            return _properties
        }

    fun deleteProperty(name:String):Int {
        for ((i,p) in _properties.withIndex()) {
            if(p.name ==name) {
                write("|d|${p.name}")
                _properties.removeAt(i)
                return i
            }
        }
        return -1
    }

    fun setOrAddProperty(pt:PropType){
        val p = getProperty(pt.name)
        if(p != null) {
            p.property.set(pt.property)
        } else{
            _properties.add(pt)
        }
    }

    fun getProperty(name:String):PropType? {
        for (p in _properties) {
            if(p.name == name) {
                return p
            }
        }
        return null
    }


    inner class Property(default:Any) {

        var onChanged: ArrayList<((Any) -> Unit)> = arrayListOf()

        var value = default
            set(value) {
                if(value != field){
                    field = value
                    for(v in onChanged.iterator()){
                        v(value)
                    }
                }
            }

        fun set(other:Property){
            this.value = other.value
        }

        fun connect(fn:(Any)->Unit){
            onChanged.add(fn)
        }

        fun save() {
            val h = this@Hive.write("${this@Property}=${value}")
        }

    }
}
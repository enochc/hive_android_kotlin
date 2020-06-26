package com.example.hivenative

import android.util.Log
import kotlinx.coroutines.*
import com.moandjiezana.toml.Toml
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.consume
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.net.Socket
import java.net.SocketException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

typealias PropType = Pair<String, Hive.Property>

val TAG ="Hive <<"
fun debug(s:String) = Log.d(TAG, s)

class Hive {
    private var connection: Socket? = null
    var connected: Boolean = false
    private var writer: OutputStream? = null

//    val propertyChannel:ConflatedBroadcastChannel<propType> = ConflatedBroadcastChannel()
    val propertyChannel:Channel<PropType> = Channel()

    fun disconnect() {

        connected = false
        connection?.close()
    }

    suspend fun connect(address: String, port: Int): Flow<PropType> {
        if (!connected) {
            connection = Socket(address, port)
            connected = true

            writer = connection?.getOutputStream()

        }

        debug("Connected to server at $address on port $port")
        GlobalScope.launch {
            messages().collect{
                debug( "socket message: $it")
            }
        }

        return properties()
    }


    suspend fun properties():Flow<PropType> {
        return flow {
            for ((name, property) in _properties){
                emit(PropType(name, property))
            }
            propertyChannel.consumeEach {
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
                    connected = false
                }
            }
        }
    }


    fun write(message: String) {
        if (connected) {
            val msgByts = (message).toByteArray(Charset.defaultCharset());
            println("writing: ${msgByts.size}")
            val sBytes = intToByteArray(msgByts.size)
            writer?.write(sBytes)
            writer?.write(msgByts)
            writer?.flush()
            println("written")
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

    val _properties:HashMap<String, Property> = hashMapOf<String, Property>()

    public fun setProperty(name:String, value:Any){
        if(_properties.containsKey(name)){
            _properties[name]?.value = value
        } else {
            _properties[name] = Property(value)
        }
    }

    public fun getProperty(name:String) = _properties[name]

    public fun getOrSetProperty(name:String, value:Any):Property{
        return if(_properties.containsKey(name)){
            _properties[name]!!
        } else {
            val p = Property(value)
            _properties[name] = p
            p
        }
    }



    inner class Property(default:Any) {

        public var onChanged: ArrayList<((Any) -> Unit)> = arrayListOf()

        var value = default
        set(value) {
            if(value != field){
                field = value
                for(v in onChanged.iterator()){
                    v(value)
                }
            }
        }

        fun connect(fn:(Any)->Unit){
            onChanged.add(fn)
        }

        fun save() {
            val h = this@Hive.write("${this@Property}=${value}")
        }

    }
}
package com.example.hivenative

import android.util.Log
import com.moandjiezana.toml.Toml
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.net.ConnectException
import java.net.Socket
import java.net.SocketException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

// name, property, index
class PropType(val name:String, val property:Hive.Property)

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
            try {
                connection = Socket(address, port)
                connected = true

                writer = connection?.getOutputStream()
            }catch (e: ConnectException) {
                return flow {
                    val e = Property("Failed to connect")
                    _properties.add(PropType("Error", e))
                    emit(PropType("Error", e))
                }
            }

        }

        debug("Connected to server at $address on port $port")

        // starts the messages consumer that needs to run in a coroutene scope to collect
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
                val p = getProperty(it.name)
                if(p != null) {
                    p.property.set(it.property)
                } else{
                    _properties.add(it)
                }
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

    //    private val _properties:HashMap<String, Property> = hashMapOf<String, Property>()
    private val _properties: MutableList<PropType> = mutableListOf()
    val propertyList:List<PropType>
        get() {
            return _properties
        }


    fun setProperty(name:String, value:Any){
        val p = getProperty(name)
        if(p != null){
            p.property.value = value
        } else {
            _properties.add(PropType(name, Property(value)))
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

//    public fun getOrSetProperty(name:String, value:Any):Property{
//        return if(_properties.containsKey(name)){
//            _properties[name]!!
//        } else {
//            val p = Property(value)
//            _properties[name] = p
//            p
//        }
//    }



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
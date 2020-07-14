package com.example.hivenative

import android.util.Log
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

class PropType(val name:String, val property:Hive.Property, val doRemove:Int? = null)
class Peer(val name:String, val address:String){
    override fun toString():String {
        return "\"${this.name}\": ${this.address}"
    }
}

val TAG ="Hive <<"
fun debug(s:String) = Log.d(TAG, s)

val DELETE = "|d|"
val HEADER = "|H|"
val PROPERTIES = "|P|"
val PROPERTY = "|p|"
val REQUEST_PEERS = "<p|"
val ACK  = "<<|";
val PEER_MESSAGE = "|s|"
val PEER_MESSAGE_DIV = "|=|"

class Hive(val name:String ="Android client") {
    private var connection: Socket? = null
    var connected: Boolean = false
    set(c:Boolean) {
        field = c
        connectedChanged?.invoke(field)
    }

    private var writer: OutputStream? = null

    val propertyChannel: Channel<PropType> = Channel()
    private val messageChanel: Channel<String> = Channel()

    fun disconnect() {

        connected = false
        connection?.close()
    }

    var connectedChanged: ((Boolean)->Unit)? = null
    var peersChanged:(()->Unit)? = null

    fun onConnectedChanged(f: (Boolean)->Unit) {
        connectedChanged = f
    }

    suspend fun peerMessages(): Flow<String> {
        return flow{
            messageChanel.consumeEach {
                emit(it)
            }
        }
    }


    suspend fun connect(address: String, port: Int): Flow<PropType> {
        if (!connected) {
            try {
                connection = Socket(address, port)
                connected = true
                writer = connection?.getOutputStream()

                // send header with peer name then request peer updates
                // TODO move request_peers into the header message
                write("${HEADER}NAME=${this.name}")
                write(REQUEST_PEERS)
            } catch (e: ConnectException) {
                debug("Error: $e")
                connected = false
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
    private suspend fun properties():Flow<PropType> {
        return flow {
            for (p in _properties){
                emit(p)
            }

            propertyChannel.consumeEach {
                if(it.doRemove == null) {
                    setOrAddProperty(it)
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
                    if(msg.isEmpty()){
                        // no data received is usually a sign that the socket has been disconnected
                        throw SocketException()
                    }
                    debug("data received: $msg")

                    val msgType = msg.substring(0,3)
                    msg = msg.substring(3)

                    if(msgType == HEADER) {
                        //TODO maybe do something with this? the name of the server Hive
                        val name = msg.split("NAME=")[1]
                        println("HEADER NAME: $name")
                    } else if(msgType == DELETE) { // delete message

                        for ((i,p) in _properties.withIndex()) {
                            if(p.name == msg) {
                                debug("remove|| $msg")
                                _properties.removeAt(i)
                                val prop = PropType(msg, Property(null), i)
                                propertyChannel.send(prop)
                                break
                            }
                        }
                    }else if(msgType == PROPERTY || msgType == PROPERTIES) {
                        val toml = Toml().read(msg)
                        for ((name, value) in toml.entrySet()) {
                            val prop = PropType(name, Property(value))
                            propertyChannel.send(prop)
                        }

                        emit(msg)
                    }else if(msgType == ACK) {
                        println("ACK RECEIVED")
                    }else if(msgType == REQUEST_PEERS) {
                        println("<<<< RECEIVED PEERS $msg")
                        _peers.clear()
                        for ( p in msg.split(",").iterator()) {
                            val x = p.split("|")
                            _peers.add(x[0])
                        }
                        peersChanged?.invoke()
                    } else if(msgType == PEER_MESSAGE) {
                        println("Received Peer Message: $msg")
                        messageChanel.send(msg)
                    } else {
                        println("ERROR: unknown message: $msg")
                    }



                } catch (e: SocketException) {
                    println("Socket Closed")
                    _properties.clear()
                    connected = false
                }
            }
        }
    }


    private fun write(message: String) {
        if (connected) {
            runBlocking {
                withContext(Dispatchers.IO){
                    val msgByts = (message).toByteArray(Charset.defaultCharset());
                    val sBytes = intToByteArray(msgByts.size)
                    println("<<<< writing: ${message}")
                    writer?.write(sBytes)
                    writer?.write(msgByts)
                    writer?.flush()
                    println("written")
                }
            }
        }
    }

    fun writeToPeer(peerName:String, msg:String){
        val msg = "$PEER_MESSAGE$peerName$PEER_MESSAGE_DIV$msg"
        write(msg)
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

    private val _peers:MutableList<String> = mutableListOf()
    val peersList:List<String>
    get() {
        return _peers
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

    private fun setOrAddProperty(pt:PropType){
        val p = getProperty(pt.name)
        if(p != null) {
            p.property.set(pt.property)
        } else{
            _properties.add(pt)
        }
    }

    fun updateProperty(prop_name:String, value:String) {
        val p = getProperty(prop_name)
        p?.property?.set(Property(value))
        val msg = "$PROPERTY${prop_name}=\"${value}\""
        write(msg)
    }

    private fun getProperty(name:String):PropType? {
        for (p in _properties) {
            if(p.name == name) {
                return p
            }
        }
        return null
    }


    inner class Property(default:Any?) {

        var onChanged: ArrayList<((Any?) -> Unit)> = arrayListOf()

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

        fun connect(fn:(Any?)->Unit){
            onChanged.add(fn)
        }

        fun save() {
            val h = this@Hive.write("${this@Property}=${value}")
        }
    }
}
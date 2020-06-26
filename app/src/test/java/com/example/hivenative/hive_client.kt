package com.example.hivenative

import kotlinx.coroutines.runBlocking
import java.io.OutputStream
import java.net.Socket
import java.nio.charset.Charset
import java.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import java.io.PrintStream
import java.io.PrintWriter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.system.*
import kotlin.concurrent.thread
import kotlin.properties.Delegates


@ExperimentalUnsignedTypes
fun aiba(vararg byte: Byte): Int {
    return (byte[0].toUByte().toInt().shl(24) +
            byte[1].toUByte().toInt().shl(16) +
            byte[2].toUByte().toInt().shl(8) +
            byte[3].toUByte().toInt().shl(0)).toInt()
}

@ExperimentalUnsignedTypes
fun main(args: Array<String>) {
    val address = "127.0.0.1"
    val port = 3000

    val client = Client(address, port)

    thread {
        runBlocking {
            client.messages().collect{
                println("message -- $it")
            }
        }
    }


    println("Subscribed")

    Thread.sleep(1000)
    client.write("some dumb message")

//    var observed = false
//
//    println(max) // 0
//    println("observed is ${observed}") // false
//
//    max = 10
//    println(max) // 10
//    println("observed is ${observed}") // true


}

fun intToByteArray(value: Int):ByteArray {
    val bufferSize = Int.SIZE_BYTES
    val buffer = ByteBuffer.allocate(4)
    buffer.order(ByteOrder.BIG_ENDIAN) // BIG_ENDIAN is default byte order, so it is not necessary.
    buffer.putInt(value)

    return buffer.array()
}

class Client(address: String, port: Int) {
    private val connection: Socket = Socket(address, port)
    private var connected: Boolean = true

    init {
        println("Connected to server at $address on port $port")
    }
    private val inputStream = connection.getInputStream()

    private val writer: OutputStream = connection.getOutputStream()

    @ExperimentalUnsignedTypes
    fun messages(): Flow<String> {
        return flow{
            while (connected) {
                val bytes = ByteArray(4)
                inputStream.read(bytes)
                assert(bytes.size == 4)

//                val size = aiba(bytes[0], bytes[1], bytes[2], bytes[3])
                val size = aiba(*bytes)
                val msgBytes = ByteArray(size)
                inputStream.read(msgBytes)
                val msg = msgBytes.toString(Charset.defaultCharset())

                emit(msg.substring(3, msg.length))
            }
        }
    }



    fun write(message: String) {
        val msgByts = (message).toByteArray(Charset.defaultCharset());
        println("writing: ${msgByts.size}")
        val sBytes = intToByteArray(msgByts.size)
        writer.write(sBytes)
        writer.write(msgByts)
        writer.flush()
        println("written")
    }
}
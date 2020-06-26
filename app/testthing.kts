package kotlinx.sockets.examples

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import kotlinx.sockets.adapters.*
import java.net.*
import java.nio.*


fun main(args: Array<String>) {
    runBlocking { // start coroutines
        aSocket().tcp().connect(InetSocketAddress(InetAddress.getByName("google.com"), 80)).use { socket ->
            println("Connected") // now we are connected

            // chain of async write
            socket.send("GET / HTTP/1.1\r\n")
            socket.send("Host: google.com\r\n")
            socket.send("Accept: text/html\r\n")
            socket.send("Connection: close\r\n")
            socket.send("\r\n")

            // loop to read bytes and write to the console
            val bb = ByteBuffer.allocate(8192)
            while (true) {
                bb.clear()
                if (socket.read(bb) == -1) break // async read

                bb.flip()
                System.out.write(bb)
                System.out.flush()
            }

            println()
        }
    }
}

main(["one","two"])
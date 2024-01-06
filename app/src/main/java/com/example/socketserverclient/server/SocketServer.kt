package com.example.socketserverclient.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Enumeration

class SocketServer(private val port: Int) {

    fun startServer() = runBlocking {
        try {
            val serverSocket = ServerSocket(port)
            println("Server is listening on port $port")

            while (true) {
                val clientSocket = serverSocket.accept()
                println("New client connected: ${clientSocket.inetAddress.hostAddress}")

                // Handle the client in a coroutine
                launch(Dispatchers.IO) {
                    handleClient(clientSocket)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun handleClient(clientSocket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
            val writer = PrintWriter(OutputStreamWriter(clientSocket.getOutputStream()), true)

            while (true) {
                // Read incoming messages
                val message = reader.readLine()
                if (message == null) {
                    println("Client disconnected: ${clientSocket.inetAddress.hostAddress}")
                    break
                }
                println("Received message from ${clientSocket.inetAddress.hostAddress}: $message")

                // Process the message (you can add your own logic here)

                // Send a response back to the client
                val response = "Server received: $message"
                writer.println(response)
                println("Sent response to ${clientSocket.inetAddress.hostAddress}: $response")
            }

            // Close resources
            reader.close()
            writer.close()
            clientSocket.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getLocalIpv4Address(): String? {
        var ipv4Address: String? = null
        try {
            val networkInterfaces = NetworkInterface.getNetworkInterfaces()
            while (networkInterfaces.hasMoreElements()) {
                val networkInterface = networkInterfaces.nextElement()
                val inetAddresses = networkInterface.inetAddresses
                while (inetAddresses.hasMoreElements()) {
                    val inetAddress = inetAddresses.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is InetAddress && inetAddress.isSiteLocalAddress && inetAddress.hostAddress.indexOf(':') == -1) {
                        // Check for IPv4 and non-loopback address
                        ipv4Address = inetAddress.hostAddress
                        break
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ipv4Address
    }


}
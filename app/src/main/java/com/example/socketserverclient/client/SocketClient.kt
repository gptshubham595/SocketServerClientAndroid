package com.example.socketserverclient.client

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.socketserverclient.helper.CoroutinesAsyncTask
import com.example.socketserverclient.models.IpAddressModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.OutputStream
import java.net.Socket


class SocketClient(private val ipModel: IpAddressModel) :
    CoroutinesAsyncTask<Void?, Void?, String?>(), DefaultLifecycleObserver {

    private val TAG = SocketClient::class.java.simpleName

    private val dstAddress: String?
    private val dstPort: Int?
    private var clientSocket: Socket? = null

    private var outputStream: OutputStream? = null
    private lateinit var dataOutputStream: DataOutputStream

    private var lifecycleOwner: LifecycleOwner? = null
    private lateinit var socketClientListener: SocketClientListener

    init {
        this.dstAddress = ipModel.ipAddress
        this.dstPort = ipModel.portNumber
    }

    fun setListener(listener: SocketClientListener) {
        socketClientListener = listener
    }

    interface SocketClientListener {
        fun onMessageSent(message: String)
        fun onMessageReceived(message: String)
    }

    fun setLifeCycleObserver(listener: LifecycleOwner) {
        this.lifecycleOwner = listener
        this.lifecycleOwner?.lifecycle?.addObserver(this)
    }

    private val coroutineScope by lazy {
        lifecycleOwner?.lifecycleScope!!
    }

    override fun onPreExecute() {
        super.onPreExecute()
        Log.d(TAG, " Started the sending!")
    }

    private suspend fun connectToClient(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, " ip address = $dstAddress port number $dstPort")

                if (clientSocket == null) {
                    dstAddress?.let {
                        dstPort?.let {
                            clientSocket = Socket(dstAddress, dstPort)
                        }
                    }
                }

                val isConnected = clientSocket?.isConnected == true
                Log.d(TAG, "clientSocket status $isConnected")
                isConnected
            } catch (e: Exception) {
                Log.e(TAG, "Message not sent, Receiver Offline ${e.stackTraceToString()}")
                false // Return false on failure
            }
        }
    }

    override fun doInBackground(vararg params: Void?): String? {
        coroutineScope.launch(Dispatchers.IO) {

            handleConnection(onResult = { isConnected ->
                if (isConnected) {
                    try {
                        clientSocket?.let {
                            Log.d(TAG, "client socket isConnected ${it.isConnected}")
                            while (it.isConnected) {

                                val dataInputStream = DataInputStream(it.getInputStream())

                                // Read the incoming data into a byte array
                                val buffer = ByteArray(1024)
                                val bytesRead = dataInputStream.read(buffer)

                                if (bytesRead != -1) {
                                    // Process the received data assuming it is a String
                                    val receivedMessage = String(buffer, 0, bytesRead)
                                    Log.d(TAG, "Received Message: $receivedMessage")
                                    socketClientListener?.onMessageReceived(receivedMessage)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            })
        }
        return null
    }

    override fun onPostExecute(receivedStatus: String?) {
    }

    fun handleConnection(onResult: (Boolean) -> Unit = {}) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                if (clientSocket?.isConnected == true) {
                    //already connected
                    onResult(true)
                } else {
                    val job = async { connectToClient() }.await()
                    onResult(job)
                }
            } catch (e: java.lang.Exception) {
                Log.e("SendMessageFile", "Message not sent, Receiver Offline")
                e.printStackTrace()
            }
        }
    }


    fun handleSendingMessage(message: String) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                outputStream = clientSocket?.getOutputStream()
                if (outputStream == null) {
                    handleConnection()
                    return@launch
                } else {
                    dataOutputStream = DataOutputStream(outputStream)
                    val msgBytes = message.toByteArray()
                    dataOutputStream.write(msgBytes)
                    dataOutputStream.flush()
                    Log.i(TAG, "Sent Message: $message")
                    socketClientListener?.onMessageSent(message)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Message not sent, ${e.stackTraceToString()}")
                e.stackTraceToString()
            }
        }
    }

}
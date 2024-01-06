package com.example.socketserverclient

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.socketserverclient.client.SocketClient
import com.example.socketserverclient.databinding.ActivityMainBinding
import com.example.socketserverclient.models.IpAddressModel
import com.example.socketserverclient.server.SocketServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Enumeration

class MainActivity : AppCompatActivity(), SocketClient.SocketClientListener {
    private lateinit var socketClient: SocketClient
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //start server
        val socketServer = SocketServer(8080)
        socketServer.startServer()
        Log.d("SocketServer", "Server started ${socketServer.getLocalIpv4Address()}:${8080}")


        binding.btnConnect.setOnClickListener {
            socketClient = SocketClient(
                IpAddressModel(
                    binding.etIpPort.text.split(':')[0],
                    binding.etIpPort.text.split(':')[1].toInt()
                )
            )
            socketClient.setLifeCycleObserver(this)
            socketClient.setListener(this)
            socketClient.execute()
        }

        binding.btnSend.setOnClickListener {
            if (::socketClient.isInitialized) {
                socketClient.handleSendingMessage(binding.textSendMsg.text.toString())
            }
        }

        binding.btnClearSendMsgBox.setOnClickListener {
            binding.textSendMsg.setText("")
        }

        binding.btnClear.setOnClickListener {
            binding.textRecvdMsg.setText("")
        }
    }

    override fun onMessageSent(message: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            Toast.makeText(this@MainActivity, "Message sent $message", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onMessageReceived(message: String) {
        binding.textRecvdMsg.append(message)
    }

}
package com.example.myapplication

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.engineio.client.EngineIOException
import org.json.JSONException
import org.json.JSONObject
import java.net.URISyntaxException

object SocketHandler {

    lateinit var mSocket: Socket

    @Synchronized
    fun setSocket() {
        try {
            mSocket = IO.socket("http://220.69.208.235:8080")

            Log.e("Socket", "Connect")
        } catch (e: URISyntaxException) {
            Log.e("Socket", "DisConnect")
        }
    }

    @Synchronized
    fun getSocket(): Socket {
        return mSocket
    }

    @Synchronized
    fun establishConnection() {
        mSocket.connect()

        mSocket.on(io.socket.client.Socket.EVENT_CONNECT) {
            // 소켓 서버에 연결이 성공하면 호출됩니다.
            Log.i("Socket", "Connect")
        }?.on(io.socket.client.Socket.EVENT_DISCONNECT) { args ->
            // 소켓 서버 연결이 끊어질 경우에 호출됩니다.
            Log.i("Socket", "Disconnet: ${args[0]}")
        }?.on(Socket.EVENT_CONNECT_ERROR) { args ->
            // 소켓 서버 연결 시 오류가 발생할 경우에 호출됩니다.
            var errorMessage = ""
            if (args[0] is EngineIOException) {
                var err = args[0] as EngineIOException

                errorMessage = "code: ${err.code}  message: ${err.message}"
            }
            Log.e("Socket", "Connect Error: $errorMessage")
        }
    }

    @Synchronized
    fun closeConnection() {
        mSocket.disconnect()
    }


}
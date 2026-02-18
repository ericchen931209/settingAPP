package com.example.settingapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.*
import kotlin.concurrent.thread

class RobotTcpManager {
    private var cmdSocket: Socket? = null
    private var writer: PrintWriter? = null
    private var heartbeatTimer: Timer? = null

    // 回調：通知介面更新地圖、更新電量/狀態
    var onMapReceived: ((Bitmap) -> Unit)? = null
    var onStatusUpdate: ((String, Int, String) -> Unit)? = null

    fun connect(ip: String, onStatus: (String) -> Unit) {
        thread {
            try {
                // 連接 8900 指令端口
                cmdSocket = Socket(ip, 8900)
                writer = PrintWriter(cmdSocket?.getOutputStream(), true)

                startHeartbeat()    // 啟動 5 秒心跳，維持連線不中斷
                startEventListener() // 啟動 8901 伺服器，等待機器人連回傳資料
                onStatus("連線成功")
            } catch (e: Exception) { onStatus("連線失敗: ${e.message}") }
        }
    }

    private fun startHeartbeat() {
        heartbeatTimer?.cancel()
        heartbeatTimer = Timer()
        heartbeatTimer?.schedule(object : TimerTask() {
            override fun run() {
                writer?.print("{AO_CHECK_CLIENT_HEARTBEAT}\n") // 官方規範心跳包
                writer?.flush()
            }
        }, 0, 5000)
    }

    fun sendCommand(command: String, parameter: String) {
        thread {
            try {
                val json = JSONObject()
                json.put("Command", command)
                json.put("Para", parameter)
                writer?.print(json.toString() + "\n") // 指令必須 LF 結尾
                writer?.flush()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun startEventListener() {
        thread {
            try {
                val serverSocket = ServerSocket(8901)
                while (true) {
                    val client = serverSocket.accept()
                    val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                    val line = reader.readLine()
                    if (line != null) {
                        val json = JSONObject(line)
                        // 1. 處理 5.56 節的地圖資料
                        if (json.optString("Command") == "GetMapData") {
                            val b64 = json.optString("Data")
                            if (b64.isNotEmpty()) {
                                val bytes = Base64.decode(b64, Base64.DEFAULT)
                                onMapReceived?.invoke(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                            }
                        }
                        // 2. 處理狀態資料（假設包含 Battery 與 Mode）
                        val batt = json.optInt("Battery", -1)
                        val mode = json.optString("Mode", "待機")
                        if (batt != -1) onStatusUpdate?.invoke("● 已連線", batt, mode)
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun close() {
        heartbeatTimer?.cancel()
        cmdSocket?.close()
    }
}
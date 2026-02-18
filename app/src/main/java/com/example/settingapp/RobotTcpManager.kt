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

    // 定義一個回標函式，當地圖影像解碼完成後通知 UI
    var onMapReceived: ((Bitmap) -> Unit)? = null

    // 1. 建立連線並啟動心跳與監聽
    fun connect(ip: String, onStatus: (String) -> Unit) {
        thread {
            try {
                // AMR 官方指令端口為 8900
                cmdSocket = Socket(ip, 8900)
                writer = PrintWriter(cmdSocket?.getOutputStream(), true)

                startHeartbeat()    // 啟動 5 秒一次的心跳包
                startEventListener() // 啟動 8901 監聽器接收地圖

                onStatus("連線成功 (Port 8900)")
            } catch (e: Exception) {
                onStatus("連線失敗: ${e.message}")
            }
        }
    }

    // 2. 依照規範發送心跳包，頻率為每 5 秒一次
    private fun startHeartbeat() {
        heartbeatTimer?.cancel()
        heartbeatTimer = Timer()
        heartbeatTimer?.schedule(object : TimerTask() {
            override fun run() {
                // AMR 必須接收到此字串才不會斷開連線
                writer?.print("{AO_CHECK_CLIENT_HEARTBEAT}\n")
                writer?.flush()
            }
        }, 0, 5000)
    }

    // 3. 發送 JSON 指令 (例如 5.9 節的手動控制)
    fun sendCommand(command: String, parameter: String) {
        thread {
            try {
                val json = JSONObject()
                json.put("Command", command)
                json.put("Para", parameter)
                // 每個 JSON 指令結尾必須加上 \n (LF)
                writer?.print(json.toString() + "\n")
                writer?.flush()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // 4. 監聽 8901 接收地圖數據 (依據 5.56 節 GetMapData)
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
                        // 判斷是否為地圖回傳數據
                        if (json.optString("Command") == "GetMapData") {
                            val base64Data = json.optString("Data")
                            if (base64Data.isNotEmpty()) {
                                // 將 Base64 轉為 Bitmap 圖片
                                val imageBytes = Base64.decode(base64Data, Base64.DEFAULT)
                                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                                onMapReceived?.invoke(bitmap)
                            }
                        }
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
package com.example.settingapp

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

    // 1. 連接指令端口 8900
    fun connect(ip: String, onStatus: (String) -> Unit) {
        thread {
            try {
                // AMR 預設指令端口為 8900
                cmdSocket = Socket(ip, 8900)
                writer = PrintWriter(cmdSocket?.getOutputStream(), true)

                // 連線成功後，啟動心跳包
                startHeartbeat()
                // 啟動 8901 事件監聽器
                startEventListener()

                onStatus("連線成功 (Port 8900)")
            } catch (e: Exception) {
                onStatus("連線失敗: ${e.message}")
            }
        }
    }

    // 2. 定期發送心跳包 (每 5 秒一次)，格式為 {AO_CHECK_CLIENT_HEARTBEAT}
    private fun startHeartbeat() {
        heartbeatTimer?.cancel()
        heartbeatTimer = Timer()
        heartbeatTimer?.schedule(object : TimerTask() {
            override fun run() {
                // 文件規範：心跳包格式
                writer?.print("{AO_CHECK_CLIENT_HEARTBEAT}\n")
                writer?.flush()
            }
        }, 0, 5000)
    }

    // 3. 發送 JSON 格式指令
    fun sendCommand(command: String, parameter: String) {
        thread {
            try {
                val json = JSONObject()
                json.put("Command", command)
                json.put("Para", parameter)

                // 每個指令後必須加上 \n (LF)
                writer?.print(json.toString() + "\n")
                writer?.flush()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // 4. 監聽 8901 端口 (AMR 會主動連回此端口傳送地圖與狀態)
    private fun startEventListener() {
        thread {
            try {
                val serverSocket = ServerSocket(8901)
                while (true) {
                    val client = serverSocket.accept()
                    val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                    val line = reader.readLine()
                    if (line != null) {
                        // 這裡接收來自機器人的事件通知 (例如電量、地圖更新)
                        println("收到 AMR 事件: $line")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun close() {
        heartbeatTimer?.cancel()
        cmdSocket?.close()
    }
}
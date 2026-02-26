package com.example.settingapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.io.*
import java.net.*
import java.util.*
import kotlin.concurrent.thread

class RobotTcpManager {
    private var cmdSocket: Socket? = null
    private var writer: PrintWriter? = null
    private var eventServerSocket: ServerSocket? = null
    private var isRunning = false

    var onMapReceived: ((Bitmap) -> Unit)? = null
    var onStatusUpdate: ((String, Int, String) -> Unit)? = null

    fun connect(ip: String, onStatus: (String) -> Unit) {
        Log.d("RobotTCP", "遵循協議 V1.18.0 啟動連線...")
        isRunning = true

        thread {
            try {
                // 1. 先啟動本地監聽器 (Port 8901)，PDF 規定 AMR 會連過來
                startEventServer()

                // 2. 建立連向 AMR 的 Socket (Port 8900)
                val socket = Socket()
                // 強制綁定乙太網路 IP
                socket.bind(InetSocketAddress("192.168.168.150", 0))
                socket.connect(InetSocketAddress(ip, 8900), 5000)

                cmdSocket = socket
                writer = PrintWriter(BufferedWriter(OutputStreamWriter(socket.getOutputStream(), "UTF-8")), true)

                Log.d("RobotTCP", "成功連線至 Port 8900")
                onStatus("已連線")

                // 3. 啟動指令回應監聽
                listenToCmdResponse()

            } catch (e: Exception) {
                Log.e("RobotTCP", "連線失敗: ${e.message}")
                onStatus("連線失敗: 機器人拒絕")
            }
        }
    }

    // PDF 2-1.2 規定：上層主控需監聽 Port 8901
    private fun startEventServer() {
        thread {
            try {
                eventServerSocket = ServerSocket(8901)
                Log.d("RobotTCP", "本地 8901 監聽器已啟動")
                while (isRunning) {
                    val client = eventServerSocket?.accept() ?: break
                    thread {
                        val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                        val data = reader.readLine()
                        if (data != null) {
                            handleIncomingJson(data)
                        }
                        client.close()
                    }
                }
            } catch (e: Exception) { Log.e("RobotTCP", "8901 監聽異常: ${e.message}") }
        }
    }

    private fun handleIncomingJson(jsonStr: String) {
        try {
            val json = JSONObject(jsonStr)
            Log.d("RobotTCP", "收到資料: $jsonStr")

            // 解析地圖數據 (PDF 第 5.56 節)
            if (json.optString("Command") == "GetMapData") {
                val b64 = json.optString("Data")
                if (b64.isNotEmpty()) {
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) onMapReceived?.invoke(bitmap)
                }
            }

            // 解析電量與模式
            val batt = json.optInt("Battery", -1)
            val mode = json.optString("Mode", "待機")
            if (batt != -1) onStatusUpdate?.invoke("● 已連線", batt, mode)

        } catch (e: Exception) { Log.e("RobotTCP", "JSON 解析錯誤") }
    }

    private fun listenToCmdResponse() {
        thread {
            try {
                val reader = BufferedReader(InputStreamReader(cmdSocket!!.getInputStream()))
                while (isRunning) {
                    val line = reader.readLine() ?: break
                    handleIncomingJson(line)
                }
            } catch (e: Exception) { Log.e("RobotTCP", "8900 斷開") }
        }
    }

    fun sendCommand(command: String, parameter: String) {
        thread {
            try {
                val json = JSONObject().apply {
                    put("Command", command)
                    put("Para", parameter)
                }
                writer?.println(json.toString())
                Log.d("RobotTCP", "發送指令: $json")
            } catch (e: Exception) { Log.e("RobotTCP", "發送失敗") }
        }
    }

    fun close() {
        isRunning = false
        thread {
            cmdSocket?.close()
            eventServerSocket?.close()
        }
    }
}
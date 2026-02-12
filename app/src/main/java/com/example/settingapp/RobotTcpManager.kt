package com.example.settingapp

import org.json.JSONObject
import java.io.PrintWriter
import java.net.Socket
import java.util.*
import kotlin.concurrent.thread

class RobotTcpManager {
    // 定義 Socket (通訊管道) 與 Writer (寫入器)
    private var cmdSocket: Socket? = null
    private var writer: PrintWriter? = null
    private var heartbeatTimer: Timer? = null

    // 連線函式：傳入機器人 IP 並回傳狀態訊息
    fun connect(ip: String, onStatus: (String) -> Unit) {
        // 使用 thread 開啟背景執行緒，避免阻塞介面導致 App 閃退
        thread {
            try {
                // 根據 AMR.pdf 規範，主控指令端口必須是 8900
                cmdSocket = Socket(ip, 8900)
                // 初始化寫入器，true 代表自動刷新緩衝區
                writer = PrintWriter(cmdSocket?.getOutputStream(), true)

                // 連線成功後立即啟動心跳機制
                startHeartbeat()
                onStatus("連線成功 (8900)")
            } catch (e: Exception) {
                onStatus("連線失敗: ${e.message}")
            }
        }
    }

    // 心跳機制：每 5 秒發送一次官方規定的字串，防止 AMR 自動斷線
    private fun startHeartbeat() {
        heartbeatTimer?.cancel()
        heartbeatTimer = Timer()
        heartbeatTimer?.schedule(object : TimerTask() {
            override fun run() {
                // 必須發送官方指定的 AO_CHECK 標籤且結尾要有換行符 \n
                writer?.print("{AO_CHECK_CLIENT_HEARTBEAT}\n")
                writer?.flush()
            }
        }, 0, 5000) // 0 秒開始，每 5000 毫秒執行一次
    }

    // 指令發送：將控制邏輯轉換成 AMR 可識別的 JSON 格式
    fun sendCommand(command: String, parameter: String) {
        thread {
            try {
                // 建立符合官方 5.9 節規範的 JSON 結構
                val json = JSONObject()
                json.put("Command", command) // 例如：ManualControl
                json.put("Para", parameter)   // 例如：F (Forward)

                // 傳送 JSON 字串，末端必須補上 LF (換行) 機器人才會處理
                writer?.print(json.toString() + "\n")
                writer?.flush()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // 關閉資源：App 結束時釋放 Socket 與計時器，節省系統資源
    fun close() {
        heartbeatTimer?.cancel()
        cmdSocket?.close()
    }
}
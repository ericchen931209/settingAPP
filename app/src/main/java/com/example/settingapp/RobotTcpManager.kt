package com.example.settingapp

import java.io.PrintWriter
import java.net.Socket
import kotlin.concurrent.thread

class RobotTcpManager {
    private var socket: Socket? = null
    private var writer: PrintWriter? = null

    // 連線函式：開啟一個背景執行緒去敲機器人的門
    fun connect(ip: String, port: Int, onStatus: (String) -> Unit) {
        thread {
            try {
                onStatus("連線中...")
                socket = Socket(ip, port)
                writer = PrintWriter(socket?.getOutputStream(), true)
                onStatus("連線成功！")
            } catch (e: Exception) {
                onStatus("連線失敗: ${e.message}")
            }
        }
    }

    // 發送指令：把文字傳給機器人
    fun send(message: String) {
        thread {
            if (writer != null) {
                writer?.println(message)
            }
        }
    }

    // 斷開連線：離開 App 時記得關門
    fun close() {
        thread {
            socket?.close()
        }
    }
}
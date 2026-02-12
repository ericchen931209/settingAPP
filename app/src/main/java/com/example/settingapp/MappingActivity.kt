package com.example.settingapp

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MappingActivity : AppCompatActivity() {
    private val tcpManager = RobotTcpManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mapping)

        // 接收從 MainActivity 傳來的 IP 資訊
        val ip = intent.getStringExtra("IP") ?: ""
        // 在這個畫面重新建立連線，以便傳送控制指令
        tcpManager.connect(ip) { }

        // 前進按鈕：對應 ManualControl 指令與參數 F (Forward)
        findViewById<Button>(R.id.btn_up).setOnClickListener {
            tcpManager.sendCommand("ManualControl", "F")
        }

        // 後退按鈕：對應 ManualControl 指令與參數 B (Backward)
        findViewById<Button>(R.id.btn_down).setOnClickListener {
            tcpManager.sendCommand("ManualControl", "B")
        }

        // 左轉按鈕：對應 ManualControl 指令與參數 L (Left)
        findViewById<Button>(R.id.btn_left).setOnClickListener {
            tcpManager.sendCommand("ManualControl", "L")
        }

        // 右轉按鈕：對應 ManualControl 指令與參數 R (Right)
        findViewById<Button>(R.id.btn_right).setOnClickListener {
            tcpManager.sendCommand("ManualControl", "R")
        }

        // 儲存按鈕：使用 SetMap 指令觸發機器人儲存當前掃描的地圖
        findViewById<Button>(R.id.btn_save_map).setOnClickListener {
            tcpManager.sendCommand("SetMap", "save")
        }
    }

    // 當離開這個畫面或 App 被縮小時執行
    override fun onDestroy() {
        super.onDestroy()
        // 發送 "S" (Stop) 指令，防止機器人在 App 關閉後還在亂跑
        tcpManager.sendCommand("ManualControl", "S")
        tcpManager.close()
    }
}
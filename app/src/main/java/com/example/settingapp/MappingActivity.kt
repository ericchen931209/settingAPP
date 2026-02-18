package com.example.settingapp

import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import java.util.*

class MappingActivity : AppCompatActivity() {
    private val tcpManager = RobotTcpManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mapping)

        val robotIp = intent.getStringExtra("IP") ?: "192.168.168.168"
        val imageView = findViewById<ImageView>(R.id.map_view)

        // 1. 連線並設定地圖更新回調
        tcpManager.connect(robotIp) { }
        tcpManager.onMapReceived = { bitmap ->
            runOnUiThread {
                // 將收到的 Bitmap 顯示在全螢幕 ImageView 上
                imageView.setImageBitmap(bitmap)
            }
        }

        // 2. 定期請求地圖 (依據 5.56 節 GetMapData)
        Timer().schedule(object : TimerTask() {
            override fun run() {
                // 傳入空字串代表請求當前正在建圖的暫存圖
                tcpManager.sendCommand("GetMapData", "")
            }
        }, 0, 2000) // 每 2 秒更新一次地圖

        // 3. 遙控按鈕 (依據 5.9 節 ManualControl)
        findViewById<Button>(R.id.btn_up).setOnClickListener { tcpManager.sendCommand("ManualControl", "F") }
        findViewById<Button>(R.id.btn_down).setOnClickListener { tcpManager.sendCommand("ManualControl", "B") }
        findViewById<Button>(R.id.btn_left).setOnClickListener { tcpManager.sendCommand("ManualControl", "L") }
        findViewById<Button>(R.id.btn_right).setOnClickListener { tcpManager.sendCommand("ManualControl", "R") }

        // 4. 儲存與重新建圖
        findViewById<Button>(R.id.btn_save_map).setOnClickListener { tcpManager.sendCommand("SetMap", "save") }
        findViewById<Button>(R.id.btn_rebuild).setOnClickListener { tcpManager.sendCommand("SwitchMode", "mapping") }
    }

    override fun onDestroy() {
        super.onDestroy()
        tcpManager.sendCommand("ManualControl", "S") // 安全停止
        tcpManager.close()
    }
}
package com.example.settingapp

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.*

class MappingActivity : AppCompatActivity() {
    private val tcpManager = RobotTcpManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mapping)

        val ip = intent.getStringExtra("IP") ?: ""
        val mapView = findViewById<ImageView>(R.id.map_view)
        val tvConn = findViewById<TextView>(R.id.tv_status_conn)
        val tvBatt = findViewById<TextView>(R.id.tv_status_battery)
        val tvMode = findViewById<TextView>(R.id.tv_status_mode)

        // 1. 設定回調：更新狀態欄與地圖
        tcpManager.onStatusUpdate = { status, battery, mode ->
            runOnUiThread {
                tvConn.text = status
                tvConn.setTextColor(if (status.contains("已連線")) Color.GREEN else Color.RED)
                tvBatt.text = "電量: $battery%"
                tvBatt.setTextColor(if (battery < 20) Color.RED else Color.WHITE)
                tvMode.text = "模式: $mode"
            }
        }

        tcpManager.onMapReceived = { bitmap ->
            runOnUiThread { mapView.setImageBitmap(bitmap) }
        }

        // 2. 定時請求：依照 5.56 節請求地圖
        tcpManager.connect(ip) { }
        Timer().schedule(object : TimerTask() {
            override fun run() { tcpManager.sendCommand("GetMapData", "") }
        }, 0, 2000)

        // 3. 方向控制 (5.9 節)
        findViewById<Button>(R.id.btn_up).setOnClickListener { tcpManager.sendCommand("ManualControl", "F") }
        findViewById<Button>(R.id.btn_down).setOnClickListener { tcpManager.sendCommand("ManualControl", "B") }
        findViewById<Button>(R.id.btn_left).setOnClickListener { tcpManager.sendCommand("ManualControl", "L") }
        findViewById<Button>(R.id.btn_right).setOnClickListener { tcpManager.sendCommand("ManualControl", "R") }

        // 4. 開啟建圖 (這會啟動 Lidar)
        findViewById<Button>(R.id.btn_rebuild).setOnClickListener { tcpManager.sendCommand("SwitchMode", "mapping") }
    }

    override fun onDestroy() {
        super.onDestroy()
        tcpManager.sendCommand("ManualControl", "S") // 安全停止
        tcpManager.close()
    }
}
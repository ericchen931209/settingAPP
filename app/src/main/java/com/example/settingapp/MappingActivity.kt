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

        val ip = intent.getStringExtra("IP") ?: "192.168.168.168"
        val tvConn = findViewById<TextView>(R.id.tv_status_conn)
        val tvBatt = findViewById<TextView>(R.id.tv_status_battery)
        val tvMode = findViewById<TextView>(R.id.tv_status_mode)
        val mapView = findViewById<ImageView>(R.id.map_view)

        tcpManager.onStatusUpdate = { status, battery, mode ->
            runOnUiThread {
                tvConn.text = status
                tvConn.setTextColor(if (status.contains("已連線")) Color.GREEN else Color.RED)
                tvBatt.text = "電量: $battery%"
                tvMode.text = "模式: $mode"
            }
        }

        tcpManager.onMapReceived = { bitmap ->
            runOnUiThread { mapView.setImageBitmap(bitmap) }
        }

        tcpManager.connect(ip) { status ->
            runOnUiThread { tvConn.text = status }
        }

        // 依據 PDF 規範，每 2 秒請求一次地圖
        Timer().schedule(object : TimerTask() {
            override fun run() {
                tcpManager.sendCommand("GetMapData", "")
            }
        }, 0, 2000)

        // 遙控按鈕
        findViewById<Button>(R.id.btn_up).setOnClickListener { tcpManager.sendCommand("ManualControl", "F") }
        findViewById<Button>(R.id.btn_down).setOnClickListener { tcpManager.sendCommand("ManualControl", "B") }
        findViewById<Button>(R.id.btn_left).setOnClickListener { tcpManager.sendCommand("ManualControl", "L") }
        findViewById<Button>(R.id.btn_right).setOnClickListener { tcpManager.sendCommand("ManualControl", "R") }
    }

    override fun onDestroy() {
        super.onDestroy()
        tcpManager.close()
    }
}
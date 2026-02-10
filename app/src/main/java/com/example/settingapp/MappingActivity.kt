package com.example.settingapp

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MappingActivity : AppCompatActivity() {

    // 這裡我們需要使用同一個 TCP Manager 實例或重新連線
    // 為了簡化，我們先宣告一個新的，實際專案建議使用 Singleton 模式
    private val tcpManager = RobotTcpManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mapping)

        // 綁定 UI 按鈕
        val btnUp = findViewById<Button>(R.id.btn_up)
        val btnDown = findViewById<Button>(R.id.btn_down)
        val btnLeft = findViewById<Button>(R.id.btn_left)
        val btnRight = findViewById<Button>(R.id.btn_right)
        val btnSave = findViewById<Button>(R.id.btn_save_map)
        val btnRebuild = findViewById<Button>(R.id.btn_rebuild)

        // 設定方向鍵指令
        btnUp.setOnClickListener { tcpManager.send("MOVE_FORWARD") }
        btnDown.setOnClickListener { tcpManager.send("MOVE_BACKWARD") }
        btnLeft.setOnClickListener { tcpManager.send("TURN_LEFT") }
        btnRight.setOnClickListener { tcpManager.send("TURN_RIGHT") }

        // 儲存與重新建圖
        btnSave.setOnClickListener {
            tcpManager.send("SAVE_MAP")
            Toast.makeText(this, "地圖儲存指令已送出", Toast.LENGTH_SHORT).show()
        }

        btnRebuild.setOnClickListener {
            tcpManager.send("CLEAR_AND_REBUILD")
            Toast.makeText(this, "重新建圖中...", Toast.LENGTH_SHORT).show()
        }
    }
}
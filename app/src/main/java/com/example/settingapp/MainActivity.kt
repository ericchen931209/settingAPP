package com.example.settingapp

// 這些 Import 是解決 Unresolved reference 的關鍵
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    // 建立通訊管理員 (確保 RobotTcpManager.kt 檔案已存在)
    private val tcpManager = RobotTcpManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 確保你的 R.layout 名稱與 activity_main.xml 一致
        setContentView(R.layout.activity_main)

        // 綁定 UI 元件
        val btnConnect = findViewById<Button>(R.id.btn_connect)
        val etIp = findViewById<EditText>(R.id.et_ip)
        val etPort = findViewById<EditText>(R.id.et_port)
        val tvStatus = findViewById<TextView>(R.id.tv_status)

        // 設定點擊邏輯
        btnConnect.setOnClickListener {
            val ip = etIp.text.toString()
            val port = etPort.text.toString().toIntOrNull() ?: 8080

            if (ip.isEmpty()) {
                Toast.makeText(this, "請輸入機器人 IP", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 呼叫連線
            tcpManager.connect(ip, port) { statusMessage ->
                // 更新 UI 必須在主執行緒
                runOnUiThread {
                    tvStatus.text = "狀態: $statusMessage"
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tcpManager.close() // 關閉 App 時斷開 TCP 連線
    }
}
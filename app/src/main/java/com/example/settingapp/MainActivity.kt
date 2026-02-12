package com.example.settingapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val tcpManager = RobotTcpManager() // 初始化通訊工具

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 綁定 XML 中的 UI 元件，以便在程式中操作
        val btnConnect = findViewById<Button>(R.id.btn_connect)
        val etIp = findViewById<EditText>(R.id.et_ip)
        val tvStatus = findViewById<TextView>(R.id.tv_status)

        // 設定「連接機器人」按鈕的點擊行為
        btnConnect.setOnClickListener {
            val ip = etIp.text.toString()
            // 呼叫連線邏輯
            tcpManager.connect(ip) { status ->
                // 更新畫面文字必須切換回 runOnUiThread (主執行緒)
                runOnUiThread {
                    tvStatus.text = "狀態: $status"
                    // 若關鍵字顯示成功，則跳出詢問對話框
                    if (status.contains("成功")) showMapCheckDialog(ip)
                }
            }
        }
    }

    // 彈出對話框：詢問地圖狀態以進行功能分流
    private fun showMapCheckDialog(ip: String) {
        AlertDialog.Builder(this)
            .setTitle("IRIS 連線成功")
            .setMessage("您是否已有地圖檔案？")
            .setPositiveButton("有，匯入地圖") { _, _ -> /* 預留匯入功能 */ }
            .setNegativeButton("沒有，去建圖") { _, _ ->
                // 使用 Intent 進行頁面跳轉，並將 IP 傳給下一個畫面
                val intent = Intent(this, MappingActivity::class.java)
                intent.putExtra("IP", ip)
                startActivity(intent)
            }
            .setCancelable(false) // 防止點擊背景關閉對話框
            .show()
    }
}
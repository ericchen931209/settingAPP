package com.example.settingapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.settingapp.databinding.ActivityMappingBinding
import org.json.JSONObject
import java.io.*
import java.net.*
import kotlin.concurrent.thread

class MappingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMappingBinding
    private var cmdWriter: PrintWriter? = null
    private var isRunning = true
    private val AMR_IP = "192.168.168.168"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMappingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        startStatusListener()     // 8901 接收機器人回傳
        connectToCommandPort()   // 8900 發送 APP 指令
        setupControlButtons()
    }

    // 更新狀態列與電量百分比 (0~20 轉 %)
    private fun updateStatusBar(isConnected: Boolean, battery: Int = -1) {
        runOnUiThread {
            val statusView = findViewById<TextView>(R.id.tv_status_conn)
            val batteryView = findViewById<TextView>(R.id.tv_status_battery)

            if (isConnected) {
                statusView?.text = "● 已連線"
                statusView?.setTextColor(Color.parseColor("#4CAF50"))
            } else {
                statusView?.text = "● 未連線"
                statusView?.setTextColor(Color.parseColor("#FF5252"))
            }

            if (battery != -1) {
                val batteryPercent = (battery * 100) / 20
                batteryView?.text = "電量: $batteryPercent%"
            }
        }
    }

    private fun startStatusListener() {
        thread {
            var server: ServerSocket? = null
            try {
                server = ServerSocket(8901)
                while (isRunning) {
                    val client = server.accept()
                    thread {
                        try {
                            val ins = client.getInputStream()
                            val outputStream = ByteArrayOutputStream()
                            val buffer = ByteArray(65536)
                            var len: Int
                            while (ins.read(buffer).also { len = it } != -1) {
                                outputStream.write(buffer, 0, len)
                                val currentData = outputStream.toString("UTF-8").trim()
                                if (currentData.endsWith("}")) {
                                    handleRobotResponse(currentData)
                                    outputStream.reset()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("AMR_TCP", "8901 斷開")
                        } finally {
                            client.close()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AMR_TCP", "8901 啟動失敗")
            } finally {
                server?.close()
            }
        }
    }

    private fun handleRobotResponse(jsonStr: String) {
        if (jsonStr.contains("AO_CHECK_HOST_HEARTBEAT")) {
            runOnUiThread { updateStatusBar(true) }
            return
        }
        try {
            val json = JSONObject(jsonStr)
            if (json.has("battery_level")) {
                updateStatusBar(true, json.getInt("battery_level"))
            }
            // 5.56 節：接收機器人傳來的地圖數據
            if (json.has("Data")) {
                var base64 = json.optString("Data")
                if (base64.contains(",")) base64 = base64.substring(base64.indexOf(",") + 1)
                thread {
                    val bytes = Base64.decode(base64, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    runOnUiThread { binding.mapView.setImageBitmap(bitmap) }
                }
            }
        } catch (e: Exception) { }
    }

    private fun connectToCommandPort() {
        thread {
            try {
                val socket = Socket()
                val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
                cm.allNetworks.find { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true }?.bindSocket(socket)

                socket.connect(InetSocketAddress(AMR_IP, 8900), 5000)
                cmdWriter = PrintWriter(BufferedWriter(OutputStreamWriter(socket.getOutputStream(), "UTF-8")), true)

                // 註冊電量通知
                cmdWriter?.println("{\"Command\":\"RegNotify\",\"Type\":\"GetBatteryLevel\",\"Period\":2000}")

                while (isRunning) {
                    // 主動請求地圖 (5.56 節)
                    cmdWriter?.println("{\"Command\":\"GetMapData\",\"Para\":\"\"}")
                    Thread.sleep(3000)
                }
            } catch (e: Exception) {
                runOnUiThread { updateStatusBar(false) }
            }
        }
    }

    private fun setupControlButtons() {
        binding.btnUp.setOnClickListener { sendMove("F") }
        binding.btnDown.setOnClickListener { sendMove("B") }
        binding.btnLeft.setOnClickListener { sendMove("L") }
        binding.btnRight.setOnClickListener { sendMove("R") }

        // --- 核心：5.54 節 SetGraffitiedMap (APP 發指令給機器人) ---
        // 這裡將「重新建圖」按鈕改為「同步繪製地圖」功能
        binding.btnRebuild.setOnClickListener {
            val bitmap = (binding.mapView.drawable as? BitmapDrawable)?.bitmap
            if (bitmap != null) {
                thread {
                    try {
                        val bos = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos)
                        val base64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)

                        // 依照 5.54 節格式：Para 必須手動加上標籤前綴
                        val json = JSONObject().apply {
                            put("Command", "SetGraffitiedMap")
                            put("Para", "data:image/png;base64,$base64")
                        }

                        cmdWriter?.println(json.toString())
                        Log.d("AMR_TCP", ">>> APP 已發送指令: SetGraffitiedMap")
                        runOnUiThread { Toast.makeText(this, "已發送繪製地圖指令", Toast.LENGTH_SHORT).show() }
                    } catch (e: Exception) {
                        Log.e("AMR_TCP", "發送失敗: ${e.message}")
                    }
                }
            }
        }
    }

    private fun sendMove(dir: String) {
        thread { cmdWriter?.println("{\"Command\":\"ManualControl\",\"Para\":\"$dir\"}") }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
    }
}
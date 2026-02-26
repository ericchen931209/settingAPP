package com.example.settingapp

import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.TextView
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

        // 啟動監聽器與連線
        startStatusListener()
        connectToCommandPort()
        setupControlButtons()
    }

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
                // 這裡我們直接顯示原始數值，確認到底抓到什麼
                batteryView?.text = "電量: $battery/20"
                Log.d("AMR_UI", "UI顯示電量: $battery")
            }
        }
    }

    private fun startStatusListener() {
        thread {
            var server: ServerSocket? = null
            try {
                server = ServerSocket(8901)
                Log.d("AMR_TCP", ">>> 本地 8901 監聽中... 等待機器人主動連接")
                while (isRunning) {
                    val client = server.accept()
                    Log.d("AMR_TCP", ">>> 偵測到機器人連入 8901: ${client.inetAddress.hostAddress}")

                    thread {
                        try {
                            val ins = client.getInputStream()
                            val reader = BufferedReader(InputStreamReader(ins, "UTF-8"))
                            // 讀取一行 JSON
                            val rawData = reader.readLine()
                            if (rawData != null) {
                                Log.d("AMR_TCP", ">>> 8901 收到數據: $rawData")
                                handleRobotResponse(rawData)
                            }
                        } catch (e: Exception) {
                            Log.e("AMR_TCP", "8901 接收失敗: ${e.message}")
                        } finally {
                            client.close()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AMR_TCP", "8901 Server 啟動失敗")
            } finally {
                server?.close()
            }
        }
    }

    private fun handleRobotResponse(jsonStr: String) {
        try {
            val json = JSONObject(jsonStr)
            updateStatusBar(true)

            // 依照你的要求解析 battery_level
            if (json.has("battery_level")) {
                val bLevel = json.getInt("battery_level")
                updateStatusBar(true, bLevel)
            }

            // 地圖數據
            if (json.has("Data")) {
                val base64Data = json.optString("Data")
                val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                runOnUiThread { binding.mapView.setImageBitmap(bitmap) }
            }
        } catch (e: Exception) {
            Log.e("AMR_TCP", "JSON 解析出錯")
        }
    }

    private fun connectToCommandPort() {
        thread {
            try {
                val socket = Socket()
                val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
                cm.allNetworks.find { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true }?.bindSocket(socket)

                socket.connect(InetSocketAddress(AMR_IP, 8900), 5000)
                // 增加自動 Flush，確保指令即時送出
                cmdWriter = PrintWriter(BufferedWriter(OutputStreamWriter(socket.getOutputStream(), "UTF-8")), true)
                Log.d("AMR_TCP", ">>> 8900 指令通道已開啟")

                // 發送 RegNotify (依照第 6 節規範)
                // 這裡特別注意：有些機器人需要 JSON 後面加個 \n 才會動
                val regJson = "{\"Command\":\"RegNotify\",\"Type\":\"GetBatteryLevel\",\"Period\":2000}"
                cmdWriter?.println(regJson)
                Log.d("AMR_TCP", ">>> 已送出訂閱指令: $regJson")

                while (isRunning) {
                    // 每隔幾秒主動要一次地圖，確保連線活著
                    val mapReq = "{\"Command\":\"GetMapData\",\"Para\":\"\"}"
                    cmdWriter?.println(mapReq)
                    Thread.sleep(3000)
                }
            } catch (e: Exception) {
                updateStatusBar(false)
                Log.e("AMR_TCP", "8900 連線中斷: ${e.message}")
            }
        }
    }

    private fun setupControlButtons() {
        binding.btnUp.setOnClickListener { sendMove("F") }
        binding.btnDown.setOnClickListener { sendMove("B") }
        binding.btnLeft.setOnClickListener { sendMove("L") }
        binding.btnRight.setOnClickListener { sendMove("R") }
        binding.btnRebuild.setOnClickListener {
            thread { cmdWriter?.println("{\"Command\":\"StartBuildMap\",\"Para\":\"\"}") }
        }
    }

    private fun sendMove(dir: String) {
        thread {
            val moveJson = "{\"Command\":\"ManualControl\",\"Para\":\"$dir\"}"
            cmdWriter?.println(moveJson)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
    }
}
package com.example.settingapp

import android.graphics.*
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.io.*
import java.net.*
import java.nio.ByteBuffer
import kotlin.concurrent.thread

class MappingActivity : AppCompatActivity() {

    private var cmdWriter: PrintWriter? = null
    private var isRunning = true
    private val AMR_IP = "192.168.168.168"

    // 地圖參數 (根據你的 Log: -17688, -9259)
    private var currentMapW = 332
    private var currentMapH = 332
    private var mapX = -17688f
    private var mapY = -9259f
    private val mapDataBuffer = ByteArrayOutputStream()

    // 儲存雷達紅點座標
    private var laserPoints = mutableListOf<PointF>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mapping)

        // 滿版配置
        findViewById<ImageView>(R.id.map_view)?.scaleType = ImageView.ScaleType.FIT_CENTER

        startStatusListener()
        connectToCommandPort()
        setupControlButtons()
    }

    private fun updateUI(status: String? = null, battery: Int = -1, remain: String? = null) {
        runOnUiThread {
            if (status != null) findViewById<TextView>(R.id.tv_status_conn)?.apply {
                text = status
                setTextColor(if (status.contains("已連線")) Color.GREEN else Color.RED)
            }
            if (battery != -1) findViewById<TextView>(R.id.tv_status_battery)?.text = "電量: ${(battery * 100) / 20}%"
            // 修正 5.4 節：顯示剩餘張數
            if (remain != null) findViewById<TextView>(R.id.tv_status_mode)?.text = "剩餘: $remain 張"
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
                            val buffer = ByteArray(1024 * 64)
                            var bytesRead: Int

                            while (ins.read(buffer).also { bytesRead = it } != -1) {
                                val rawStr = String(buffer, 0, bytesRead, Charsets.UTF_8)

                                // 1. 解析 JSON (電量、5.4 剩餘空間、5.58 雷達數據)
                                if (rawStr.contains("{") && rawStr.contains("}")) {
                                    handleRobotJson(rawStr)
                                }

                                // 2. 累積地圖 Binary (5.56)
                                if (bytesRead > 1000 || !rawStr.trim().startsWith("{")) {
                                    mapDataBuffer.write(buffer, 0, bytesRead)
                                    renderFullMap()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("AMR_TCP", "8901 Stream Error")
                        } finally {
                            client.close()
                        }
                    }
                }
            } catch (e: Exception) {
                server?.close()
            }
        }
    }

    private fun handleRobotJson(jsonStr: String) {
        try {
            val start = jsonStr.indexOf("{")
            val end = jsonStr.lastIndexOf("}")
            val json = JSONObject(jsonStr.substring(start, end + 1))

            updateUI(status = "● 已連線")

            if (json.has("battery_level")) updateUI(battery = json.getInt("battery_level"))

            // 5.4 節：處理 SetMap 與 remaining 數值
            if (json.optString("Command") == "SetMap") {
                val res = json.optString("Result", "")
                if (res.any { it.isDigit() }) updateUI(remain = res)
            }

            // 5.58 節核心：解析雷達紅點數據 (Result 裡通常是 x,y;x,y...)
            if (json.optString("Command") == "GetLidarScan") {
                val rawPoints = json.optString("Result", "")
                if (rawPoints.isNotEmpty() && rawPoints != "Successful") {
                    parseLidarPoints(rawPoints)
                }
            }
        } catch (e: Exception) {}
    }

    private fun parseLidarPoints(raw: String) {
        try {
            val points = mutableListOf<PointF>()
            // 假設格式為 x,y;x,y 或從 JSON Array 解析
            val pairs = raw.split(";")
            for (pair in pairs) {
                val xy = pair.split(",")
                if (xy.size == 2) {
                    points.add(PointF(xy[0].toFloat(), xy[1].toFloat()))
                }
            }
            laserPoints = points
        } catch (e: Exception) {}
    }

    private fun renderFullMap() {
        val expectedSize = currentMapW * currentMapH
        if (mapDataBuffer.size() >= expectedSize) {
            try {
                val rawData = mapDataBuffer.toByteArray()
                val bitmap = Bitmap.createBitmap(currentMapW, currentMapH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                val paint = Paint()

                // 畫地圖底圖 (5.56)
                for (i in 0 until expectedSize) {
                    val value = rawData[i].toInt() and 0xFF
                    val x = i % currentMapW
                    val y = i / currentMapW
                    paint.color = when {
                        value > 200 -> Color.WHITE
                        value == 0 -> Color.parseColor("#CCCCCC") // 未知區域灰
                        else -> Color.BLACK
                    }
                    canvas.drawPoint(x.toFloat(), y.toFloat(), paint)
                }

                // 疊加 5.58 雷達紅點
                paint.color = Color.RED
                paint.strokeWidth = 3f
                for (pt in laserPoints) {
                    val drawX = (pt.x - mapX) / 100f
                    val drawY = (pt.y - mapY) / 100f
                    canvas.drawPoint(drawX, drawY, paint)
                }

                runOnUiThread {
                    findViewById<ImageView>(R.id.map_view)?.setImageBitmap(bitmap)
                }
                mapDataBuffer.reset()
            } catch (e: Exception) {
                mapDataBuffer.reset()
            }
        }
    }

    private fun connectToCommandPort() {
        thread {
            try {
                val socket = Socket()
                val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
                cm.allNetworks.find { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true }?.bindSocket(socket)
                socket.connect(InetSocketAddress(AMR_IP, 8900), 5000)
                cmdWriter = PrintWriter(BufferedWriter(OutputStreamWriter(socket.getOutputStream(), "UTF-8")), true)

                // --- 5.1 & 5.58 節：主動訂閱雷達數據 ---
                cmdWriter?.println("{\"Command\":\"RegNotify\",\"Type\":\"GetLidarScan\",\"Period\":500}")

                // 5.4 節：查詢剩餘張數
                cmdWriter?.println("{\"Command\":\"SetMap\",\"Para\":\"remaining\"}")

                while (isRunning) {
                    // 5.56 節：要地圖
                    cmdWriter?.println("{\"Command\":\"GetMapData\",\"Para\":\"\"}")
                    Thread.sleep(1000)
                }
            } catch (e: Exception) {
                updateUI(status = "● 連線失敗")
            }
        }
    }

    private fun setupControlButtons() {
        findViewById<android.view.View>(R.id.btn_up)?.setOnClickListener { sendMove("F") }
        findViewById<android.view.View>(R.id.btn_down)?.setOnClickListener { sendMove("B") }
        findViewById<android.view.View>(R.id.btn_left)?.setOnClickListener { sendMove("L") }
        findViewById<android.view.View>(R.id.btn_right)?.setOnClickListener { sendMove("R") }
        findViewById<android.view.View>(R.id.btn_rebuild)?.setOnClickListener {
            thread {
                // 重新觸發 5.4 與 5.58
                cmdWriter?.println("{\"Command\":\"SetMap\",\"Para\":\"remaining\"}")
                cmdWriter?.println("{\"Command\":\"RegNotify\",\"Type\":\"GetLidarScan\",\"Period\":500}")
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
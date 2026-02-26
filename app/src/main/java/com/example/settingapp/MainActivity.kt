package com.example.settingapp

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.settingapp.databinding.ActivityMainBinding
import java.net.*
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val AMR_IP = "192.168.168.168"
    private val CMD_PORT = 8900

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.etIp.visibility = View.GONE
        binding.btnConnect.text = "直接連線至 AMR"

        binding.btnConnect.setOnClickListener {
            startAmrConnection()
        }
    }

    private fun startAmrConnection() {
        binding.btnConnect.isEnabled = false
        binding.tvStatus.text = "連線中 (強制使用乙太網路)..."
        binding.tvStatus.setTextColor(Color.parseColor("#FFA500"))

        thread {
            try {
                // 【關鍵技術】：在不關閉 Wi-Fi 的情況下，強制尋找乙太網路介面
                val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val allNetworks = connectivityManager.allNetworks
                var ethernetNetwork: android.net.Network? = null

                for (network in allNetworks) {
                    val caps = connectivityManager.getNetworkCapabilities(network)
                    if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true) {
                        ethernetNetwork = network
                        break
                    }
                }

                val socket = Socket()

                if (ethernetNetwork != null) {
                    Log.d("AMR_TCP", "偵測到乙太網路，執行硬體綁定連線")
                    // 強制將此 Socket 綁定到乙太網路硬體
                    ethernetNetwork.bindSocket(socket)
                } else {
                    Log.w("AMR_TCP", "未偵測到乙太網路，使用預設路由 (可能因 Wi-Fi 干擾導致逾時)")
                }

                // 執行連線
                Log.d("AMR_TCP", "嘗試連接 $AMR_IP:$CMD_PORT...")
                socket.connect(InetSocketAddress(AMR_IP, CMD_PORT), 5000)

                Log.d("AMR_TCP", "TCP 連線成功")

                runOnUiThread {
                    binding.tvStatus.text = "連線成功"
                    binding.tvStatus.setTextColor(Color.GREEN)

                    binding.root.postDelayed({
                        val intent = Intent(this, MappingActivity::class.java)
                        startActivity(intent)
                        binding.btnConnect.isEnabled = true
                    }, 1000)
                }
                socket.close()

            } catch (e: Exception) {
                val errorReason = when (e) {
                    is SocketTimeoutException -> "連線失敗: 5 秒逾時 (請檢查線路)"
                    is ConnectException -> "連線失敗: 機器人拒絕 (Port 未開)"
                    else -> "連線錯誤: ${e.localizedMessage}"
                }
                Log.e("AMR_TCP", errorReason)
                runOnUiThread {
                    binding.tvStatus.text = errorReason
                    binding.tvStatus.setTextColor(Color.RED)
                    binding.btnConnect.isEnabled = true
                }
            }
        }
    }
}
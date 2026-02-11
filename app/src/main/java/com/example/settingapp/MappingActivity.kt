// ... 其他 import 保持不變

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_mapping)

    // 綁定元件代碼... (略)

    // 使用符合 AMR 協議的 JSON 指令
    btnUp.setOnClickListener    { tcpManager.sendCommand("ManualControl", "F") } // 前進
    btnDown.setOnClickListener  { tcpManager.sendCommand("ManualControl", "B") } // 後退
    btnLeft.setOnClickListener  { tcpManager.sendCommand("ManualControl", "L") } // 左轉
    btnRight.setOnClickListener { tcpManager.sendCommand("ManualControl", "R") } // 右轉

    // 停止按鈕 (建議增加一個停止鈕，或在放開按鈕時傳送 S)
    // btnStop.setOnClickListener { tcpManager.sendCommand("ManualControl", "S") }

    // 儲存地圖：根據文件應使用 SetMap 指令
    btnSave.setOnClickListener {
        tcpManager.sendCommand("SetMap", "save")
        Toast.makeText(this, "發送儲存指令", Toast.LENGTH_SHORT).show()
    }

    // 重新建圖：切換到 mapping 模式
    btnRebuild.setOnClickListener {
        tcpManager.sendCommand("SwitchMode", "mapping")
    }
}
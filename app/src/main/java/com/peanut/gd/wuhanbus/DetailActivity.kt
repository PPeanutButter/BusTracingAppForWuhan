package com.peanut.gd.wuhanbus

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.peanut.gd.wuhanbus.databinding.ActivityDetailBinding
import com.peanut.sdk.miuidialog.MIUIDialog
import org.json.JSONObject
import kotlin.concurrent.thread

class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding
    private var line: String = ""

    @SuppressLint("UseCompatLoadingForDrawables")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SettingManager.init(this)
        window.statusBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        val lineId = intent.getStringExtra("lineId") ?: ""
        loadLine(lineId)
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun loadLine(lineId: String = line) {
        val route = binding.route
        line = lineId
        doSearch(lineId = lineId) {
            try {
                val data = it.getJSONObject("data")
                binding.endStation.text = String.format("%s路: 往%s方向", data.getString("lineName"), data.getString("endStopName"))
                binding.endTime.text = String.format(
                    "%s  %s - %s",
                    data.getString("price"),
                    data.getString("firstTime"),
                    data.getString("lastTime")
                )
                binding.refresh.setOnClickListener {
                    loadLine(lineId)
                }
                binding.switchLine.setOnClickListener {
                    val line2Id = data.getString("line2Id")
                    if (line2Id.isNotEmpty())
                        loadLine(line2Id)
                    else Toast.makeText(this, "该线路无返程哦", Toast.LENGTH_SHORT).show()
                }
                binding.createShortCut.setOnClickListener {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        addShortCut(
                            this,
                            lineId,
                            data.getString("lineName") + " - " + data.getString("endStopName")
                        )
                        Toast.makeText(this, "已提交创建快捷方式请求~", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "暂不支持Android 8以下创建快捷方式", Toast.LENGTH_SHORT).show()
                    }
                }
                val lines = data.getJSONArray("stops")
                val buses = data.getJSONArray("buses")
                route.removeAllViews()
                val state = mutableListOf<Pair<Int, Boolean>>()
                for (idx in 0 until buses.length()) {
                    val station = buses.getString(idx).split("|")
                    state.add(station[2].toInt() to (station[3] == "1"))
                }
                val startedStation = SettingManager.map(key = "StartedStation")
                for (idx in 0 until lines.length()) {
                    val station = lines.getJSONObject(idx)
                    val stopName = station.getString("stopName")
                    val stopId = station.getString("stopId")
                    val item = buildRouteItem(stopName, stopId, stopId in startedStation)
                    route.addView(item)
                }
                for (bus in state) {
                    val view = route.getChildAt(bus.first - 1)
                    val busIcon = view.findViewById<ImageView>(R.id.imageView4)
                    busIcon.visibility = View.VISIBLE
                    if (bus.second.not()) {
                        busIcon.setImageDrawable(resources.getDrawable(R.drawable.ic_round_directions_bus_24))
                        val a = busIcon.layoutParams as ConstraintLayout.LayoutParams
                        a.startToStart = 0
                        a.endToEnd = -1
                        busIcon.layoutParams = a
                    }
                }
                route.getChildAt(0).apply {
                    val line = this.findViewById<ImageView>(R.id.imageView2)
                    val a = line.layoutParams as ConstraintLayout.LayoutParams
                    a.startToStart = R.id.imageView3
                    line.layoutParams = a
                }
                route.getChildAt(route.childCount - 1).apply {
                    val line = this.findViewById<ImageView>(R.id.imageView2)
                    val a = line.layoutParams as ConstraintLayout.LayoutParams
                    a.endToEnd = R.id.imageView3
                    line.layoutParams = a
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun addShortCut(context: Context, busId: String, label: String) {
        val shortcutManager = context.getSystemService(Context.SHORTCUT_SERVICE) as ShortcutManager
        if (shortcutManager.isRequestPinShortcutSupported) {
            val shortcutInfoIntent =
                Intent(context, DetailActivity::class.java).apply { this.putExtra("lineId", busId) }
            shortcutInfoIntent.action = Intent.ACTION_VIEW
            val info: ShortcutInfo = ShortcutInfo.Builder(context, busId)
                .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
                .setShortLabel(label)
                .setIntent(shortcutInfoIntent)
                .build()
            shortcutManager.requestPinShortcut(info, null)
        }
    }

    private fun doSearch(lineId: String, func: (JSONObject) -> Unit) {
        val dialog = MIUIDialog(this).show {
            progress(text = "正在查询公交位置...")
            cancelable = false
            cancelOnTouchOutside = false
        }
        thread {
            try {
                val host =
                    "http://bus.wuhancloud.cn:9087/website//web/420100/line/${lineId}.do?Type=LineDetail"
                Http().apply {
                    this.setGet(host)
                    this.run()
                    val body = this.body
                    Handler(this@DetailActivity.mainLooper).post {
                        dialog.cancel()
                        func.invoke(JSONObject(body ?: "{}"))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Handler(this@DetailActivity.mainLooper).post {
                    dialog.cancel()
                    MIUIDialog(this).show {
                        title(text = "失败")
                        message(text = e.localizedMessage)
                    }
                    func.invoke(JSONObject("{}"))
                }
            }
        }
    }

    private fun buildRouteItem(stationName: String, stopId: String, started: Boolean): View {
        val view = this.layoutInflater.inflate(R.layout.station, null)
        view.findViewById<TextView>(R.id.station_name).apply {
            this.text = ("$stationName${if (started) "★" else ""}").toCharArray().joinToString("\n")
            if (started)
                this.setTextColor(Color.parseColor("#7367EF"))
            this.setOnClickListener {
                MIUIDialog(this@DetailActivity).show {
                    title(text = "设为常用站")
                    message(text = "收藏后,${stationName}将高亮显示以方便您查找")
                    positiveButton(text = "收藏") {
                        SettingManager.plus(key = "StartedStation", stopId)
                        loadLine()
                    }
                    negativeButton(text = "移除") {
                        SettingManager.remove(key = "StartedStation", stopId)
                        loadLine()
                    }
                }
            }
        }
        return view
    }
}
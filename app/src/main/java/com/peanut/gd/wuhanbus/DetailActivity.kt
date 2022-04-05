package com.peanut.gd.wuhanbus

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Icon
import android.net.Uri
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
import com.peanut.sdk.miuidialog.AddInFunction.toast
import com.peanut.sdk.miuidialog.MIUIDialog
import org.json.JSONObject
import kotlin.concurrent.thread

class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding
    private var line: String = ""
    private var traceId: String = ""

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
        traceId = intent.getStringExtra("traceId") ?: ""
        buildLine(lineId)
    }

    /**
     * 构建线路图
     */
    @SuppressLint("UseCompatLoadingForDrawables")
    private fun buildLine(lineId: String = line) {
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
                    buildLine(lineId)
                }
                binding.switchLine.setOnClickListener {
                    val line2Id = data.getString("line2Id")
                    if (line2Id.isNotEmpty())
                        buildLine(line2Id)
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
                /**
                 * 生成数据缓存
                 */
                val state = mutableListOf<Pair<Int, Boolean>>()
                val busid = mutableListOf<String>()
                for (idx in 0 until buses.length()) {
                    val station = buses.getString(idx).split("|")
                    state.add(station[2].toInt() to (station[3] == "1"))
                    busid.add(station[0])
                }
                val startedStation = SettingManager.map(key = "StartedStation")
                /**
                 * 开始渲染车站
                 */
                for (idx in 0 until lines.length()) {
                    val station = lines.getJSONObject(idx)
                    val stopName = station.getString("stopName")
                    val stopId = station.getString("stopId")
                    val item = buildRouteItem(stopName, stopId, stopId in startedStation)
                    route.addView(item)
                }
                /**
                 * 检查是否有需要跟踪的公交，有的话做高亮处理
                 */
                val traceIndex = busid.indexOf(traceId)
                /**
                 * 判断车站是否有车，车是否到站，到站了需要修改车图标的布局。
                 */
                for ((i,bus) in state.withIndex()) {
                    val view = route.getChildAt(bus.first - 1)
                    val busIcon = view.findViewById<ImageView>(R.id.imageView4)
                    busIcon.visibility = View.VISIBLE
                    if (bus.second.not()) {
                        busIcon.setImageDrawable(resources.getDrawable(R.drawable.ic_round_directions_bus_24))
                        (busIcon.layoutParams as ConstraintLayout.LayoutParams).let { layoutParams->
                            if(this.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                                layoutParams.startToStart = 0
                                layoutParams.endToEnd = -1
                            }else{
                                layoutParams.topToTop = 0
                                layoutParams.bottomToBottom = -1
                            }
                            busIcon.layoutParams = layoutParams
                        }
                    }
                    //高亮处理
                    if (i == traceIndex){
                        //set background
                        busIcon.setBackgroundResource(R.drawable.traced_bus)
                        busIcon.setImageDrawable(resources.getDrawable(R.drawable.ic_round_directions_bus_fff_24))
                    }
                    /**
                     * 添加分享当前所在车。
                     */
                    busIcon.setOnClickListener {
                        try {
                            ("点击查看我的位置 "+Uri.parse("https://peanutbutter.gitee.io/exercise.share?").buildUpon()
                                .appendQueryParameter("type", "bust")
                                .appendQueryParameter("bus", busid[i])
                                .appendQueryParameter("line", lineId)
                                .build().toString()).copy(this@DetailActivity)
                            "已复制我的位置~".toast(this@DetailActivity)
                        }catch (e:Exception){
                            e.printStackTrace()
                            e.localizedMessage?.toast(this@DetailActivity)
                        }
                    }
                }
                /**
                 * 第一个车站，前面的线段需要隐藏
                 */
                route.getChildAt(0).apply {
                    this.findViewById<ImageView>(R.id.imageView2).apply {
                        (this.layoutParams as ConstraintLayout.LayoutParams).let { layoutParams ->
                            if(this.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
                                layoutParams.startToStart = R.id.imageView3
                            else layoutParams.topToTop = R.id.imageView3
                            this.layoutParams = layoutParams
                        }
                    }
                }
                /**
                 * 最后一个车站，后面的线段需要隐藏
                 */
                route.getChildAt(route.childCount - 1).apply {
                    this.findViewById<ImageView>(R.id.imageView2).apply {
                        (this.layoutParams as ConstraintLayout.LayoutParams).let { layoutParams ->
                            if(this.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
                                layoutParams.endToEnd = R.id.imageView3
                            else layoutParams.bottomToBottom = R.id.imageView3
                            this.layoutParams = layoutParams
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 添加桌面快捷方式，仅支持Android O +
     */
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

    /**
     * 执行搜索，包括手动与自动
     */
    private fun doSearch(lineId: String, func: (JSONObject) -> Unit) {
        var retry = 5
        var success = false
        val dialog = MIUIDialog(this).show {
            progress(text = "正在查询公交位置...")
            cancelable = false
            cancelOnTouchOutside = false
        }
        thread {
            while (retry > 0 && success.not()) {
                try {
                    val host =
                        "http://bus.wuhancloud.cn:9087/website//web/420100/line/${lineId}.do?Type=LineDetail"
                    Http().apply {
                        this.setGet(host)
                        this.run()
                        val body = this.body
                        Handler(this@DetailActivity.mainLooper).post {
                            success = true
                            dialog.cancel()
                            func.invoke(JSONObject(body ?: "{}"))
                        }
                    }
                } catch (e: Exception) {
                    retry--
                    e.printStackTrace()
                    if (retry == 0)
                        Handler(this@DetailActivity.mainLooper).post {
                            dialog.cancel()
                            MIUIDialog(this).show {
                                title(text = "失败")
                                message(text = e.localizedMessage)
                            }
                            func.invoke(JSONObject("{}"))
                        }
                    else
                        Handler(this@DetailActivity.mainLooper).post {
                            dialog.setProgressText("第${6-retry}次查询公交位置...")
                        }
                }
            }
        }
    }

    /**
     * 渲染车站视图,支持收藏
     */
    private fun buildRouteItem(stationName: String, stopId: String, started: Boolean): View {
        val view = this.layoutInflater.inflate(R.layout.station, null)
        view.findViewById<TextView>(R.id.station_name).apply {
            this.text = ("$stationName${if (started) "★" else ""}").toCharArray().joinToString(if(this.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) "\n" else "")
            if (started)
                this.setTextColor(resources.getColor(R.color.primary))
            this.setOnClickListener {
                MIUIDialog(this@DetailActivity).show {
                    title(text = "设为常用站")
                    message(text = "收藏后,${stationName}将高亮显示以方便您查找")
                    positiveButton(text = "收藏") {
                        SettingManager.plus(key = "StartedStation", stopId)
                        buildLine()
                    }
                    negativeButton(text = "移除") {
                        SettingManager.remove(key = "StartedStation", stopId)
                        buildLine()
                    }
                }
            }
        }
        return view
    }

    /**
     * 复制文字到剪切板
     */
    private fun String.copy(context: Context) {
        val clipboard: ClipboardManager? =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
        val clip = ClipData.newPlainText("bus", this)
        clipboard?.setPrimaryClip(clip)
    }
}
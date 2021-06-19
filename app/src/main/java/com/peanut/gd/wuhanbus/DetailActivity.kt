package com.peanut.gd.wuhanbus

import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.material.snackbar.Snackbar
import com.peanut.gd.wuhanbus.databinding.ActivityDetailBinding
import com.peanut.sdk.miuidialog.MIUIDialog
import org.json.JSONObject
import kotlin.concurrent.thread

class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(findViewById(R.id.toolbar))
        val lineName = intent.getStringExtra("lineName")
        val lineId = intent.getStringExtra("lineId")?:""
        val endStopName = intent.getStringExtra("endStopName")
        binding.toolbarLayout.title = "开往: $endStopName"
        val route = binding.root.findViewById<LinearLayout>(R.id.route)
        doSearch(lineId = lineId){
            try {
                val lines = it.getJSONObject("data").getJSONArray("stops")
                val buses = it.getJSONObject("data").getJSONArray("buses")
                route.removeAllViews()
                val state = mutableListOf<Pair<Int,Boolean>>()
                for (idx in 0 until buses.length()) {
                    val station = buses.getString(idx).split("|")
                    state.add(station[2].toInt() to (station[3] == "1"))
                }
                for (idx in 0 until lines.length()) {
                    val station = lines.getJSONObject(idx)
                    val stopName = station.getString("stopName")
                    val item = buildRouteItem(stopName)
                    route.addView(item)
                }
                for (bus in state){
                    val view = route.getChildAt(bus.first-1)
                    val busIcon = view.findViewById<ImageView>(R.id.imageView4)
                    busIcon.visibility = View.VISIBLE
                    if (bus.second){
                        busIcon.setImageDrawable(resources.getDrawable(R.drawable.ic_round_directions_bus_color_24))
                        val a = busIcon.layoutParams as ConstraintLayout.LayoutParams
                        a.topToTop = R.id.station_name
                        a.bottomToBottom = R.id.station_name
                        busIcon.layoutParams = a
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        binding.fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
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

    private fun buildRouteItem(stationName:String):View{
        val view = this.layoutInflater.inflate(R.layout.station, null)
        view.findViewById<TextView>(R.id.station_name).text = stationName
        return view
    }
}
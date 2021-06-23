package com.peanut.gd.wuhanbus

import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.TextView
import com.peanut.gd.wuhanbus.databinding.ActivityMainBinding
import com.peanut.sdk.miuidialog.MIUIDialog
import org.json.JSONObject
import java.lang.Exception
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
        setContentView(ActivityMainBinding.inflate(layoutInflater).also { binding = it }.root)
        binding.button.setOnClickListener {
            val key = binding.editTextTextPersonName.text.toString()
            doSearch(key) {
                try {
                    val lines = it.getJSONObject("data").getJSONArray("lines")
                    binding.result.removeAllViews()
                    for (idx in 0 until lines.length()) {
                        val line = lines.getJSONObject(idx)
                        val lineName = line.getString("lineName")
                        val lineId = line.getString("lineId")
                        val startStopName = line.getString("startStopName")
                        val endStopName = line.getString("endStopName")
                        val firstTime = line.getString("firstTime")
                        val lastTime = line.getString("lastTime")
                        val price = line.getString("price")
                        val item = buildResultItem(lineName, "$startStopName - $endStopName")
                        item.setOnClickListener {
                            startActivity(Intent(this,DetailActivity::class.java).also { intent->
                                intent.putExtra("lineId",lineId)
                            })
                        }
                        binding.result.addView(item)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun buildResultItem(lineName: String, s: String): View {
        val view = this.layoutInflater.inflate(R.layout.bus_search_result, null)
        view.findViewById<TextView>(R.id.textView).text = lineName
        view.findViewById<TextView>(R.id.textView2).text = s
        return view
    }

    private fun doSearch(search: String, func: (JSONObject) -> Unit) {
        val dialog = MIUIDialog(this).show {
            progress(text = "正在查询${search}的线路图...")
            cancelable = false
            cancelOnTouchOutside = false
        }
        thread {
            try {
                val host =
                    "http://bus.wuhancloud.cn:9087/website//web/420100/search.do?keyword=$search&type=line"
                Http().apply {
                    this.setGet(host)
                    this.run()
                    val body = this.body
                    Handler(this@MainActivity.mainLooper).post {
                        dialog.cancel()
                        func.invoke(JSONObject(body ?: "{}"))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Handler(this@MainActivity.mainLooper).post {
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
}
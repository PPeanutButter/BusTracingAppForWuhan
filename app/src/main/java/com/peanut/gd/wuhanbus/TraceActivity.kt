package com.peanut.gd.wuhanbus

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

class TraceActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.data?.let { htmlUri->
            val lineId = htmlUri.getQueryParameter("line")
            val traceId = htmlUri.getQueryParameter("bus")
            startActivity(Intent(this,DetailActivity::class.java).also { intent->
                intent.putExtra("lineId",lineId)
                intent.putExtra("traceId",traceId)
            })
            this.finish()
        }
    }
}
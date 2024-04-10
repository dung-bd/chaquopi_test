package com.example.myapplication

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val tv = findViewById<TextView>(R.id.textview)

        if (! Python.isStarted()) {
            Python.start( AndroidPlatform(applicationContext));
        }

        val py = Python.getInstance()
        val pyobj = py.getModule("myscript")
        val obj = pyobj.callAttr("main")
        tv.text = obj.toString()
    }
}
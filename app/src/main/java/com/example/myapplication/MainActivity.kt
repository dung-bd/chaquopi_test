package com.example.myapplication

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.example.myapplication.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    val es = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
    private lateinit var binding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(applicationContext));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.addCategory("android.intent.category.DEFAULT")
                intent.data = Uri.parse(
                    java.lang.String.format(
                        "package:%s",
                        this
                    )
                )
                startActivityForResult(intent, 123)
            } catch (e: java.lang.Exception) {
                val intent = Intent()
                intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                startActivityForResult(intent, 123)
            }
        }

        val imagePickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result: ActivityResult ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                if (data != null) {
                    val fileUri = data.data
                    val path: String = FileUtil(applicationContext, this).getPath(fileUri)
                    if (!TextUtils.isEmpty(path)) {
                        Log.d("fuck", "path : $path")
                        es.execute {
                            progressVisible(true)
                            png2wav(path)
                            progressVisible(false)
                        }
                    } else {
                        Snackbar.make(
                            binding.root, "Cannot find image absolute path", Snackbar.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        val audioPickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result: ActivityResult ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                if (data != null) {
                    val fileUri = data.data
                    val path: String = FileUtil(applicationContext, this).getPath(fileUri)
                    if (!TextUtils.isEmpty(path)) {
                        es.execute {
                            progressVisible(true)
                            wav2png(path)
                            progressVisible(false)
                        }
                    } else {
                        notice("Cannot find image absolute path!")
                    }
                }
            }
        }

        binding.imagepicker.setOnClickListener {
            val intent = Intent()
            intent.action = Intent.ACTION_GET_CONTENT
            intent.type = "image/*"
            imagePickerLauncher.launch(intent)
        }
        binding.audiopicker.setOnClickListener {
            val intent = Intent()
            intent.action = Intent.ACTION_GET_CONTENT
            intent.type = "audio/*"
            audioPickerLauncher.launch(intent)
        }
    }

    @SuppressLint("NewApi")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 123) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    // perform action when allow permission success
                } else {
                    notice("Allow permission for storage access!")
                }
            }
        }
    }

    private fun notice(text: String) {
        runOnUiThread {
            Snackbar.make(binding.root, text, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun progressVisible(isShow: Boolean) {
        runOnUiThread {
            binding.progressCircular.visibility = if (isShow) View.VISIBLE else View.GONE
        }
    }

    @SuppressLint("SetTextI18n")
    fun wav2png(path: String) {
        try {
            val py = Python.getInstance()
            val pyObj = py.getModule("wav2png")
            val obj = pyObj.callAttr("main", path)
            notice("File Spectrogram PNG đã được tạo ra")
            binding.result.text = "File tạo ra được lưu tại $obj"
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("SetTextI18n")
    fun png2wav(path: String) {
        try {
            val py = Python.getInstance()
            val pyObj = py.getModule("png2wav")
            val obj = pyObj.callAttr("main", path)
            notice("File Audio đã được tạo ra")
            binding.result.text = "File tạo ra được lưu tại $obj"
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

}
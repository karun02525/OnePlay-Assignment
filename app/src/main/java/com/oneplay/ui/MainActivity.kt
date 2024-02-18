package com.oneplay.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
import android.media.MediaScannerConnection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.oneplay.R
import com.oneplay.data.Recording
import com.oneplay.databinding.ActivityMainBinding
import com.oneplay.services.NotificationService
import com.oneplay.ui.adapter.RecordingAdapter
import com.oneplay.utils.fileName
import com.oneplay.utils.hasPermissions
import com.oneplay.utils.showVideoSavedNotification
import com.oneplay.viewmodel.MainAppViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File


class MainActivity : AppCompatActivity() {

    companion object {
        val PARENT_DIRECTORY: String = Environment.DIRECTORY_DOWNLOADS
        const val DIRECTORY = "one_play"
    }

    private lateinit var mediaProjectionManager: MediaProjectionManager
    private val viewModel: MainAppViewModel by viewModels()
    private lateinit var file: File
    private lateinit var folder: File
    private lateinit var binding: ActivityMainBinding
    private lateinit var recordingsAdapter: RecordingAdapter
    val inFiles = mutableListOf<Recording>()


    private val isNotificationPermissionGranted: Boolean
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermissions(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }

    private val isRecordAudioPermissionGranted: Boolean
        get() = hasPermissions(Manifest.permission.RECORD_AUDIO)


    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (Manifest.permission.RECORD_AUDIO in permissions) {
                if (permissions[Manifest.permission.RECORD_AUDIO] == true) {
                    startRecording()
                }
            }
            if (Manifest.permission.POST_NOTIFICATIONS in permissions) {
                if (permissions[Manifest.permission.POST_NOTIFICATIONS] == true) {
                    binding.startRecordButton.isEnabled = true
                    stopCheck()
                }
            }
        }

    private fun startForegroundService() {
        val serviceIntent = Intent(NotificationService.START_RECORDING).also {
            it.setClass(this, NotificationService::class.java)
        }
        ActivityCompat.startForegroundService(this, serviceIntent)
    }

    private fun startForegroundServiceReally() {
        val serviceIntent =
            Intent(NotificationService.START_RECORDING_REALLY).also {
                it.setClass(this, NotificationService::class.java)
            }
        ActivityCompat.startForegroundService(this, serviceIntent)
    }

    private fun stopForegroundService() {
        val serviceIntent = Intent(NotificationService.STOP_RECORDING).also {
            it.setClass(this, NotificationService::class.java)
        }
        ActivityCompat.startForegroundService(this, serviceIntent)
    }

    private val requestScreenCapture =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val resultCode = result.resultCode
            if (resultCode != RESULT_OK) {
                binding.startRecordButton.isEnabled = true
                return@registerForActivityResult
            }
            val data = result.data ?: return@registerForActivityResult

            startForegroundService()

            lifecycleScope.launch {
                binding.animationView.visibility=View.VISIBLE
                binding.animationView.playAnimation()
                delay(3000)
                binding.animationView.visibility=View.GONE
                val lMediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
                val isStarted = viewModel.startRecording(this@MainActivity, file, lMediaProjection)
                if (isStarted) {
                    stopCheck(true)
                    startForegroundServiceReally()
                } else {
                    binding.startRecordButton.isEnabled = true
                    stopForegroundService()
                }
            }
        }


    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getStringExtra("action") == "STOP") {
                stopRecording()
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        val builder = VmPolicy.Builder()
        StrictMode.setVmPolicy(builder.build())
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        fileCreate()
        initView()
        launch()
        initPermissions()
        initRecyclerview()

    }


    private fun initView() {
        binding.startRecordButton.isEnabled = false
        stopCheck()
    }

    private fun fileCreate(){
        try {
            folder =
                File(Environment.getExternalStoragePublicDirectory(PARENT_DIRECTORY), DIRECTORY)
            if (!folder.exists()) {
                folder.mkdir()
            }
            file = File(folder, fileName())
        }catch (e:Exception){e.printStackTrace()}

    }

    private fun launch() {
        val intentFilter = IntentFilter("$packageName.RECORDING_EVENT")
        val receiverFlags = ContextCompat.RECEIVER_NOT_EXPORTED
        ContextCompat.registerReceiver(this, broadcastReceiver, intentFilter, receiverFlags)

        mediaProjectionManager =
            ContextCompat.getSystemService(this, MediaProjectionManager::class.java)!!

        binding.startRecordButton.setOnClickListener {
            if (!isRecordAudioPermissionGranted) {
                requestPermissionsLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                return@setOnClickListener
            }
            startRecording()
        }

        binding.stopRecordButton.setOnClickListener {
            stopRecording()
        }

    }

    private fun initPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (isNotificationPermissionGranted) {
                binding.startRecordButton.isEnabled = true
                stopCheck()
            } else {
                requestPermissionsLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
        } else {
            binding.startRecordButton.isEnabled = true
            stopCheck()
        }

        if (viewModel.isRecording()) {
            binding.startRecordButton.isEnabled = false
            stopCheck(true)
        }

    }

    private fun startRecording() {
        requestScreenCapture.launch(mediaProjectionManager.createScreenCaptureIntent())
        binding.startRecordButton.isEnabled = false
    }


    private fun stopRecording() {

        MediaScannerConnection.scanFile(
            this@MainActivity,
            arrayOf<String>(file.path),
            arrayOf("video/mp4"),
            null
        )
        getListFiles(folder)

        stopForegroundService()
        val outFile = viewModel.stopRecording()
        binding.startRecordButton.isEnabled = true
        stopCheck()
        showVideoSavedNotification(outFile)
    }

    private fun initRecyclerview() {
        binding.videosList.apply {
            val linearLayoutManager = LinearLayoutManager(this@MainActivity)
            layoutManager = linearLayoutManager
            updateData()
        }
        getListFiles(folder)
    }

    private fun updateData() {
        inFiles.reverse()
        if(inFiles.isEmpty()){
            binding.videosList.visibility=View.GONE
            binding.messageNoVideo.visibility=View.VISIBLE
        }else{
            binding.videosList.visibility=View.VISIBLE
            binding.messageNoVideo.visibility=View.GONE
        }

        recordingsAdapter = RecordingAdapter(inFiles)
        binding.videosList.adapter = recordingsAdapter

    }

    private fun getListFiles(parentDir: File) {
        val files: Array<File>? = parentDir.listFiles()
        if (files != null) {
            inFiles.clear()
            for (file in files) {
                if (file.name.endsWith(".mp4")) {
                    Log.d("TAGS", "getListFiles: " + file.path)
                    inFiles.add(Recording(
                        uri = file.toUri(),file.name,
                        duration = file.toUri().getMediaDuration(this).toInt(),
                        size = file.length(),
                        modified = file.lastModified()))
                }
            }
        }
        updateData()
    }
    private fun Uri.getMediaDuration(context: Context): Long {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, this)
        val duration = retriever.extractMetadata(METADATA_KEY_DURATION)
        retriever.release()
        return duration?.toLongOrNull() ?: 0
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(broadcastReceiver)
    }

    private fun stopCheck(flag: Boolean = false) {
        if (flag) {
            binding.stopRecordButton.run {
                isEnabled = true
                setBackgroundResource(R.drawable.stop_recoding)
                setTextColor(Color.parseColor("#EAEAEA"))
            }
        } else {
            binding.stopRecordButton.run {
                isEnabled = false
                setBackgroundResource(R.drawable.stop_dis_recoding)
                setTextColor(Color.parseColor("#BAB9B9"))
            }
        }
    }

}
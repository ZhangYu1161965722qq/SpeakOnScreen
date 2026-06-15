package com.example.speakdateandtime

import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.speakdateandtime.ui.theme.SpeakDateAndTimeTheme

class MainActivity : ComponentActivity() {

    private lateinit var screenReceiver: ScreenStateReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        screenReceiver = ScreenStateReceiver()
        registerScreenReceiver()

        setContent {
            SpeakDateAndTimeTheme {
                PowerKeyScreen(
                    onStartService = {
                        startPowerListenerService()
                    }
                )
            }
        }
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenReceiver)
    }

    private fun startPowerListenerService() {
        val serviceIntent = Intent(this, PowerListenerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}

@Composable
fun PowerKeyScreen(onStartService: () -> Unit) {
    var isServiceRunning by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("服务未启动") }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "电源键时间播报",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(32.dp))

            Card(
                modifier = Modifier.padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Text(
                    text = "欢迎使用电源键时间播报\n\n功能说明（启动服务后）：\n• 按电源键亮屏时自动播报当前时间\n• 支持中文语音播报\n• 自动调节音量至最大\n\n使用前请确保：\n1. 已开启系统文字转语音(TTS)\n2. Android 13+需授予通知权限" ,
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                modifier = Modifier.padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Text(
                    text = statusText,
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    onStartService()
                    isServiceRunning = true
                    statusText = "服务运行中"
                },
                enabled = !isServiceRunning
            ) {
                Text(if (isServiceRunning) "服务已启动" else "启动服务")
            }
        }
    }
}
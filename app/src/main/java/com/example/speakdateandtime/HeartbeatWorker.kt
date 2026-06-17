package com.example.speakdateandtime

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Worker
import androidx.work.WorkerParameters
import android.util.Log

class HeartbeatWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        Log.d("HeartbeatWorker", "心跳任务拉起PowerListenerService")
        return try {
            // 静态标记判断服务是否运行
            if (!PowerListenerService.isRunning) {
//                val serviceIntent  = Intent(applicationContext, PowerListenerService::class.java).apply {
//                    // apply 用法，就是直接持有this，他内部的直接是它的属性，省去写对象
//                    // ACTION_START_FOREGROUND是类里的常量
//                    // 这个作用是若代码里需要区分调用服务的来源
//                    action = PowerListenerService.ACTION_START_FOREGROUND
//                }

                val serviceIntent  = Intent(applicationContext, PowerListenerService::class.java)
                // 版本分支：26以上用前台启动，24/25直接startService
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    applicationContext.startForegroundService(serviceIntent )
                } else {
                    applicationContext.startService(serviceIntent )
                }
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("HeartbeatWorker", "启动服务失败", e)
            Result.failure()
        }
    }
}
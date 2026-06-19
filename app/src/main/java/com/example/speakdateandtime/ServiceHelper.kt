// ====== 文件：ServiceHelper.kt ======
package com.example.speakdateandtime

import android.app.ActivityManager
import android.content.Context

/**
 * 服务工具类
 * 用法：ServiceHelper.isRunning(context, PowerListenerService::class.java)
 */
object ServiceHelper {

    /**
     * 检查指定服务是否在运行
     * @param context Context
     * @param serviceClass 服务的 Class
     */
    @Suppress("DEPRECATION")
    fun isRunning(context: Context, serviceClass: Class<*>): Boolean {
        return try {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return false
            manager.getRunningServices(Integer.MAX_VALUE)
                ?.any { it.service.className == serviceClass.name }
                ?: false
        } catch (_: Exception) {
            false
        }
    }
}
// ==================== 包声明 ====================
package com.example.speakdateandtime

// ==================== Android 系统相关导入 ====================
import android.content.BroadcastReceiver  // BroadcastReceiver：广播接收器基类
import android.content.Context           // Context：上下文
import android.content.Intent           // Intent：用于启动服务
import android.os.Build                 // Build：系统版本检测
import android.util.Log                 // Log：日志输出
import android.widget.Toast

/**
 * 开机自启动广播接收器
 * 
 * 功能说明：
 * 1. 监听系统开机完成事件（BOOT_COMPLETED）
 * 2. 系统启动完成后自动启动 PowerListenerService
 * 3. 确保应用在设备重启后无需手动打开即可运行
 * 
 * 技术要点：
 * - 静态注册在 AndroidManifest.xml 中
 * - 需要 RECEIVE_BOOT_COMPLETED 权限
 * - exported=true 允许接收系统广播
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"  // 日志标签
    }

    /**
     * 广播接收回调方法
     * 
     * @param context 上下文环境
     * @param intent  接收到的 Intent，包含动作信息
     */
    override fun onReceive(context: Context, intent: Intent) {
        // ==================== 记录接收到的广播 ====================
        Log.d(TAG, "收到广播: ${intent.action}")

        // ==================== 检查是否为开机完成事件 ====================
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "检测到开机完成，准备启动服务")
            
            // ==================== 启动前台服务 ====================
            // 创建启动 PowerListenerService 的 Intent
            val serviceIntent = Intent(context, PowerListenerService::class.java)
            
            // Android 8.0 (API 26) 及以上必须使用 startForegroundService
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // 启动前台服务（带通知，优先级高，不易被杀死）
                Log.d(TAG, "使用 startForegroundService 启动服务")
                context.startForegroundService(serviceIntent)
            } else {
                // 低版本使用普通启动方式
                Log.d(TAG, "使用 startService 启动服务")
                context.startService(serviceIntent)
            }
            
            Log.d(TAG, "服务启动命令已发送")
        } else {
            Log.w(TAG, "收到非 BOOT_COMPLETED 广播: ${intent.action}")
        }
    }
}

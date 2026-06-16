// 这个类完全是为了兼容Android 7.0之前的版本的，ACTION_SCREEN_ON 是全局系统隐式广播（无指定目标应用，所有注册 App 都会收到）的静态（xml配置）注册，Android 8.0 后台限制规则，只能用动态（代码）注册；

// ==================== 包声明 ====================
package com.example.speakdateandtime

// ==================== 导入依赖 ====================
import android.content.BroadcastReceiver  // BroadcastReceiver：广播接收器基类
import android.content.Context            // Context：应用上下文，用于启动服务
import android.content.Intent             // Intent：用于传递数据和启动组件
import android.os.Build                   // Build：获取系统版本信息
import android.util.Log                   // Log：日志输出

/**
 * 屏幕状态广播接收器
 *
 * 功能说明：
 * 1. 监听系统广播事件（SCREEN_ON/OFF）
 * 2. 当屏幕亮起时启动 PowerListenerService
 * 3. 作为系统和后台服务之间的桥梁
 *
 * 工作原理：
 * - Android 系统在屏幕亮/暗时会发送系统广播
 * - 本接收器捕获 SCREEN_ON 广播并启动服务
 * - 使用 startForegroundService 确保服务在前台运行（Android 8.0+）
 */
class ScreenStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScreenStateReceiver"  // 日志标签
    }

    /**
     * 接收到广播时调用
     *
     * @param context 应用上下文，用于启动服务
     * @param intent  包含广播信息的 Intent 对象
     */
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "收到广播: ${intent.action}")

        // 创建启动 PowerListenerService 的 Intent
        val serviceIntent = Intent(context, PowerListenerService::class.java)

        // 根据广播类型执行不同操作
        when (intent.action) {
            // ==================== 屏幕亮起事件 ====================
            Intent.ACTION_SCREEN_ON -> {
                // 在 Intent 中添加动作标识，供 Service 识别
                serviceIntent.putExtra("action", "SCREEN_ON")

                // 根据系统版本选择启动方式
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // Android 8.0 (API 26) 及以上必须使用前台服务
                    // 前台服务会显示通知，优先级更高，不易被系统杀死
                    Log.d(TAG, "使用 startForegroundService 启动服务")
                    context.startForegroundService(serviceIntent)
                } else {
                    // 旧版本可以直接启动普通服务
                    Log.d(TAG, "使用 startService 启动服务")
                    context.startService(serviceIntent)
                }
                Log.d(TAG, "服务启动命令已发送")
            }

        }
    }
}
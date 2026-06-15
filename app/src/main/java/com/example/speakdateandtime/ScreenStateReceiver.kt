// ==================== 包声明 ====================
package com.example.speakdateandtime

// ==================== 导入依赖 ====================
import android.content.BroadcastReceiver  // BroadcastReceiver：广播接收器基类
import android.content.Context            // Context：应用上下文，用于启动服务
import android.content.Intent             // Intent：用于传递数据和启动组件
import android.os.Build                   // Build：获取系统版本信息

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
    
    /**
     * 接收到广播时调用
     * 
     * @param context 应用上下文，用于启动服务
     * @param intent  包含广播信息的 Intent 对象
     */
    override fun onReceive(context: Context, intent: Intent) {
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
                    context.startForegroundService(serviceIntent)
                } else {
                    // 旧版本可以直接启动普通服务
                    context.startService(serviceIntent)
                }
            }
            
            // ==================== 其他事件 ====================
            // 注意：当前版本不处理 SCREEN_OFF 等其他事件
            // 原因：只在用户主动亮屏时报时，避免屏幕超时误触发
        }
    }
}
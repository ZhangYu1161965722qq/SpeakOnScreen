// ==================== 包声明 ====================
package com.example.speakdateandtime

// ==================== Android 系统相关导入 ====================
import android.app.*                    // 通知相关类：Notification, NotificationChannel, Service
import android.content.Intent           // Intent：接收启动参数
import android.media.AudioManager       // AudioManager：音频管理器，控制音量
import android.os.Build                 // Build：系统版本检测
import android.os.IBinder               // IBinder：服务绑定接口（本例未使用）
import android.speech.tts.TextToSpeech  // TextToSpeech：文字转语音引擎
import androidx.core.app.NotificationCompat  // 通知兼容性库

// ==================== Java 工具类导入 ====================
import java.util.*                      // Calendar, Locale 等工具类

/**
 * 电源键播报服务（前台服务）
 * 
 * 功能说明：
 * 1. 接收屏幕亮屏事件，播报当前时间
 * 2. 使用 TTS（文字转语音）引擎进行中文语音播报
 * 3. 自动调节音量为最大，播报结束后恢复原音量
 * 4. 作为前台服务运行，降低被系统杀死的概率
 * 5. 支持进程自动恢复（START_REDELIVER_INTENT）
 * 
 * 技术要点：
 * - 实现 TextToSpeech.OnInitListener 接口，监听 TTS 初始化状态
 * - 使用前台服务 + 通知，提高服务优先级
 * - 防抖机制：避免短时间内重复触发
 * - 返回 START_REDELIVER_INTENT，确保服务被杀死后能自动重启
 */
class PowerListenerService : Service(), TextToSpeech.OnInitListener {

    // ==================== 成员变量 ====================
    private var tts: TextToSpeech? = null           // TTS 引擎实例
    private var isTtsReady = false                  // TTS 是否初始化完成
    private var audioManager: AudioManager? = null  // 音频管理器
    private var originalVolume = 0                  // 原始音量（播报前保存，播报后恢复）

    // ==================== 伴生对象（类似 Java 的 static）====================
    companion object {
        private const val NOTIFICATION_ID = 1001    // 通知 ID（唯一标识）
        private const val CHANNEL_ID = "power_tts_channel"  // 通知渠道 ID
        private var lastSpeakTime = 0L              // 上次播报时间戳（用于防抖）
        private const val DEBOUNCE_MS = 1000L       // 防抖时间：1000 毫秒（1 秒）
    }

    /**
     * 服务创建时调用（只调用一次）
     * 用于初始化资源和创建前台通知
     */
    override fun onCreate() {
        super.onCreate()
        
        // ==================== 初始化音频管理器 ====================
        // getSystemService 获取系统服务，AUDIO_SERVICE 是音频管理服务
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        
        // ==================== 初始化 TTS 引擎 ====================
        // TextToSpeech 构造函数需要传入 Context 和初始化监听器
        // this 既是 Context 也是 OnInitListener（因为实现了该接口）
        tts = TextToSpeech(this, this)
        
        // ==================== 创建通知渠道（Android 8.0+ 必需）====================
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 创建通知渠道
            // 参数：渠道ID、渠道名称、重要性级别
            val channel = NotificationChannel(
                CHANNEL_ID,                         // 渠道 ID
                "电源键播报服务",                    // 渠道名称（用户可见）
                NotificationManager.IMPORTANCE_LOW  // 低重要性（不发出声音）
            )
            // 注册通知渠道到系统
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        
        // ==================== 启动前台服务 ====================
        // 前台服务必须显示通知，这样用户知道服务在运行
        // 前台服务优先级高，不容易被系统杀死
        startForeground(
            NOTIFICATION_ID,  // 通知 ID
            NotificationCompat.Builder(this, CHANNEL_ID)  // 通知构建器
                .setContentTitle("电源键播报")              // 通知标题
                .setContentText("服务运行中，按电源键将播报时间")  // 通知内容
                .setSmallIcon(android.R.drawable.ic_lock_lock)  // 小图标（使用系统图标）
                .setPriority(NotificationCompat.PRIORITY_LOW)   // 低优先级
                .build()                                        // 构建通知对象
        )
    }

    /**
     * TTS 初始化完成回调
     * 
     * @param status 初始化状态：SUCCESS 表示成功，ERROR 表示失败
     */
    override fun onInit(status: Int) {
        // 检查初始化是否成功
        isTtsReady = status == TextToSpeech.SUCCESS
        
        if (isTtsReady) {
            // ==================== 配置 TTS 参数 ====================
            // 设置语言为中文
            tts?.language = Locale.CHINESE
            
            // 设置语速：0.5f 表示正常速度的一半（较慢，更清晰）
            // 取值范围：0.1f（最慢）~ 2.0f（最快），1.0f 为正常速度
            tts?.setSpeechRate(0.5f)
        }
    }

    /**
     * 服务启动命令处理
     * 
     * 每次启动服务时都会调用此方法
     * 
     * @param intent  启动服务的 Intent
     * @param flags   启动标志
     * @param startId 启动 ID（唯一标识本次启动请求）
     * @return 服务行为常量：START_REDELIVER_INTENT 表示重启后重新投递 Intent
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 从 Intent 中获取动作标识
        val currentStatus = intent?.getStringExtra("action")
        
        // 只处理 SCREEN_ON 事件（亮屏）
        if (currentStatus == "SCREEN_ON") {
            // ==================== 防抖处理 ====================
            // 获取当前时间戳
            val now = System.currentTimeMillis()
            
            // 如果距离上次播报超过 1 秒，才执行播报
            // 这样可以避免快速连续触发导致多次播报
            if (now - lastSpeakTime >= DEBOUNCE_MS) {
                // 更新上次播报时间
                lastSpeakTime = now
                
                // 执行语音播报
                speakCurrentTime()
            }
        }
        
        // ==================== 返回服务行为常量 ====================
        // START_REDELIVER_INTENT：如果服务被杀死，系统会重启服务并重新投递最后一个 Intent
        // 这确保了服务的可靠性，即使进程被杀死也能自动恢复
        return START_REDELIVER_INTENT
    }

    /**
     * 播报当前时间
     * 
     * 核心功能：
     * 1. 确保 TTS 已就绪
     * 2. 将音量调至最大
     * 3. 播报时间文本
     * 4. 3 秒后恢复原音量
     */
    private fun speakCurrentTime() {
        // ==================== 确保 TTS 就绪 ====================
        if (!isTtsReady) {
            // 如果 TTS 未就绪，重新配置（理论上不会发生，因为 onCreate 已初始化）
            tts?.language = Locale.CHINESE
            tts?.setSpeechRate(0.5f)
        }
        
        // ==================== 调节音量 ====================
        // 保存当前音量并设置为最大
        setMaxVolume()
        
        // ==================== 执行语音播报 ====================
        // QUEUE_FLUSH：清空队列，立即播报（打断之前的播报）
        // 参数：文本、队列模式、附加参数、播报完成回调
        tts?.speak(getCurrentTimeText(), TextToSpeech.QUEUE_FLUSH, null, null)
        
        // ==================== 延迟恢复音量 ====================
        // 使用 Handler 在主线程延迟执行
        // 3 秒后恢复原始音量（给足够时间让播报完成）
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
        }, 3000)
    }

    /**
     * 设置最大音量
     * 
     * 工作原理：
     * 1. 保存当前音量到 originalVolume
     * 2. 获取最大音量值
     * 3. 设置为最大音量
     */
    private fun setMaxVolume() {
        // 保存当前音量（STREAM_MUSIC 是媒体音量通道）
        originalVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
        
        // 获取最大音量值
        val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
        
        // 设置为最大音量
        // 第三个参数 flags：0 表示无特殊标志
        audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)
    }

    /**
     * 获取当前时间的文本描述
     * 
     * 格式示例："现在是2026年6月15日。下午3点25分"
     * 
     * 特点：
     * 1. 使用句号分隔日期和时间，产生自然停顿
     * 2. 根据小时智能判断时间段（上午/下午/晚上等）
     * 3. 使用 12 小时制更符合日常习惯
     * 
     * @return 格式化后的时间文本
     */
    private fun getCurrentTimeText(): String {
        // 获取当前时间
        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY)  // 24 小时制（0-23）
        
        // ==================== 判断时间段 ====================
        // 根据小时数确定属于哪个时间段
        val period = when (hour) {
            in 0..4 -> "深夜"      // 0-4点：深夜
            in 5..11 -> "上午"     // 5-11点：上午
            in 12..13 -> "中午"    // 12-13点：中午
            in 14..17 -> "下午"    // 14-17点：下午
            in 18..21 -> "晚上"    // 18-21点：晚上
            else -> "深夜"         // 22-23点：深夜
        }
        
        // ==================== 转换为 12 小时制 ====================
        val hour12 = when (hour) {
            0 -> 12                // 0点 → 12点（午夜）
            in 13..23 -> hour - 12 // 13-23点 → 1-11点
            else -> hour           // 1-12点保持不变
        }
        
        // ==================== 组装时间文本 ====================
        // 使用句号（。）分隔日期和时间，TTS 会在此处自然停顿
        return "现在是${now.get(Calendar.YEAR)}年${now.get(Calendar.MONTH) + 1}月${now.get(Calendar.DAY_OF_MONTH)}日。${period}${hour12}点${now.get(Calendar.MINUTE)}分"
    }

    /**
     * 服务销毁时调用
     * 
     * 清理资源：
     * 1. 停止 TTS 播报
     * 2. 关闭 TTS 引擎
     */
    override fun onDestroy() {
        // 停止当前播报
        tts?.stop()
        // 关闭 TTS 引擎，释放资源
        tts?.shutdown()
        // 调用父类销毁方法
        super.onDestroy()
    }

    /**
     * 服务绑定接口（本例不使用绑定模式）
     * 
     * 返回 null 表示不支持绑定，只能通过 startService 启动
     */
    override fun onBind(intent: Intent?): IBinder? = null
}
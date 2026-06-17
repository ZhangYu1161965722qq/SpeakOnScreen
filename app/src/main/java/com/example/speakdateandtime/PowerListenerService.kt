// ==================== 包声明 ====================
package com.example.speakdateandtime

// ==================== Android 系统相关导入 ====================
import android.app.*                    // 通知相关类：Notification, NotificationChannel, Service
import android.content.BroadcastReceiver  // BroadcastReceiver：广播接收器
import android.content.Context           // Context：上下文
import android.content.Intent           // Intent：接收启动参数
import android.content.IntentFilter     // IntentFilter：定义广播接收器要监听的事件类型
import android.media.AudioManager       // AudioManager：音频管理器，控制音量
import kotlin.math.ceil
import android.os.BatteryManager        // BatteryManager：电池管理
import android.os.Build                 // Build：系统版本检测
import android.os.IBinder               // IBinder：服务绑定接口（本例未使用）
import android.speech.tts.TextToSpeech  // TextToSpeech：文字转语音引擎
import androidx.core.app.NotificationCompat  // 通知兼容性库
import androidx.media.app.NotificationCompat.MediaStyle
import android.util.Log                 // Log：日志输出

// ==================== Java 工具类导入 ====================
import java.util.Calendar
import java.util.Locale

// ==================== 农历库导入（tyme4kt）====================
import com.tyme.solar.SolarDay// 公历日期（包含农历、星期等信息）


/**
 * 亮屏播报服务（前台服务）
 *
 * 功能说明：
 * 1. 接收屏幕亮屏事件，播报当前时间
 * 2. 使用 TTS（文字转语音）引擎进行中文语音播报
 * 3. 自动调节音量，播报结束后恢复原音量
 * 4. 作为前台服务运行，降低被系统杀死的概率
 * 5. 支持进程自动恢复（START_STICKY，重启不携带旧Intent）
 *
 * 技术要点：
 * - 实现 TextToSpeech.OnInitListener 接口，监听 TTS 初始化状态
 * - 使用前台服务 + 通知，提高服务优先级
 * - 防抖机制：避免短时间内重复触发
 * - 返回 START_STICKY，服务被杀后系统自动重启，不复用历史Intent
 */
class PowerListenerService : Service(), TextToSpeech.OnInitListener {

    // ==================== 伴生对象（类似 Java 的 static）====================
    companion object {
        private const val TAG = "PowerListenerService"  // 日志标签

        private const val CHANNEL_FG = "power_tts_fg"    // 前台常驻渠道：普通DEFAULT

        private var lastSpeakTime = 0L              // 上次播报时间戳（用于防抖）
        private const val DEBOUNCE_MS = 1000L       // 防抖时间：1000 毫秒（1 秒）
        var isRunning = false  // service是否在运行全局标记

        private const val SPEECH_RATE = 0.6f    // 语速 0.6 取值范围：0.1f（最慢）~ 2.0f（最快），1.0f 为正常速度

        private const val VOLUME_DAY = 0.7      // 白天音量
        private const val VOLUME_NIGHT = 0.2    // 深夜音量

        private var channelCreated = false // 新增：渠道仅创建一次，避免重复调用系统渠道接口
    }

    // ==================== 成员变量 ====================
    private var tts: TextToSpeech? = null           // TTS 引擎实例
    private var isTtsReady = false                  // TTS 是否初始化完成
    private var audioManager: AudioManager? = null  // 音频管理器
    private var batteryManager: BatteryManager? = null // 电池管理器，全局复用，无需每次播报重复获取
    private var originalVolume = 0                  // 原始音量（播报前保存，播报后恢复）
    private var screenReceiver: BroadcastReceiver? = null  // 亮屏广播接收器
    private var notificationManager: NotificationManager? = null // 新增：通知管理器全局实例


    /**
     * 服务创建时调用（只调用一次）
     * 用于初始化资源和创建前台通知
     */
    override fun onCreate() {
        super.onCreate()
        isRunning = true
        Log.d(TAG, "==================== 服务创建 ====================")

        // ==================== 初始化音频管理器 ====================
        // getSystemService 获取系统服务，AUDIO_SERVICE 是音频管理服务
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        // 初始化电池管理器，安全可空强转，防止少数设备返回null崩溃
        batteryManager = getSystemService(BATTERY_SERVICE) as? BatteryManager

        // 新增：初始化通知管理器
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // ==================== 初始化 TTS 引擎 ====================
        // TextToSpeech 构造函数需要传入 Context 和初始化监听器
        // this 既是 Context 也是 OnInitListener（因为实现了该接口）
        tts = TextToSpeech(this, this)

        // ==================== 设置 TTS 播报完成监听器 ====================
        // 用于在播报完成后自动恢复音量，确保不会提前恢复
        tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                // 播报开始时调用（不需要处理）
            }

            override fun onDone(utteranceId: String?) {
                // ✅ 播报真正完成时调用，此时才恢复原始音量
                // 这确保了无论播报多长，都会等完全结束后才恢复音量
                audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
            }

            override fun onError(utteranceId: String?) {
                // 播报出错时也恢复音量
                audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
            }
        })

        // 统一初始化通知、广播（抽取公共方法，onStartCommand重启时复用）
        initNotifyAndReceiver()

        Log.d(TAG, "服务创建完成，前台服务已启动")
    }

    /**
     * 统一初始化方法：创建渠道、前台通知、滑动通知、注册亮屏广播
     */
    private fun initNotifyAndReceiver() {
        // ==================== 创建通知渠道（Android 8.0+ 必需）====================
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !channelCreated) {
            // 创建通知渠道
            // 参数：渠道ID、渠道名称、重要性级别
            val channelFg  = NotificationChannel(
                CHANNEL_FG,                         // 渠道 ID
                "亮屏播报-后台常驻",                    // 渠道名称（用户可见）
                NotificationManager.IMPORTANCE_DEFAULT  // 优先级
            ).apply {
                // 锁屏完全显示通知（PUBLIC=锁屏可见完整内容，PRIVATE也可用）
                //VISIBILITY_PUBLIC = 1 锁屏完整显示内容；VISIBILITY_PRIVATE = 0 锁屏只提示有通知，隐藏详情；VISIBILITY_SECRET = -1 锁屏完全不显示
                lockscreenVisibility = 1
                // 关闭红点角标（可选）
                setShowBadge(false)
                // 静音，不弹窗响铃（不影响锁屏显示）
                setSound(null, null)

                // 开启允许长通知展开
                enableVibration(false)
            }

            // 注册通知渠道到系统
            notificationManager?.createNotificationChannel(channelFg)
            channelCreated = true // 标记渠道已创建，后续不再重复执行
        }

        // 停止播报意图
        val stopIntent = Intent(this, PowerListenerService::class.java).apply {
            putExtra("action", "CANCEL_SPEECH")
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            2001,
            stopIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        // 媒体音乐样式
        val mediaStyle = MediaStyle()
            .setShowActionsInCompactView(0)  // 折叠视图显示前1个按钮
            .setMediaSession(null)

        // 构建前台通知 // 前台服务必须显示通知，这样用户知道服务在运行；前台服务优先级高，不容易被系统杀死
        val notifyBuilder = NotificationCompat.Builder(this, CHANNEL_FG)
            .setContentTitle("\uD83D\uDD0A亮屏报时运行中")
            .setContentText("点✖停止播报")
//            .setSubText("点击✖按钮停止语音播报")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)   // 常驻前台通知，无法手动划掉
            .setShowWhen(false)     // 下角的时间戳
            .setStyle(mediaStyle)   // 先绑定媒体样式
            .setPriority(NotificationCompat.PRIORITY_LOW)   // 优先级
            .addAction(android.R.drawable.ic_delete, "停止", stopPendingIntent)
            .build()

        // ==================== 启动前台服务 ====================
        startForeground(1001, notifyBuilder)

        // ==================== 注册亮屏广播接收器 ====================
        registerScreenOnReceiver()
    }

    /**
     * TTS 初始化完成回调
     *
     * @param status 初始化状态：SUCCESS 表示成功，ERROR 表示失败
     */
    override fun onInit(status: Int) {
        // 检查初始化是否成功
        isTtsReady = status == TextToSpeech.SUCCESS
        Log.d(TAG, "TTS 初始化状态: ${if (isTtsReady) "成功" else "失败"}")

        if (isTtsReady) {
            // ==================== 配置 TTS 参数 ====================
            // 设置语言为中文
            tts?.language = Locale.CHINESE

            // 设置语速
            tts?.setSpeechRate(SPEECH_RATE)
            Log.d(TAG, "TTS 配置完成 - 语言: 中文, 语速: $SPEECH_RATE")
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
        Log.d(TAG, "收到启动命令 - startId: $startId, action: ${intent?.getStringExtra("action")}")

        // ✅ 每次启动时确保接收器已注册（如果已注册则不会重复注册）
        if (screenReceiver == null) {
            registerScreenOnReceiver()
        }

        // 取消播报
        if (intent?.getStringExtra("action") == "CANCEL_SPEECH") {
            tts?.stop()
            Log.d(TAG, "✅ 用户取消播报")
        }


        // ==================== 返回服务行为常量 ====================
        // START_REDELIVER_INTENT：如果服务被杀死，系统会重启服务并重新投递最后一个 Intent
        // START_STICKY：服务被杀后系统自动重启，不复用历史Intent
        return START_STICKY  // 系统会在资源允许时自动重启服务，确保进程被杀死也能自动恢复
    }

    /**
     * 播报当前时间
     *
     * 核心功能：
     * 1. 确保 TTS 已就绪
     * 2. 调音量
     * 3. 播报时间文本
     * 4. ✅ 播报完成后通过 UtteranceProgressListener.onDone() 自动恢复原音量
     */
    private fun speakCurrentTime() {
        Log.d(TAG, "开始播报流程")

        // 获取日期时间和电量
        val (timeText,period) = getCurrentTimeText()
        val batteryText = getBatteryInfo()

        val speakText = timeText + "\n" + batteryText

        // ==================== 调节音量 ====================
        // 保存当前音量并设置音量
        val percent : Double = if (period == "深夜") VOLUME_NIGHT else VOLUME_DAY
        setMaxVolume(percent = percent)

        Log.d(TAG, "播报内容: $speakText")
        // ==================== 执行语音播报 ====================
        // QUEUE_FLUSH：清空队列，立即播报（打断之前的播报）
        // 最后一个参数 utteranceId：用于标识这次播报，onDone 回调时会用到
        // ✅ 关键：传入 "time_utterance" 作为标识，让 UtteranceProgressListener 能识别
        tts?.speak(speakText, TextToSpeech.QUEUE_FLUSH, null, "time_utterance")
    }

    /**
     * 设置音量
     *
     * 工作原理：
     * 1. 保存当前音量到 originalVolume
     * 2. 设置音量
     */
    private fun setMaxVolume(percent: Double = 0.8) {
        // 保存当前音量（STREAM_MUSIC 是媒体音量通道）
        originalVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0

        // 获取最大音量值
        val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15

        // 音量百分比向上取整
        val targetVolume = ceil(maxVolume * percent).toInt()

        // 确保不超出范围
        val finalVolume = targetVolume.coerceIn(1, maxVolume)

        // 设置音量
        // 第三个参数 flags：0 表示无特殊标志
        audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, finalVolume, 0)
    }

    /**
     * 获取当前时间的文本描述
     * @return 格式化后的时间文本
     */
    private fun getCurrentTimeText(): Pair<String, String> {
        // 用系统 Calendar 获取当前的年、月、日
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1  // Calendar的月份从0开始
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        // 用 tyme4j 将公历转换为农历
        val solar = SolarDay.fromYmd(year, month, day)

        // 获取农历和星期
        val solarDate = solar.toString()           // 公历: 2026年6月16日

        var lunarDate = solar.getLunarDay().toString()  // 农历: 农历丙午年五月廿一
        // 修复：增加indexOf判空，防止字符串越界崩溃
        val yearIndex = lunarDate.indexOf("年")
        lunarDate = if (yearIndex != -1) lunarDate.substring(yearIndex + 1) else lunarDate

        val weekDay = solar.getWeek()              // 星期: 星期二

        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        // ==================== 判断时间段 ====================
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
            0 -> 12                         // 0点 → 12点（午夜）
            in 13..23 -> hour - 12    // 13-23点 → 1-11点
            else -> hour                    // 1-12点保持不变
        }

        val timeText = "现在时间：${period}${hour12}点${minute}分\n${solarDate}；星期${weekDay}；${lunarDate}。"
        return Pair(timeText,period)
    }

    /**
     * 获取电池信息
     *
     * 获取当前电量百分比和充电状态，并返回一个包含信息的字符串。
     *
     * @return 包含电量信息和充电状态的字符串
     */
    private fun getBatteryInfo(): String {
        // ==================== 获取电量信息 ====================
        // 优化：使用全局复用的batteryManager，不再每次重新获取系统服务
        val batteryLevel = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0
        val charging = batteryManager?.isCharging ?: false

        // 组装基础时间文本
        var batteryText = "电量：${batteryLevel}%"

        if (charging) {
            // 如果正在充电，添加提示
            batteryText += "，充电中"
        } else {
            // 如果电量低于20%，添加警告
            if (batteryLevel < 20) {
                batteryText += "，电量低，请充电！电量低，请充电！"
            }
        }

        return batteryText
    }

    /**
     * 注册亮屏广播接收器
     *
     * 功能说明：
     * 1. 监听 SCREEN_ON 事件（亮屏）
     * 2. 亮屏时自动播报时间和电量
     * 3. 防止重复注册，确保后台持续监听
     */
    private fun registerScreenOnReceiver() {
        // 先尝试注销（防止重复注册）
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {
            // 如果未注册过，会抛出异常，忽略即可
        }

        // 创建 IntentFilter，指定要监听的广播类型
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)  // 只添加亮屏事件
        }

        // 创建广播接收器
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_ON) {
                    // ✅ 防抖逻辑
                    val now = System.currentTimeMillis()
                    if (now - lastSpeakTime >= DEBOUNCE_MS) {
                        lastSpeakTime = now
                        // 亮屏时执行播报
                        speakCurrentTime()
                    } else {
                        Log.d(TAG, "防抖触发，忽略本次亮屏")
                    }

                }
            }
        }

        // 注册接收器
        registerReceiver(screenReceiver, filter)
    }

    /**
     * 服务销毁时调用
     *
     * 清理资源：
     * 1. 停止 TTS 播报
     * 2. 关闭 TTS 引擎
     * 3. 注销广播接收器
     */
    override fun onDestroy() {
        isRunning = false

        if (originalVolume > 0) {
            // 还原媒体音量，防止进程异常被杀后音量卡死
            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
        }

        // 注销广播接收器
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {
            // 忽略异常
        }

        // 停止当前播报
        tts?.stop()
        // 关闭 TTS 引擎，释放资源
        tts?.shutdown()
        // 调用父类销毁方法
        super.onDestroy()

        screenReceiver = null
    }

    /**
     * 服务绑定接口（本例不使用绑定模式）
     *
     * 返回 null 表示不支持绑定，只能通过 startService 启动
     */
    override fun onBind(intent: Intent?): IBinder? = null
}
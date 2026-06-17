// ==================== 包声明 ====================
package com.example.speakdateandtime

// ==================== Android 系统相关导入 ====================
import android.content.Intent          // Intent：用于启动服务和传递数据
import android.os.Build                // Build：获取设备系统版本信息
import android.os.Bundle               // Bundle：保存 Activity 的状态数据
import android.util.Log                // Log：日志输出

// ==================== Jetpack Compose 相关导入 ====================
import androidx.activity.ComponentActivity  // ComponentActivity：Compose 项目的基础 Activity
import androidx.activity.compose.setContent // setContent：设置 Compose UI 内容
import androidx.compose.foundation.layout.* // 布局组件：Column、Row、Spacer 等
import androidx.compose.material3.*         // Material3 组件：Button、Card、Text 等
import androidx.compose.runtime.*           // 状态管理：remember、mutableStateOf 等
import androidx.compose.ui.Alignment        // 对齐方式：居中、左对齐等
import androidx.compose.ui.Modifier         // Modifier：修饰组件样式和布局
import androidx.compose.ui.unit.dp          // dp：密度无关像素单位
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.delay             // 新增：协程延迟，用来同步服务状态

// ==================== 项目主题导入 ====================
import com.example.speakdateandtime.ui.theme.SpeakDateAndTimeTheme
import java.util.concurrent.TimeUnit

/**
 * 主界面 Activity
 *
 * 功能说明：
 * 1. 显示控制界面，用户可以启动播报服务
 * 2. 管理服务的生命周期
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"  // 日志标签
    }

    /**
     * Activity 创建时调用
     * @param savedInstanceState 保存的实例状态（旋转屏幕等场景使用）
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "==================== Activity 创建 ====================")

        // ==================== 设置 Compose UI ====================
        setContent {
            // 应用 Material3 主题
            SpeakDateAndTimeTheme {
                // 显示电源键播报控制界面
                PowerKeyScreen(
                    // 启动服务的回调函数
                    onStartService = {
                        startPowerListenerService()
                    },
                    // 新增：停止服务回调
                    onStopService = {
                        stopPowerListenerService()
                    }
                )
            }
        }

        // ✅ 调用启动 WorkManager 心跳
        startHeartbeat()
    }

    // 新增：停止播报服务方法
    private fun stopPowerListenerService() {
        Log.d(TAG, "用户触发停止服务")
        val serviceIntent = Intent(this, PowerListenerService::class.java)
        stopService(serviceIntent)
        Log.d(TAG, "停止服务指令已发送")
    }

    // 启动WorkManager
    private fun startHeartbeat() {
        val workRequest = PeriodicWorkRequestBuilder<HeartbeatWorker>(
            15, TimeUnit.MINUTES  // 每15分钟检查一次
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "Heartbeat",
            ExistingPeriodicWorkPolicy.KEEP,  // 如果已存在则保留
            workRequest
        )
        Log.d("MainActivity", "WorkManager 心跳已启动")
    }

    /**
     * Activity 恢复时调用
     * 确保服务在后台运行（如果被关闭则重新启动）
     */
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "Activity 恢复，注册屏幕广播接受器")

        // 优化：仅服务未运行时才启动，避免重复发送启动指令
        if (!PowerListenerService.isRunning) {
            startPowerListenerService()
        }
    }

    /**
     * 启动电源键播报服务
     * 根据系统版本选择启动方式（Android 8.0+ 必须用前台服务）
     */
    private fun startPowerListenerService() {
        Log.d(TAG, "用户手动启动服务")

        // 创建启动 PowerListenerService 的 Intent
        val serviceIntent = Intent(this, PowerListenerService::class.java)

        // Android 8.0 (API 26) 及以上必须使用 startForegroundService
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(TAG, "使用 startForegroundService 启动服务")
            startForegroundService(serviceIntent)  // 启动前台服务（带通知）
        } else {
            Log.d(TAG, "使用 startService 启动服务")
            startService(serviceIntent)              // 普通启动服务
        }
        Log.d(TAG, "服务启动命令已发送")
    }
}

/**
 * 电源键播报控制界面（Composable 函数）
 *
 * Composable 函数是 Jetpack Compose 的核心，用于声明式地构建 UI
 *
 * @param onStartService 启动服务的回调函数
 * @param onStopService 停止服务的回调函数（新增）
 */
@Composable
fun PowerKeyScreen(onStartService: () -> Unit, onStopService: () -> Unit) {
    // ==================== 状态管理 ====================
    // remember：在重组时记住这个值，避免每次都重新创建
    // mutableStateOf：创建一个可变状态，值改变时会自动触发 UI 重组
    // 修改：初始化直接读取服务真实运行状态，不再写死默认值
    var isServiceRunning by remember { mutableStateOf(PowerListenerService.isRunning) }
    var statusText by remember { mutableStateOf(if (isServiceRunning) "服务运行中" else "服务未启动") }

    // 新增：实时同步后台服务真实状态，解决切后台、重启Worker后UI状态错乱
    LaunchedEffect(Unit) {
        while (true) {
            // 每秒读取一次服务全局标记，同步UI
            isServiceRunning = PowerListenerService.isRunning
            statusText = if (isServiceRunning) "服务运行中" else "服务未启动"
            delay(1000)
        }
    }

    // ==================== UI 布局 ====================
    // Scaffold：Material Design 的基础布局框架，提供顶部栏、底部栏等标准结构
    // innerPadding：Scaffold 提供的内边距，用于适配系统栏（状态栏、导航栏）
    Scaffold(
        modifier = Modifier.fillMaxSize()  // 填满整个屏幕
    ) { innerPadding ->
        // Column：垂直排列子组件的布局容器
        Column(
            modifier = Modifier
                .fillMaxSize()              // 填满父容器
                .padding(innerPadding),     // 应用 Scaffold 的内边距
            horizontalAlignment = Alignment.CenterHorizontally,  // 水平居中
            verticalArrangement = Arrangement.Center             // 垂直居中
        ) {
            // ==================== 标题 ====================
            Text(
                text = "亮屏播报时间",
                style = MaterialTheme.typography.headlineMedium  // 使用主题定义的标题样式
            )
            Spacer(modifier = Modifier.height(16.dp))  // 间距

            // ==================== 使用说明 ====================
            // Card：卡片组件，带有阴影和圆角
            Card(
                modifier = Modifier.padding(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Text(
                    text = "功能说明（启动服务后）：\n• 按电源键亮屏时自动播报当前时间\n\n使用前确保：\n1.已开启系统文字转语音(TTS)\n2.设置：授予通知权限，锁定后台免清理，电池优化不限制，自启动" ,
                    modifier = Modifier.padding(16.dp),  // 内边距
                    style = MaterialTheme.typography.bodyLarge  // 正文样式
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ==================== 状态显示 ====================
            Text(
                text = statusText,  // 动态显示状态文本
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = androidx.compose.ui.graphics.Color(0xFF666666)  // 固定浅灰色
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ==================== 按钮区域：横向并排 启动 + 停止 ====================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 启动按钮
                Button(
                    onClick = {
                        // 点击按钮时执行
                        onStartService()           // 调用启动服务回调
                        // 不再手动赋值状态，由上方LaunchedEffect自动同步真实状态
                    },
                    modifier = Modifier
                        .height(56.dp)
                      //  .weight(1f) // 平分宽度
                        .padding(horizontal = 12.dp),
                    enabled = !isServiceRunning  // 服务运行时禁用按钮（防止重复启动）
                ) {
                    // 根据状态显示不同的按钮文字
                    Text(if (isServiceRunning) "服务已启动" else "启动服务")
                }

                // 停止服务按钮
                OutlinedButton(
                    onClick = onStopService,
                    modifier = Modifier
                        .height(56.dp)
                      //  .weight(1f) // 和左边按钮平分宽度
                        .padding(horizontal = 12.dp),
                    enabled = isServiceRunning
                ) {
                    Text("停止服务")
                }
            }
        }
    }
}
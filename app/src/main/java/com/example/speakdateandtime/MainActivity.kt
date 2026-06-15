// ==================== 包声明 ====================
package com.example.speakdateandtime

// ==================== Android 系统相关导入 ====================
import android.content.Intent          // Intent：用于启动服务和传递数据
import android.content.IntentFilter    // IntentFilter：定义广播接收器要监听的事件类型
import android.os.Build                // Build：获取设备系统版本信息
import android.os.Bundle               // Bundle：保存 Activity 的状态数据

// ==================== Jetpack Compose 相关导入 ====================
import androidx.activity.ComponentActivity  // ComponentActivity：Compose 项目的基础 Activity
import androidx.activity.compose.setContent // setContent：设置 Compose UI 内容
import androidx.compose.foundation.layout.* // 布局组件：Column、Row、Spacer 等
import androidx.compose.material3.*         // Material3 组件：Button、Card、Text 等
import androidx.compose.runtime.*           // 状态管理：remember、mutableStateOf 等
import androidx.compose.ui.Alignment        // 对齐方式：居中、左对齐等
import androidx.compose.ui.Modifier         // Modifier：修饰组件样式和布局
import androidx.compose.ui.unit.dp          // dp：密度无关像素单位

// ==================== 项目主题导入 ====================
import com.example.speakdateandtime.ui.theme.SpeakDateAndTimeTheme

/**
 * 主界面 Activity
 * 
 * 功能说明：
 * 1. 注册屏幕状态广播接收器，监听屏幕亮屏事件
 * 2. 显示控制界面，用户可以启动播报服务
 * 3. 管理服务的生命周期
 */
class MainActivity : ComponentActivity() {

    // 屏幕状态广播接收器（lateinit 表示稍后初始化，避免可空类型）
    private lateinit var screenReceiver: ScreenStateReceiver

    /**
     * Activity 创建时调用
     * @param savedInstanceState 保存的实例状态（旋转屏幕等场景使用）
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ==================== 初始化广播接收器 ====================
        screenReceiver = ScreenStateReceiver()
        registerScreenReceiver()

        // ==================== 设置 Compose UI ====================
        setContent {
            // 应用 Material3 主题
            SpeakDateAndTimeTheme {
                // 显示电源键播报控制界面
                PowerKeyScreen(
                    // 启动服务的回调函数
                    onStartService = {
                        startPowerListenerService()
                    }
                )
            }
        }
    }

    /**
     * 注册屏幕状态广播接收器
     * 只监听 SCREEN_ON（亮屏）事件，避免屏幕超时误触发
     */
    private fun registerScreenReceiver() {
        // 创建 IntentFilter，指定要监听的广播类型
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)  // 只添加亮屏事件
        }
        // 注册接收器
        registerReceiver(screenReceiver, filter)
    }

    /**
     * Activity 销毁时调用
     * 必须取消注册广播接收器，否则会导致内存泄漏
     */
    override fun onDestroy() {
        super.onDestroy()
        // 取消注册广播接收器，释放资源
        unregisterReceiver(screenReceiver)
    }

    /**
     * 启动电源键播报服务
     * 根据系统版本选择启动方式（Android 8.0+ 必须用前台服务）
     */
    private fun startPowerListenerService() {
        // 创建启动 PowerListenerService 的 Intent
        val serviceIntent = Intent(this, PowerListenerService::class.java)
        
        // Android 8.0 (API 26) 及以上必须使用 startForegroundService
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)  // 启动前台服务（带通知）
        } else {
            startService(serviceIntent)              // 普通启动服务
        }
    }
}

/**
 * 电源键播报控制界面（Composable 函数）
 * 
 * Composable 函数是 Jetpack Compose 的核心，用于声明式地构建 UI
 * 
 * @param onStartService 启动服务的回调函数
 */
@Composable
fun PowerKeyScreen(onStartService: () -> Unit) {
    // ==================== 状态管理 ====================
    // remember：在重组时记住这个值，避免每次都重新创建
    // mutableStateOf：创建一个可变状态，值改变时会自动触发 UI 重组
    var isServiceRunning by remember { mutableStateOf(false) }  // 服务是否正在运行
    var statusText by remember { mutableStateOf("服务未启动") }  // 状态提示文本

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
                text = "电源键时间播报",
                style = MaterialTheme.typography.headlineMedium  // 使用主题定义的标题样式
            )
            Spacer(modifier = Modifier.height(8.dp))  // 间距：8dp

            // ==================== 使用说明卡片 ====================
            // Card：卡片组件，带有阴影和圆角
            Card(
                modifier = Modifier.padding(8.dp),  // 外边距
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)  // 阴影高度
            ) {
                Text(
                    text = "欢迎使用电源键时间播报\n\n功能说明（启动服务后）：\n• 按电源键亮屏时自动播报当前时间\n• 支持中文语音播报\n• 自动调节音量至最大\n\n使用前请确保：\n1. 已开启系统文字转语音(TTS)\n2. Android 13+需授予通知权限" ,
                    modifier = Modifier.padding(8.dp),  // 内边距
                    style = MaterialTheme.typography.bodyLarge  // 正文样式
                )
            }

            Spacer(modifier = Modifier.height(8.dp))  // 间距：8dp

            // ==================== 状态显示卡片 ====================
            Card(
                modifier = Modifier.padding(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Text(
                    text = statusText,  // 动态显示状态文本
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Spacer(modifier = Modifier.height(8.dp))  // 间距：8dp

            // ==================== 启动按钮 ====================
            Button(
                onClick = {
                    // 点击按钮时执行
                    onStartService()           // 调用启动服务回调
                    isServiceRunning = true    // 更新状态为运行中
                    statusText = "服务运行中"  // 更新提示文本
                },
                enabled = !isServiceRunning  // 服务运行时禁用按钮（防止重复启动）
            ) {
                // 根据状态显示不同的按钮文字
                Text(if (isServiceRunning) "服务已启动" else "启动服务")
            }
        }
    }
}
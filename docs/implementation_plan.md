# 局域网远程控制功能设计方案 (Remote Control via LAN)

## 1. 需求分析

**核心目标**：实现局域网内其他设备（中控机、手机、脚本等）通过网络协议控制抽奖程序的运行。

**具体需求**：

1. **远程开始**：发送指令控制转盘开始旋转。
2. **远程结束**：发送指令控制转盘停止，并获取本次中奖结果。
3. **状态反馈**：获取当前程序是否连接、是否正在抽奖等状态。

## 2. 技术选型

方案选择 **轻量级 HTTP Server (`NanoHTTPD`)** 嵌入到 Android 应用中。

* **理由**：
  * **通用性强**：HTTP 协议通用，任何设备（浏览器、Python、Postman、中控软件）都能轻松发送指令。
  * **集成方便**：`NanoHTTPD` 是 Android 上最常用的微型服务器库，体积小（几十KB），无复杂依赖。
  * **调试简单**：直接用浏览器或 Logcat 即可调试。

## 3. 架构设计

### 3.1 核心模块

* **`LocalServer`**: 继承自 `NanoHTTPD`，负责监听端口（默认 `8888`）和解析 HTTP 请求。
* **`MainActivity`**:
  * 作为服务器的宿主（或者通过 Service 托管，考虑生命周期，Demo 级别可在 Activity 中启停）。
  * 提供 `public void remoteStart()` 和 `public Prize remoteStop()` 方法供 Server 调用。
  * **线程安全**：网络请求在后台线程，操作 UI（转盘、弹窗）必须通过 `runOnUiThread` 切换回主线程。

### 3.2 通信协议设计 (RESTful API)

所有请求采用 **HTTP GET/POST**。

#### A. 检查状态

* **Endpoint**: `/api/status`
* **Method**: `GET`
* **Response (JSON)**:

    ```json
    {
        "status": "idle" | "running", // 当前转盘状态
        "device_ip": "192.168.1.100"
    }
    ```

#### B. 开始抽奖

* **Endpoint**: `/api/start`
* **Method**: `POST`
* **Action**: 触发转盘开始旋转。
* **Response (JSON)**:

    ```json
    {
        "success": true,
        "message": "Lottery started"
    }
    ```

#### C. 停止抽奖并获取结果

* **Endpoint**: `/api/stop`
* **Method**: `POST`
* **Action**: 触发停止逻辑，计算出结果，立即返回（无需等待动画结束）。
* **Response (JSON)**:

    ```json
    {
        "success": true,
        "prize_name": "一等奖",
        "prize_index": 3
    }
    ```

## 4. 详细实施步骤

### 4.1 引入依赖

在 `app/build.gradle` 中添加 `NanoHTTPD` 依赖。

### 4.2 实现 `LocalServer` 类

创建一个 Java 类处理网络请求的分发：

* 解析 URL 路径。
* 根据路径调用 `MainActivity` 的对应回调接口。
* 封装 JSON 响应返回。

### Voice Control Feature (Vosk Offline)

#### [MODIFY] [build.gradle](file:///d:/code/demo/VoiceLottery/VoiceLotteryApp/app/build.gradle)

- Add implementation 'com.alphacephei:vosk-android:0.3.50' (Using 0.3.50+ for specific fixes if needed, else 0.3.47)

#### [MODIFY] [AndroidManifest.xml](file:///d:/code/demo/VoiceLottery/VoiceLotteryApp/app/src/main/AndroidManifest.xml)

- Add `android.permission.RECORD_AUDIO`

#### [NEW] [VoiceManager.java](file:///d:/code/demo/VoiceLottery/VoiceLotteryApp/app/src/main/java/com/example/voicelotteryapp/manager/VoiceManager.java)

- **Model Handling**: Copy `model-cn` from assets to internal storage on first run.
* **Service**: Initialize `SpeechService` or `Recognizer`.
* **Logic**:
  * Keyword spotting: "开始" (Start), "停/停止" (Stop), "退出" (Exit).
  * Callback to `MainActivity` when keyword detected.

#### [MODIFY] [MainActivity.java](file:///d:/code/demo/VoiceLottery/VoiceLotteryApp/app/src/main/java/com/example/voicelotteryapp/MainActivity.java)

- Add Floating/Toolbar Mic Button.
* Handle Runtime Permission Request (`ActivityCompat.requestPermissions`).
* Integrate callbacks to `startLottery()` and `stopLotteryAndGetResult()`.

## Verification Plan.ACCESS_WIFI_STATE` (获取 IP 地址用)

* `android.permission.ACCESS_NETWORK_STATE`

## 6. 风险与规避

* **并发冲突**：如果用户在大屏上手动点击，同时网络又发请求怎么办？
  * *策略*：加锁或状态标志位 `isRunning`。如果正在运行，`start` 指令忽略；如果已停止，`stop` 指令忽略并返回错误提示。
* **网络延迟**：局域网内延迟通常忽略不计，但需处理超时。

---

**待确认项**：
是否需要支持修改端口号？（默认暂定 8888）

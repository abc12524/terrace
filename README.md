# Shell Tool Android

[shell-tool](../shell-tool) 的 Android 客户端：通过 HTTP API 连接命令行 AI 助手，
流式展示思考过程与工具调用；**对话历史仅保存在本机 SQLite**，方便在手机上回看。

## 功能

- **流式对话** — 消费 `POST /chat/stream` 的 SSE 语义事件，实时渲染思考（reasoning）与正文（Markdown）
- **工具调用可视化** — 每轮工具调用/结果聚合成「工具调用」卡片，点击查看完整参数与输出
- **本地历史** — 全部消息落本地 Room（SQLite），会话列表按「今天 / 昨天 / 本周 / 更早」分组，
  切换即秒开，**不向服务端读取历史**
- **极简设置** — 只需填写服务端 `Host` 与 `Port`，一键保存并测试连通性
- **新会话** — 新建对话时在服务端新开 session（等价 CLI 的 `-n`）

## 使用

1. 在运行 shell-tool 的机器上启动 HTTP 服务：

   ```bash
   cd shell-tool
   python server/api.py       # 监听 0.0.0.0:8000
   ```

2. 安装 APK 后打开应用，进入 **设置**，填写运行服务的机器 IP 与端口（如 `192.168.1.10` / `8000`）。
3. 返回对话页即可开始聊天。

> 手机与服务器需在同一局域网，且服务器防火墙放行对应端口。

## 升级 / 覆盖安装

支持直接覆盖安装（无需卸载），且本地对话历史会保留：

- **同一包名** `com.shelltool.android`；
- **同一签名**：debug 与 release 均复用仓库内固定的 `app/keystore.jks`，签名一致即可覆盖安装；
- **versionCode 递增**：由 CI 的 `GITHUB_RUN_NUMBER` 生成，每次构建自动增大，系统识别为升级。

只要不在应用内更换包名或签名密钥，后续版本都能一路覆盖升级。

## 技术栈

- **语言**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **数据库**: Room (SQLite)
- **网络**: OkHttp（SSE 流式）+ Gson
- **构建**: Gradle + GitHub Actions（自动构建 Debug APK 并发布 Release）

## 构建

本仓库不含 Gradle Wrapper jar 之外的构建产物，推荐直接用 GitHub Actions：

- push 到 `main`/`master` 或手动触发 `Build APK` workflow；
- 构建成功后可在 Actions 的 Artifacts 或 Releases 下载 `shell-tool-android-debug-*.apk`。

本地构建（需 JDK 17 + Android SDK）：

```bash
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

## 目录结构

```
app/src/main/java/com/shelltool/android/
├── ShellToolApp.kt              # Application：初始化配置与数据库
├── MainActivity.kt              # 单 Activity + Navigation（chat / settings）
├── data/
│   ├── AppPreferences.kt        # host / port
│   ├── HttpClientProvider.kt    # OkHttp 客户端（普通 / 流式）
│   ├── api/ShellToolClient.kt   # /chat/stream SSE 客户端 + /health
│   ├── db/                      # Room：AppDatabase / MessageDao / SessionDao
│   └── model/                   # Message / ChatSession
├── engine/ChatEngine.kt         # 事件流 → 本地消息映射与落库
└── ui/
    ├── chat/                    # ChatScreen / ChatViewModel / 工具卡 / 表格
    ├── settings/SettingsScreen.kt
    └── theme/                   # Compose 主题与设计令牌
```

## 事件映射

服务端 `/chat/stream` 只发送「变化的数据」，客户端自行组织展示：

| 事件 | 处理 |
|------|------|
| `content` / `reasoning` | 增量拼接，流式渲染为正文 / 深度思考 |
| `tool_call` / `tool_result` | 聚合成工具调用卡片并落库 |
| `continuing` | 工具轮边界，落一轮 assistant(tool_calls) + tool 结果 |
| `session` | 标记服务端会话已建立（后续消息不再发 `-n`） |
| `usage` | 累计 token / 费用 / 余额写入会话 |
| `error` | 提示错误 |

## License

MIT

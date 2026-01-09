# VideoPlayer 项目

一个基于 Android 的视频播放应用，支持视频上传、评论、点赞等社交功能，配合 Java 后端聊天服务器实现实时通讯。

## 项目结构

```
VideoPlayer/
├── app/                    # Android 应用主模块
│   └── src/
│       └── main/
├── ChatServer.java         # 简易聊天服务器
├── RobustChatServer.java   # 增强版聊天服务器（推荐使用）
└── build.gradle.kts        # Gradle 构建配置
```

## 环境要求

### Android 客户端
- **Android Studio**: Arctic Fox 或更高版本
- **JDK**: 11 或更高版本
- **Android SDK**:
  - compileSdk: 36
  - minSdk: 24
  - targetSdk: 36
- **Gradle**: 8.0+
- **Kotlin**: 支持 Compose

### 后端服务器
- **JDK**: 11 或更高版本
- **端口**: 8888（聊天服务器默认端口）

## 快速开始

### 1. 启动后端服务器

后端有两个版本，推荐使用增强版：

#### 使用增强版服务器（推荐）

```bash
# 编译
javac -encoding UTF-8 RobustChatServer.java

# 运行
java RobustChatServer
```

#### 使用简易版服务器

```bash
# 编译
javac -encoding UTF-8 ChatServer.java

# 运行
java ChatServer
```

服务器启动成功后会显示：
```
[服务器] 启动成功，端口：8888
[服务器] 等待客户端连接...
```

### 2. 配置客户端网络地址

在运行 Android 应用前，需要配置服务器地址：

1. 找到网络配置文件（通常在 `app/src/main/java/com/example/videoplayer/` 目录下）
2. 根据你的环境修改服务器地址：
   - **使用模拟器**: `10.0.2.2:8888`
   - **使用真机**: 电脑的局域网 IP + `:8888`（例如 `192.168.1.100:8888`）

#### 查看本机 IP 地址

**Windows:**
```bash
ipconfig
# 查找 "IPv4 地址" 或 "IPv4 Address"
```

**Linux/Mac:**
```bash
ifconfig
# 或
ip addr show
```

### 3. 运行 Android 应用

#### 方法一：使用 Android Studio（推荐）

1. 打开 Android Studio
2. 选择 `File` -> `Open` -> 选择项目根目录
3. 等待 Gradle 同步完成
4. 连接 Android 设备或启动模拟器
5. 点击 `Run` 按钮（绿色三角形）或按 `Shift + F10`

#### 方法二：使用命令行

```bash
# Windows
gradlew.bat :app:assembleDebug
gradlew.bat :app:installDebug

# Linux/Mac
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

### 4. 使用 ADB 端口转发（可选）

如果在模拟器上运行且遇到网络问题，可以使用 ADB 端口转发：

```bash
adb forward tcp:8888 tcp:8888
```

## 功能特性

### Android 客户端
- 视频播放（支持横竖屏切换）
- 视频上传
- 评论功能
- 点赞/取消点赞
- 实时聊天
- 用户认证

### 后端服务器
- 用户注册/登录
- 消息转发
- 离线消息存储
- 视频列表同步
- 多线程并发处理

## 构建说明

### 编译 Debug 版本
```bash
# Windows
gradlew.bat :app:assembleDebug

# Linux/Mac
./gradlew :app:assembleDebug
```

### 编译 Release 版本
```bash
# Windows
gradlew.bat :app:assembleRelease

# Linux/Mac
./gradlew :app:assembleRelease
```

### 清理构建
```bash
# Windows
gradlew.bat clean

# Linux/Mac
./gradlew clean
```

## 常见问题

### 1. 连接服务器失败

**检查项：**
- 确保后端服务器已启动
- 检查防火墙是否允许 8888 端口
- 验证客户端配置的 IP 地址是否正确
- 使用模拟器时确认使用 `10.0.2.2`

**测试服务器连接：**
```bash
# 编译测试程序
javac TestConnection.java

# 运行测试
java TestConnection
```

### 2. Gradle 同步失败

**解决方法：**
- 检查网络连接
- 清理 Gradle 缓存：`gradlew.bat clean`
- 删除 `.gradle` 文件夹后重新同步

### 3. 编译错误

**检查 JDK 版本：**
```bash
java -version
javac -version
```

确保使用 JDK 11 或更高版本。

### 4. 端口已被占用

**Windows 查找占用端口的进程：**
```bash
netstat -ano | findstr :8888
```

**Linux/Mac:**
```bash
lsof -i :8888
```

**停止占用的进程：**
```bash
# Windows
taskkill /PID <进程ID> /F

# Linux/Mac
kill -9 <进程ID>
```

## 开发说明

### 项目依赖
- AndroidX Core KTX
- Jetpack Compose
- Lifecycle Runtime
- ExoPlayer（视频播放）
- Room Database（数据持久化）
- Retrofit/OkHttp（网络请求）

### 架构
- MVVM 架构模式
- Jetpack Compose UI
- Kotlin Coroutines 异步处理
- Repository 模式数据层

## 版本历史

查看最近的提交记录：
```bash
git log --oneline -5
```

最新版本包含：
- 迭代15：视频服务器同步功能
- 修复表情框显示问题
- 优化视频上传后刷新逻辑

## 贡献指南

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

## 许可证

本项目仅供学习和研究使用。

## 联系方式

如有问题，请提交 Issue 或联系项目维护者。

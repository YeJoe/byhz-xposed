# BYHZ Xposed

基于 LSPosed 的 Android HTTP 请求改写模块，自动替换目标 App 的 `Authorization` 请求头并拦截指定上报 URL。

## 功能

- **Token 替换**：将目标 App 发出的 `Authorization` 头替换为指定 Token
- **URL 拦截**：对匹配的上报 URL 直接返回 HTTP 200，阻止真实请求
- **精准注入**：通过 LSPosed 作用域仅对指定 App 生效，不影响其他应用

## 工作原理

```
目标 App 发起 HTTP 请求
        │
        ▼
┌─── OkHttp Request.Builder.header() ────┐
├─── OkHttp Request.Builder.addHeader() ─┤  检测到 Authorization → 替换为新 Token
├─── HttpURLConnection.setRequestProperty() ┤
│                                         │
├─── OkHttp RealCall.execute() ──────────┤  匹配拦截 URL → 返回假 200
└─── OkHttp RealCall.enqueue() ──────────┘
```

共 5 个 Hook 点，覆盖 OkHttp3 同步/异步请求及传统 HttpURLConnection。

## 目录结构

```
├── .github/workflows/build.yml   # GitHub Actions 自动构建
├── app/
│   ├── build.gradle.kts          # 构建配置
│   ├── stub-src/                 # Xposed API 桩代码（编译用）
│   └── src/main/
│       ├── AndroidManifest.xml   # LSPosed 模块声明
│       ├── assets/xposed_init    # 入口类注册
│       └── java/com/byhz/xposed/
│           └── MainHook.java     # 核心 Hook 逻辑
├── build_stub.bat                # Windows 本地编译桩 jar
└── build_stub.sh                 # Linux/macOS 本地编译桩 jar
```

## 构建

### 自动构建 (GitHub Actions)

推送代码到 `main`/`master` 分支后自动触发，构建产物在 Actions → Artifacts 下载。

也可在 Actions 页面手动触发 `Build APK (arm64-v8a)`。

### 本地构建

1. 编译 Xposed API 桩 jar：

```bat
# Windows
build_stub.bat
```

```bash
# Linux / macOS
./build_stub.sh
```

2. 用 Android Studio 打开项目，Build → Build APK。

## 安装与使用

1. 安装 [LSPosed](https://github.com/LSPosed/LSPosed) 框架
2. 安装本模块 APK
3. 打开 LSPosed Manager → 模块 → 启用 BYHZ Token
4. 在作用域中**勾选目标 App**
5. 重启目标 App

## 配置

修改 `app/src/main/java/com/byhz/xposed/MainHook.java`：

```java
// 替换为你的 Token
private static final String NEW_TOKEN = "你的Token";

// 需要拦截的 URL 正则
private static final String BLOCK_URL_REGEX = ".*域名/路径.*";
```

修改后重新构建即可。

## 技术细节

- **依赖方式**：compileOnly + 桩代码 jar，不依赖远程 Maven 仓库
- **架构适配**：通过字段类型反射获取 OkHttp `RealCall` 中的 Request，兼容 OkHttp 3.x/4.x
- **构造假响应**：完全通过反射调用 OkHttp API，避免编译时类引用冲突
- **签名**：CI 自动生成 debug keystore 签名

## License

MIT

package com.byhz.xposed;

import android.util.Log;

import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Xposed 模块入口 — 在目标 App 进程内直接替换 API 请求的 Authorization header。
 *
 * 策略（按优先级）：
 *   1. Hook OkHttp3 Request.Builder.build() —— 加密前直接改 header（最优雅）
 *   2. Hook HttpURLConnection —— 兜底方案
 */
public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "BYHZ_Xposed";
    private static final String NEW_TOKEN = "bearer eb8fbd7caca26d2b8147e59340b78d60";

    // ---- 目标 App 包名（根据实际情况修改）----
    private static final String[] TARGET_PACKAGES = {
            "com.example.untitled"  // Flutter debug apk 默认包名
    };

    // ---- 需要替换 token 的 API 路径特征 ----
    private static final String[] API_PATTERNS = {
            "/api/video/report_item",
            "/api/live/room/detail",
            "/api/video/related",
            "/api/video/detail",
            "/api/socialposts_info",
            "/api/my/profile"
    };

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpp) throws Throwable {
        // 只注入目标 App
        if (!isTargetPackage(lpp.packageName)) return;

        log("=== Loaded: " + lpp.packageName + " ===");

        // 方案 1：OkHttp3（最优雅）
        if (hookOkHttp3(lpp.classLoader)) {
            log("[OK] OkHttp3 hooked");
            return;
        }

        // 方案 2：HttpURLConnection（通用兜底）
        if (hookHttpURLConnection()) {
            log("[OK] HttpURLConnection hooked");
            return;
        }

        log("[FAIL] No suitable HTTP client found");
    }

    // ==================== OkHttp3 Hook ====================

    private boolean hookOkHttp3(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(
                    "okhttp3.Request$Builder",
                    cl,
                    "build",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                Object builder = param.thisObject;
                                // 反射拿 url 字段（HttpUrl 类型，toString() 得到完整 URL）
                                Object urlObj = XposedHelpers.getObjectField(builder, "url");
                                if (urlObj == null) return;
                                String url = urlObj.toString();

                                if (isTargetUrl(url)) {
                                    log("[OkHttp] " + url);
                                    // 调用 builder.header("Authorization", token) 覆盖/添加
                                    XposedHelpers.callMethod(builder, "header",
                                            "Authorization", NEW_TOKEN);
                                    log("[OkHttp-Token] Replaced");
                                }
                            } catch (Throwable t) {
                                log("[OkHttp-Err] " + t.getMessage());
                            }
                        }
                    }
            );
            return true;
        } catch (Throwable t) {
            log("[OkHttp] Not found: " + t.getMessage());
            return false;
        }
    }

    // ==================== HttpURLConnection 兜底 ====================

    private boolean hookHttpURLConnection() {
        try {
            // 目标 App 的 HTTP 请求一般会经过 setRequestProperty 设置 header
            // 我们 hook connect()，在发送前修改 header
            // 实际上更好的做法是 hook setRequestProperty 或 connect

            // 方案 A: hook setRequestProperty 来拦截 Authorization 设置
            XposedHelpers.findAndHookMethod(
                    HttpURLConnection.class,
                    "setRequestProperty",
                    String.class, String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String key = (String) param.args[0];
                            if ("Authorization".equalsIgnoreCase(key)) {
                                log("[HttpURL-Property] Intercepting Authorization set");
                                param.args[1] = NEW_TOKEN;
                            }
                        }
                    }
            );

            // 方案 B: hook connect() 确保一定有 Authorization
            XposedHelpers.findAndHookMethod(
                    HttpURLConnection.class,
                    "connect",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                HttpURLConnection conn = (HttpURLConnection) param.thisObject;
                                URL u = conn.getURL();
                                if (u == null) return;
                                String url = u.toString();

                                if (isTargetUrl(url)) {
                                    log("[HttpURL] " + url);
                                    conn.setRequestProperty("Authorization", NEW_TOKEN);
                                    log("[HttpURL-Token] Replaced");
                                }
                            } catch (Throwable t) {
                                // ignore
                            }
                        }
                    }
            );
            return true;
        } catch (Throwable t) {
            log("[HttpURL] Fail: " + t.getMessage());
            return false;
        }
    }

    // ==================== 工具方法 ====================

    private boolean isTargetPackage(String packageName) {
        for (String pkg : TARGET_PACKAGES) {
            if (pkg.equals(packageName)) return true;
        }
        return false;
    }

    private boolean isTargetUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        String lower = url.toLowerCase();
        if (!lower.contains("/api/")) return false;
        for (String pattern : API_PATTERNS) {
            if (lower.contains(pattern)) return true;
        }
        return false;
    }

    private static void log(String msg) {
        Log.d(TAG, msg);
        XposedBridge.log("[BYHZ] " + msg);
    }
}

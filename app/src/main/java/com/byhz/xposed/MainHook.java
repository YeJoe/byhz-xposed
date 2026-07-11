package com.byhz.xposed;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Xposed 模块入口。
 * 代码层包名过滤 + LSPosed 管理器注入范围双重控制。
 * <p>
 * Hook V2TXLivePlayerImpl.startLivePlay 抓取参数和返回值。
 */
public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "LiveURLHook";

    /** 目标 App 包名集合 */
    private static final HashSet<String> TARGET_PACKAGES = new HashSet<>(Arrays.asList(
            "com.l95c2450d7.f68bb4e2d5",
            "com.jhtycjujslsz.kpkprhqkgmwcpwbkt",
            "top.bienvenido.saas.i18n"
    ));

    /** 广播 Action — 与 MainActivity 中的 receiver 一致 */
    static final String BROADCAST_ACTION = "com.byhz.xposed.LIVE_RESULT";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpp) throws Throwable {
        if (!TARGET_PACKAGES.contains(lpp.packageName)) return;

        log("=== Injected: " + lpp.packageName + " ===");
        hookV2TXLive(lpp.classLoader);
        hookA0b(lpp.classLoader);
    }

    // ==================== V2TXLivePlayerImpl.startLivePlay ====================

    private void hookV2TXLive(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.tencent.live2.impl.V2TXLivePlayerImpl",
                    cl,
                    "startLivePlay",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                String playUrl = (String) param.args[0];
                                Object result = param.getResult();
                                String resultStr = result == null ? "null" : result.toString();

                                log("[V2TXLive] url=" + playUrl + " ret=" + resultStr);
                                sendResult(playUrl, resultStr, "V2TXLive.startLivePlay");
                            } catch (Throwable t) {
                                log("[Live-Err] " + t.getMessage());
                            }
                        }
                    }
            );
            log("[OK] V2TXLivePlayerImpl.startLivePlay hooked (after)");
        } catch (Throwable t) {
            log("[Live-Fail] " + t.getClass().getName() + ": " + t.getMessage());
        }
    }

    // ==================== a0.b.F ====================

    private static volatile boolean a0bHooked = false;

    private void hookA0b(ClassLoader cl) {
        // 1) 沿父链找（常规/磁盘加载）
        ClassLoader target = findClassLoader(cl, "a0.b");
        if (target != null) {
            try {
                XposedHelpers.findAndHookMethod("a0.b", target, "F", String.class, a0bCallback());
                a0bHooked = true;
                log("[OK] a0.b.F hooked (直接, " + target + ")");
            } catch (Throwable t) {
                log("[A0b-Fail] " + t.getClass().getName() + ": " + t.getMessage());
            }
            return;
        }
        // 2) 内存/独立 ClassLoader 兜底：等它被加载时再 hook
        try {
            XposedHelpers.findAndHookMethod(ClassLoader.class, "loadClass",
                    String.class, boolean.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (a0bHooked) return;
                            if (!"a0.b".equals(param.args[0])) return;
                            Class<?> c = (Class<?>) param.getResult();
                            if (c == null) return;
                            try {
                                XposedBridge.hookAllMethods(c, "F", a0bCallback());
                                a0bHooked = true;
                                log("[OK] a0.b.F hooked (loadClass动态, " + c.getClassLoader() + ")");
                            } catch (Throwable t) {
                                log("[A0b-Dyn-Fail] " + t.getMessage());
                            }
                        }
                    });
            log("[OK] a0.b loadClass 动态钩子已挂载");
        } catch (Throwable t) {
            log("[A0b] loadClass 钩子失败: " + t.getMessage());
        }
    }

    private ClassLoader findClassLoader(ClassLoader start, String name) {
        ClassLoader cl = start;
        while (cl != null) {
            try { cl.loadClass(name); return cl; } catch (Throwable ignored) {}
            cl = cl.getParent();
        }
        return null;
    }

    private XC_MethodHook a0bCallback() {
        return new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    String playUrl = (String) param.args[0];
                    Object result = param.getResult();
                    String resultStr = result == null ? "null" : result.toString();
                    log("[A0b] url=" + playUrl + " ret=" + resultStr);
                    sendResult(playUrl, resultStr, "a0.b.F");
                } catch (Throwable t) {
                    log("[A0b-Err] " + t.getMessage());
                }
            }
        };
    }

    /** 通过广播将结果发给本模块的 Activity */
    private void sendResult(String playUrl, String returnValue, String source) {
        try {
            Context ctx = (Context) XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.ActivityThread", null),
                    "currentApplication"
            );
            if (ctx == null) {
                log("[Live] No context, skip broadcast");
                return;
            }

            String time = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(new Date());

            Intent intent = new Intent(BROADCAST_ACTION);
            intent.setPackage("com.byhz.xposed");
            intent.putExtra("time", time);
            intent.putExtra("playUrl", playUrl);
            intent.putExtra("returnValue", returnValue);
            intent.putExtra("source", source == null ? "" : source);
            ctx.sendBroadcast(intent);

            log("[Live] Broadcast sent (" + source + ")");
        } catch (Throwable t) {
            log("[Live-Broadcast-Err] " + t.getMessage());
        }
    }

    private static void log(String msg) {
        Log.d(TAG, msg);
        XposedBridge.log("[LiveURL] " + msg);
    }
}

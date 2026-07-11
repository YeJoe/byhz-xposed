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

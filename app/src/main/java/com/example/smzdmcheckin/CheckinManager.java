package com.example.smzdmcheckin;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.json.JSONObject;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class CheckinManager {

    private static final String TAG = MainHook.TAG;
    private static final String CHECKIN_URL = "https://user-api.smzdm.com/checkin/all_reward";
    private static final String NET_ENGINE = "Pg.g";       // 宿主网络引擎 (ZZNetCoroutine)
    private static final String NET_CALLBACK = "Pg.e";     // onSuccess/onFailure 回调接口
    private static final String STATE_SP = "xposed_checkin_state";
    private static final String KEY_LAST_DATE = "last_success_date";

    private static volatile boolean sInited = false;
    private static volatile boolean sChecking = false;
    private static Context sAppContext;
    private static ClassLoader sClassLoader;
    private static Config sConfig;
    private static final Handler sMainHandler = new Handler(Looper.getMainLooper());

    // ==================== 入口 ====================

    public static void onAppReady(Context ctx, ClassLoader cl) {
        if (sInited || ctx == null || cl == null) return;
        sInited = true;
        sAppContext = ctx;
        sClassLoader = cl;
        sConfig = Config.load();

        // 多进程保护：只在主进程执行
        String proc = currentProcessName();
        if (proc != null && proc.contains(":")) {
            XposedBridge.log(TAG + ": skip subprocess: " + proc);
            return;
        }
        XposedBridge.log(TAG + ": app ready, pkg=" + ctx.getPackageName());

        if (sConfig.observeMode) hookNetworkObservation();

        if (sConfig.enableAuto) {
            sMainHandler.postDelayed(CheckinManager::autoCheckinOnce,
                    sConfig.delaySeconds * 1000L);
        }

        if (sConfig.hookPageAuto) hookSignInPageAuto();
    }

    private static String currentProcessName() {
        try {
            return (String) Class.forName("android.app.ActivityThread")
                    .getMethod("currentProcessName").invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    // ==================== 观察模式：打印请求 ====================

    private static void hookNetworkObservation() {
        try {
            Class<?> engine = sClassLoader.loadClass(NET_ENGINE);
            Class<?> cbIf = sClassLoader.loadClass(NET_CALLBACK);
            // Pg.g.q(String method, String url, Map params, int timeout, Class type, Pg.e cb)
            XposedHelpers.findAndHookMethod(engine, "q",
                    String.class, String.class, java.util.Map.class,
                    int.class, Class.class, cbIf,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            XposedBridge.log(TAG + " [REQ] " + param.args[0]
                                    + " " + param.args[1]
                                    + " params=" + param.args[2]
                                    + " timeout=" + param.args[3]);
                        }
                    });
            XposedBridge.log(TAG + ": network observation hooked (Pg.g.q)");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": hook Pg.g.q failed（类名可能随版本变化）: " + t);
        }
    }

    // ==================== 签到页自动触发 ====================

    private static void hookSignInPageAuto() {
        try {
            Class<?> act = sClassLoader.loadClass(
                    "com.smzdm.client.android.user_center.signin.SignInActivity");
            XposedHelpers.findAndHookMethod(act, "onResume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    sMainHandler.postDelayed(() -> {
                        if (!isDoneToday()) {
                            XposedBridge.log(TAG + ": page auto trigger");
                            doCheckin("page");
                        }
                    }, 2000);
                }
            });
            XposedBridge.log(TAG + ": SignInActivity.onResume hooked");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": hook SignInActivity failed: " + t);
        }
    }

    // ==================== 每日一次守卫 ====================

    private static void autoCheckinOnce() {
        if (isDoneToday()) {
            XposedBridge.log(TAG + ": already done today, skip");
            return;
        }
        doCheckin("launch");
    }

    private static boolean isDoneToday() {
        try {
            String last = sAppContext
                    .getSharedPreferences(STATE_SP, Context.MODE_PRIVATE)
                    .getString(KEY_LAST_DATE, "");
            return today().equals(last);
        } catch (Throwable t) {
            return false;
        }
    }

    private static void markDone(String brief) {
        try {
            sAppContext.getSharedPreferences(STATE_SP, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_LAST_DATE, today())
                    .putString("last_result", brief)
                    .putLong("last_time", System.currentTimeMillis())
                    .apply();
        } catch (Throwable ignored) {
        }
    }

    private static String today() {
        return new SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(new Date());
    }

    // ==================== 核心签到 ====================

    /**
     * 复用宿主网络栈发起签到：
     * Pg.g.q("POST", CHECKIN_URL, null, 10000, String.class, Pg.e回调)
     * 公共参数（token/sign/f/v 等）由宿主 OkHttp 拦截器自动注入，无需逆向签名算法。
     */
    public static void doCheckin(final String source) {
        if (sChecking) {
            XposedBridge.log(TAG + ": checking in progress, skip");
            return;
        }
        sChecking = true;
        try {
            Class<?> engine = sClassLoader.loadClass(NET_ENGINE);
            Class<?> cbIf = sClassLoader.loadClass(NET_CALLBACK);
            Method q = engine.getDeclaredMethod("q",
                    String.class, String.class, java.util.Map.class,
                    int.class, Class.class, cbIf);

            Object callback = Proxy.newProxyInstance(sClassLoader,
                    new Class[]{cbIf}, (proxy, method, args) -> {
                        // Object 方法防递归
                        if (method.getDeclaringClass() == Object.class) {
                            switch (method.getName()) {
                                case "toString":
                                    return "SMZDM_Checkin_Callback";
                                case "hashCode":
                                    return System.identityHashCode(proxy);
                                case "equals":
                                    return proxy == (args != null && args.length > 0 ? args[0] : null);
                                default:
                                    return null;
                            }
                        }
                        if ("onSuccess".equals(method.getName())) {
                            handleResponse(args != null && args.length > 0 ? args[0] : null);
                            return null;
                        }
                        if ("onFailure".equals(method.getName())) {
                            int code = args != null && args.length > 0 && args[0] instanceof Integer
                                    ? (Integer) args[0] : -1;
                            String msg = args != null && args.length > 1
                                    ? String.valueOf(args[1]) : "";
                            XposedBridge.log(TAG + " [签到失败] code=" + code + " msg=" + msg);
                            toast("签到请求失败: " + msg);
                            sChecking = false;
                            return null;
                        }
                        return null;
                    });

            XposedBridge.log(TAG + " [签到发起] source=" + source);
            q.invoke(null, "POST", CHECKIN_URL, null, 10000, String.class, callback);
        } catch (Throwable t) {
            sChecking = false;
            XposedBridge.log(TAG + " [签到异常] " + t);
            XposedBridge.log(t);
        }
    }

    // ==================== 响应处理 ====================

    private static void handleResponse(Object resp) {
        sChecking = false;
        if (resp == null) {
            XposedBridge.log(TAG + " [响应为空]");
            return;
        }
        String json = String.valueOf(resp);
        XposedBridge.log(TAG + " [签到响应] " + json);

        try {
            JSONObject obj = new JSONObject(json);
            int errorCode = obj.optInt("error_code", -999);
            String errorMsg = obj.optString("error_msg", "");

            if (errorCode == 0) {
                JSONObject data = obj.optJSONObject("data");
                String brief = "签到成功";
                if (data != null) {
                    JSONObject normal = data.optJSONObject("normal_reward");
                    if (normal != null) {
                        brief = "签到成功 " + normal.optString("reward_desc", "");
                    }
                }
                markDone(brief);
                toast(brief);
            } else if (errorCode == 4) {
                // 4: 静默分支 —— 可能是已签到/无需处理
                if (errorMsg.contains("已签") || errorMsg.contains("重复")) {
                    markDone("今日已签到");
                    toast("今日已签到");
                } else {
                    toast("今日无需签到(" + errorCode + "): " + errorMsg);
                }
            } else if (errorMsg.contains("验证") || json.contains("geetest")
                    || errorMsg.contains("安全")) {
                // 风控验证码：透出给用户手动完成，不重试
                toast("签到触发验证码，请打开App手动完成");
                XposedBridge.log(TAG + " [风控] 疑似需要 GeeTest: " + errorMsg);
            } else {
                toast("签到失败(" + errorCode + "): " + errorMsg);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + " [解析异常] " + t);
        }
    }

    // ==================== 工具 ====================

    private static void toast(final String msg) {
        try {
            Runnable r = () -> {
                try {
                    Toast.makeText(sAppContext, "[自动签到] " + msg,
                            Toast.LENGTH_LONG).show();
                } catch (Throwable ignored) {
                }
            };
            if (Looper.myLooper() == Looper.getMainLooper()) {
                r.run();
            } else {
                sMainHandler.post(r);
            }
        } catch (Throwable ignored) {
        }
    }
}

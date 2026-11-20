package com.example.smzdmcheckin;

import android.app.Application;
import android.content.Context;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {

    public static final String TAG = "SMZDM_Checkin";
    public static final String TARGET_PKG = "com.smzdm.client.android";

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpp) {
        if (!TARGET_PKG.equals(lpp.packageName)) {
            return;
        }
        XposedBridge.log(TAG + ": module loaded, process=" + lpp.processName);

        // 入口1: Application.attach（标准模式）
        XposedHelpers.findAndHookMethod(Application.class, "attach",
                Context.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            Context app = (Context) param.thisObject;
                            CheckinManager.onAppReady(app, app.getClassLoader());
                        } catch (Throwable t) {
                            XposedBridge.log(TAG + ": init(attach) failed: " + t);
                        }
                    }
                });

        // 入口2: Instrumentation.callApplicationOnCreate（防宿主重写 attach 的兑底）
        try {
            XposedHelpers.findAndHookMethod("android.app.Instrumentation",
                    lpp.classLoader, "callApplicationOnCreate",
                    Application.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                Application app = (Application) param.args[0];
                                CheckinManager.onAppReady(app, app.getClassLoader());
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + ": init(instr) failed: " + t);
                            }
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": hook Instrumentation failed: " + t);
        }
    }
}

package com.example.smzdmcheckin;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class Config {
    private static final String MODULE_PKG = "com.example.smzdmcheckin";
    private static final String PREFS_NAME = "config";
    private static final String TAG = MainHook.TAG;

    public final boolean enableAuto;
    public final boolean observeMode;
    public final boolean hookPageAuto;
    public final int delaySeconds;

    private Config(boolean en, boolean ob, boolean page, int delay) {
        enableAuto = en;
        observeMode = ob;
        hookPageAuto = page;
        delaySeconds = delay;
    }

    public static Config load() {
        boolean en = true, ob = true, page = false;
        int delay = 15;
        try {
            XSharedPreferences sp = new XSharedPreferences(MODULE_PKG, PREFS_NAME);
            sp.reload();
            en = sp.getBoolean("enable_auto", true);
            ob = sp.getBoolean("observe_mode", true);
            page = sp.getBoolean("hook_page_auto", false);
            delay = sp.getInt("delay_seconds", 15);
            XposedBridge.log(TAG + ": config loaded en=" + en + " ob=" + ob
                    + " page=" + page + " delay=" + delay);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": load config failed, use defaults: " + t);
        }
        return new Config(en, ob, page, delay);
    }
}

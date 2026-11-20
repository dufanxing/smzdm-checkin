package com.example.smzdmcheckin;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final SharedPreferences sp = getSharedPreferences("config", MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (getResources().getDisplayMetrics().density * 16);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("SMZDM 自动签到");
        title.setTextSize(20);
        root.addView(title);

        final CheckBox ckAuto = new CheckBox(this);
        ckAuto.setText("启动后自动签到（每日一次）");
        ckAuto.setChecked(sp.getBoolean("enable_auto", true));
        root.addView(ckAuto);

        final CheckBox ckObserve = new CheckBox(this);
        ckObserve.setText("观察模式（打印所有API请求到日志）");
        ckObserve.setChecked(sp.getBoolean("observe_mode", true));
        root.addView(ckObserve);

        final CheckBox ckPage = new CheckBox(this);
        ckPage.setText("打开签到页时自动签到");
        ckPage.setChecked(sp.getBoolean("hook_page_auto", false));
        root.addView(ckPage);

        final EditText etDelay = new EditText(this);
        etDelay.setHint("启动延迟秒数，默认15");
        etDelay.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etDelay.setText(String.valueOf(sp.getInt("delay_seconds", 15)));
        root.addView(etDelay);

        TextView tip = new TextView(this);
        tip.setText("修改后需强停并重新打开什么值得买才生效。\n首次使用建议只开观察模式，手动签到一次验证日志。");
        tip.setPadding(0, pad, 0, 0);
        root.addView(tip);

        Button save = new Button(this);
        save.setText("保存");
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sp.edit()
                        .putBoolean("enable_auto", ckAuto.isChecked())
                        .putBoolean("observe_mode", ckObserve.isChecked())
                        .putBoolean("hook_page_auto", ckPage.isChecked())
                        .putInt("delay_seconds", parseSafe(etDelay, 15))
                        .apply();
                makePrefsReadable();
                Toast.makeText(SettingsActivity.this, "已保存", Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(save);

        setContentView(root);
    }

    /** 旧版 Xposed 环境需要 prefs 可读；LSPosed 下自动授予，失败无影响 */
    private void makePrefsReadable() {
        try {
            Class<?> xsp = Class.forName("de.robv.android.xposed.XSharedPreferences");
            Object prefs = xsp.getConstructor(String.class, String.class)
                    .newInstance("com.example.smzdmcheckin", "config");
            prefs.getClass().getMethod("makeWorldReadable").invoke(prefs);
        } catch (Throwable ignored) {
        }
    }

    private static int parseSafe(EditText et, int def) {
        try {
            return Integer.parseInt(et.getText().toString().trim());
        } catch (Exception e) {
            return def;
        }
    }
}

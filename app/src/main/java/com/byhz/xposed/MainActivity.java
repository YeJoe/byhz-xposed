package com.byhz.xposed;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * 实时显示 V2TXLivePlayerImpl.startLivePlay 的 hook 结果，支持长按选中复制和播放。
 */
public class MainActivity extends Activity {

    private TextView resultText;
    private TextView statusText;
    private final StringBuilder allResults = new StringBuilder();
    private int recordCount = 0;
    private final List<String> urlList = new ArrayList<>();
    private final List<String> labelList = new ArrayList<>();

    private final int BG_DARK = 0xFF0D1117;
    private final int BG_CARD = 0xFF161B22;
    private final int ACCENT = 0xFF58A6FF;
    private final int ACCENT_GREEN = 0xFF3FB950;
    private final int ACCENT_RED = 0xFFDA3633;
    private final int TEXT_PRIMARY = 0xFFE6EDF3;
    private final int TEXT_SECONDARY = 0xFF8B949E;
    private final int BORDER = 0xFF30363D;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String time = intent.getStringExtra("time");
            String playUrl = intent.getStringExtra("playUrl");
            String returnValue = intent.getStringExtra("returnValue");

            urlList.add(playUrl);
            labelList.add("#" + (recordCount + 1) + "  " + time);

            allResults.append("── #").append(++recordCount).append(" ──\n")
                    .append("Time : ").append(time).append("\n")
                    .append("URL  : ").append(playUrl).append("\n")
                    .append("Ret  : ").append(returnValue).append("\n\n");

            resultText.setText(allResults.toString().trim());
            statusText.setText("已捕获 " + recordCount + " 条记录");

            resultText.post(() -> {
                int lineCount = resultText.getLineCount();
                if (resultText.getLayout() != null && lineCount > 0) {
                    int scrollY = resultText.getLayout().getLineTop(lineCount) - resultText.getHeight();
                    if (scrollY > 0) resultText.scrollTo(0, scrollY);
                }
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(createLayout());

        registerReceiver(receiver,
                new IntentFilter(MainHook.BROADCAST_ACTION),
                RECEIVER_EXPORTED);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(receiver); } catch (Exception ignored) {}
    }

    // ---- 圆角背景工具 ----

    private GradientDrawable roundedBg(int color, float radius, int borderColor) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(dp(radius));
        d.setColor(color);
        if (borderColor != 0) {
            d.setStroke(dp(1), borderColor);
        }
        return d;
    }

    private void styleBtn(Button btn, int bgColor, String text) {
        btn.setText(text);
        btn.setTextColor(0xFFFFFFFF);
        btn.setTextSize(14);
        btn.setTypeface(null, Typeface.BOLD);
        btn.setBackground(roundedBg(bgColor, 8, 0));
        btn.setPadding(dp(20), dp(10), dp(20), dp(10));
        btn.setAllCaps(false);
        btn.setGravity(Gravity.CENTER);
        btn.setStateListAnimator(null);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        lp.setMargins(dp(4), 0, dp(4), 0);
        lp.weight = 1;
        btn.setLayoutParams(lp);
    }

    private int dp(float dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    // ---- 布局 ----

    private View createLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(24), dp(16), dp(24));
        root.setBackgroundColor(BG_DARK);

        // 标题栏
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(8));

        TextView dot = new TextView(this);
        dot.setText("● ");
        dot.setTextColor(ACCENT_GREEN);
        dot.setTextSize(20);
        header.addView(dot);

        TextView title = new TextView(this);
        title.setText("LiveURL 抓取");
        title.setTextColor(TEXT_PRIMARY);
        title.setTextSize(22);
        title.setTypeface(null, Typeface.BOLD);
        header.addView(title);

        root.addView(header);

        // 分割线
        View divider1 = new View(this);
        divider1.setBackgroundColor(BORDER);
        divider1.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        root.addView(divider1);

        // 状态栏
        statusText = new TextView(this);
        statusText.setText("等待 hook 触发...\n打开目标 App 并进入直播间");
        statusText.setTextColor(TEXT_SECONDARY);
        statusText.setTextSize(13);
        statusText.setPadding(0, dp(12), 0, dp(12));
        root.addView(statusText);

        // 结果卡片容器
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundedBg(BG_CARD, 12, BORDER));
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        clp.setMargins(0, 0, 0, dp(16));
        card.setLayoutParams(clp);

        resultText = new TextView(this);
        resultText.setTextColor(TEXT_PRIMARY);
        resultText.setTextSize(12);
        resultText.setTypeface(Typeface.MONOSPACE);
        resultText.setPadding(0, dp(4), 0, dp(4));
        resultText.setMovementMethod(new ScrollingMovementMethod());
        resultText.setTextIsSelectable(true);        // 关键：允许长按选中复制
        resultText.setHighlightColor(0x3358A6FF);    // 选中高亮色
        resultText.setText("暂无数据");
        resultText.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        card.addView(resultText);

        root.addView(card);

        // 按钮栏
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);

        Button copyBtn = new Button(this);
        styleBtn(copyBtn, ACCENT, "复制全部");
        copyBtn.setOnClickListener(v -> {
            String data = allResults.toString().trim();
            if (data.isEmpty()) {
                Toast.makeText(this, "暂无数据", Toast.LENGTH_SHORT).show();
                return;
            }
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("live_url_hook", data));
            Toast.makeText(this, "已复制 " + recordCount + " 条记录", Toast.LENGTH_SHORT).show();
        });
        btnRow.addView(copyBtn);

        Button playBtn = new Button(this);
        styleBtn(playBtn, ACCENT_GREEN, "播放");
        playBtn.setOnClickListener(v -> {
            if (urlList.isEmpty()) {
                Toast.makeText(this, "暂无 URL", Toast.LENGTH_SHORT).show();
                return;
            }
            String[] labels = labelList.toArray(new String[0]);
            new AlertDialog.Builder(this)
                    .setTitle("选择要播放的链接")
                    .setItems(labels, (dialog, which) -> {
                        try {
                            Intent playIntent = new Intent(Intent.ACTION_VIEW);
                            playIntent.setDataAndType(Uri.parse(urlList.get(which)), "video/*");
                            startActivity(Intent.createChooser(playIntent, "选择播放器"));
                        } catch (Exception e) {
                            Toast.makeText(this, "无法播放: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    })
                    .show();
        });
        btnRow.addView(playBtn);

        Button clearBtn = new Button(this);
        styleBtn(clearBtn, ACCENT_RED, "清空");
        clearBtn.setOnClickListener(v -> {
            allResults.setLength(0);
            urlList.clear();
            labelList.clear();
            recordCount = 0;
            resultText.setText("暂无数据");
            statusText.setText("等待 hook 触发...");
        });
        btnRow.addView(clearBtn);

        root.addView(btnRow);

        return root;
    }
}

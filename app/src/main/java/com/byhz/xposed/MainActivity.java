package com.byhz.xposed;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * 实时显示 hook 结果。每条记录以独立卡片展示，卡片右上角带复制 / 播放图标，
 * 点击复制图标直接复制该条 URL，点击播放图标选择播放器播放该 URL；
 * 卡片上同时标明是哪条 hook 产生的。
 */
public class MainActivity extends Activity {

    private ScrollView scrollView;
    private LinearLayout logContainer;
    private TextView emptyHint;
    private TextView statusText;

    private int recordCount = 0;
    private final List<String> urlList = new ArrayList<>();

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
            String source = intent.getStringExtra("source");

            recordCount++;
            urlList.add(playUrl);

            addLogCard(time, playUrl, returnValue, source, recordCount);
            statusText.setText("已捕获 " + recordCount + " 条记录");
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

    private int dp(float dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    // ---- 图标（程序化生成 VectorDrawable）----

    private Drawable vectorIcon(int color, String pathData) {
        String hex = String.format("%08X", color);
        String xml = "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" "
                + "android:width=\"24dp\" android:height=\"24dp\" "
                + "android:viewportWidth=\"24\" android:viewportHeight=\"24\">"
                + "<path android:fillColor=\"#" + hex + "\" android:pathData=\"" + pathData + "\"/>"
                + "</vector>";
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(new StringReader(xml));
            return Drawable.createFromXml(getResources(), parser);
        } catch (Exception e) {
            return null;
        }
    }

    private Drawable copyIcon() {
        // 两张重叠的卡片，表示「复制」
        return vectorIcon(ACCENT,
                "M16 1H4a2 2 0 0 0-2 2v14h2V3h12V1zm3 4H8a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h11"
                + "a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2zm0 16H8V7h11v14z");
    }

    private Drawable playIcon() {
        // 播放三角
        return vectorIcon(ACCENT_GREEN, "M8 5v14l11-7z");
    }

    private ImageView makeIcon(Drawable drawable, String desc, View.OnClickListener onClick) {
        ImageView iv = new ImageView(this);
        if (drawable != null) {
            iv.setImageDrawable(drawable);
        }
        iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iv.setPadding(dp(6), dp(6), dp(6), dp(6));
        iv.setClickable(true);
        iv.setFocusable(true);
        iv.setContentDescription(desc);
        iv.setBackground(roundedBg(0x22000000, 8, 0));
        iv.setOnClickListener(onClick);
        return iv;
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

        // 滚动容器 + 卡片列表
        scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        slp.setMargins(0, 0, 0, dp(16));
        scrollView.setLayoutParams(slp);

        logContainer = new LinearLayout(this);
        logContainer.setOrientation(LinearLayout.VERTICAL);
        logContainer.setPadding(0, dp(4), 0, dp(4));
        scrollView.addView(logContainer);

        // 空状态提示
        emptyHint = new TextView(this);
        emptyHint.setText("暂无数据");
        emptyHint.setTextColor(TEXT_SECONDARY);
        emptyHint.setTextSize(13);
        emptyHint.setGravity(Gravity.CENTER);
        emptyHint.setPadding(0, dp(24), 0, dp(24));
        logContainer.addView(emptyHint);

        root.addView(scrollView);

        // 仅保留「清空」按钮
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);

        Button clearBtn = new Button(this);
        clearBtn.setText("清空");
        clearBtn.setTextColor(0xFFFFFFFF);
        clearBtn.setTextSize(14);
        clearBtn.setTypeface(null, Typeface.BOLD);
        clearBtn.setBackground(roundedBg(ACCENT_RED, 8, 0));
        clearBtn.setPadding(dp(20), dp(10), dp(20), dp(10));
        clearBtn.setAllCaps(false);
        clearBtn.setGravity(Gravity.CENTER);
        clearBtn.setStateListAnimator(null);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        clp.setMargins(dp(4), 0, dp(4), 0);
        clearBtn.setLayoutParams(clp);
        clearBtn.setOnClickListener(v -> {
            urlList.clear();
            recordCount = 0;
            logContainer.removeAllViews();
            logContainer.addView(emptyHint);
            emptyHint.setVisibility(View.VISIBLE);
            statusText.setText("等待 hook 触发...");
        });
        btnRow.addView(clearBtn);

        root.addView(btnRow);

        return root;
    }

    // ---- 添加一条记录卡片 ----

    private void addLogCard(String time, String playUrl, String ret, String source, int index) {
        if (emptyHint.getParent() != null) {
            logContainer.removeView(emptyHint);
        }

        final int pos = index - 1;

        // 卡片
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundedBg(BG_CARD, 12, BORDER));
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams cLP = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cLP.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(cLP);

        // 顶部行：标签 + 播放图标 + 复制图标
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView tag = new TextView(this);
        String srcLabel = (source == null || source.isEmpty()) ? "" : "  ·  " + source;
        tag.setText("#" + index + "  " + time + srcLabel);
        tag.setTextColor(TEXT_SECONDARY);
        tag.setTextSize(12);
        top.addView(tag, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        ImageView playIv = makeIcon(playIcon(), "播放", v -> playUrl(pos));
        top.addView(playIv, new LinearLayout.LayoutParams(dp(36), dp(36)));

        ImageView copyIv = makeIcon(copyIcon(), "复制 URL", v -> copyUrl(pos));
        top.addView(copyIv, new LinearLayout.LayoutParams(dp(36), dp(36)));

        card.addView(top);

        // URL
        TextView urlView = new TextView(this);
        urlView.setText(playUrl);
        urlView.setTextColor(TEXT_PRIMARY);
        urlView.setTextSize(13);
        urlView.setTypeface(Typeface.MONOSPACE);
        urlView.setTextIsSelectable(true);
        urlView.setHighlightColor(0x3358A6FF);
        LinearLayout.LayoutParams urlLP = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        urlLP.setMargins(0, dp(8), 0, 0);
        urlView.setLayoutParams(urlLP);
        card.addView(urlView);

        // 返回值
        if (ret != null && !ret.isEmpty()) {
            TextView retView = new TextView(this);
            retView.setText("Ret: " + ret);
            retView.setTextColor(TEXT_SECONDARY);
            retView.setTextSize(11);
            retView.setTypeface(Typeface.MONOSPACE);
            LinearLayout.LayoutParams retLP = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            retLP.setMargins(0, dp(4), 0, 0);
            retView.setLayoutParams(retLP);
            card.addView(retView);
        }

        logContainer.addView(card);
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }

    private void copyUrl(int index) {
        if (index < 0 || index >= urlList.size()) return;
        String url = urlList.get(index);
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("live_url", url));
        Toast.makeText(this, "已复制 URL", Toast.LENGTH_SHORT).show();
    }

    private void playUrl(int index) {
        if (index < 0 || index >= urlList.size()) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(urlList.get(index)), "video/*");
            startActivity(Intent.createChooser(intent, "选择播放器"));
        } catch (Exception e) {
            Toast.makeText(this, "无法播放: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}

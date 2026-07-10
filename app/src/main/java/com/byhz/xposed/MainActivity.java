package com.byhz.xposed;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 实时显示 V2TXLivePlayerImpl.startLivePlay 的 hook 结果，支持一键复制。
 */
public class MainActivity extends Activity {

    private TextView resultText;
    private TextView statusText;
    private final StringBuilder allResults = new StringBuilder();
    private int recordCount = 0;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String time = intent.getStringExtra("time");
            String playUrl = intent.getStringExtra("playUrl");
            String returnValue = intent.getStringExtra("returnValue");

            allResults.append("── #").append(++recordCount).append(" ──\n")
                    .append("Time : ").append(time).append("\n")
                    .append("URL  : ").append(playUrl).append("\n")
                    .append("Ret  : ").append(returnValue).append("\n\n");

            resultText.setText(allResults.toString().trim());
            statusText.setText("已捕获 " + recordCount + " 条记录");

            // 自动滚动到底部
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

        // 注册广播接收器
        registerReceiver(receiver,
                new IntentFilter(MainHook.BROADCAST_ACTION),
                RECEIVER_EXPORTED);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(receiver); } catch (Exception ignored) {}
    }

    private View createLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);
        root.setBackgroundColor(0xFF1E1E1E);

        TextView title = new TextView(this);
        title.setText("V2TXLive Hook 结果");
        title.setTextColor(0xFF00E676);
        title.setTextSize(22);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, 16, 0, 16);
        root.addView(title);

        statusText = new TextView(this);
        statusText.setText("等待 hook 触发...\n打开目标 App 并触发 startLivePlay");
        statusText.setTextColor(0xFF888888);
        statusText.setTextSize(13);
        statusText.setPadding(0, 0, 0, 12);
        root.addView(statusText);

        resultText = new TextView(this);
        resultText.setTextColor(0xFFFFFFFF);
        resultText.setTextSize(13);
        resultText.setTypeface(Typeface.MONOSPACE);
        resultText.setBackgroundColor(0xFF2D2D2D);
        resultText.setPadding(24, 24, 24, 24);
        resultText.setMovementMethod(new ScrollingMovementMethod());
        resultText.setText("暂无数据");
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        rlp.setMargins(0, 0, 0, 24);
        resultText.setLayoutParams(rlp);
        root.addView(resultText);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);

        Button copyBtn = new Button(this);
        copyBtn.setText("复制全部");
        copyBtn.setTextColor(0xFFFFFFFF);
        copyBtn.setBackgroundColor(0xFF1565C0);
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

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(24, 0));
        btnRow.addView(spacer);

        Button clearBtn = new Button(this);
        clearBtn.setText("清空");
        clearBtn.setTextColor(0xFFFFFFFF);
        clearBtn.setBackgroundColor(0xFFC62828);
        clearBtn.setOnClickListener(v -> {
            allResults.setLength(0);
            recordCount = 0;
            resultText.setText("暂无数据");
            statusText.setText("等待 hook 触发...");
        });
        btnRow.addView(clearBtn);

        root.addView(btnRow);
        return root;
    }
}

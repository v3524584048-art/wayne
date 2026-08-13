package com.wayne.okxsim;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 一个只干一件事的外壳：用系统 WebView 打开你的网页，
 * 并把状态栏 / 导航栏强制设成白底深色图标（浏览器快捷方式做不到的那一点）。
 *
 * 网址不写死在代码里：第一次打开会弹框让你粘贴，之后记住。
 * 打不开时也会弹出来让你改，所以网址填错了不用重新打包。
 * 想彻底换网址：手机设置 → 应用 → 合约 → 存储 → 清除数据，再打开就会重新问。
 */
public class MainActivity extends Activity {

    private static final String PREF = "cfg";
    private static final String KEY_URL = "url";

    private WebView web;
    private LinearLayout errBox;
    private TextView errText;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 状态栏 / 导航栏：纯白底 + 深色图标
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        View decor = getWindow().getDecorView();
        int flags = decor.getSystemUiVisibility();
        flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        decor.setSystemUiVisibility(flags);

        FrameLayout root = new FrameLayout(this);

        web = new WebView(this);
        web.setBackgroundColor(Color.WHITE);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);      // 页面用 localStorage 存仓位/设置
        s.setDatabaseEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);                // 不跟随系统字体大小
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView v, String url, Bitmap favicon) {
                hideError();
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                if (req != null && req.isForMainFrame()) {
                    showError("连不上这个网址：\n" + req.getUrl() + "\n\n（网络不通，或者地址写错了）");
                }
            }

            @Override
            public void onReceivedHttpError(WebView v, WebResourceRequest req, WebResourceResponse res) {
                if (req != null && req.isForMainFrame() && res != null && res.getStatusCode() >= 400) {
                    showError("这个网址不存在（服务器返回 " + res.getStatusCode() + "）：\n" + req.getUrl()
                            + "\n\n多半是地址少了一截。到浏览器里打开你那个网站，把地址栏里的完整地址复制过来。");
                }
            }
        });

        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(buildErrorView(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        errBox.setVisibility(View.GONE);

        setContentView(root);

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
            return;
        }

        String url = savedUrl();
        if (url.length() == 0) {
            askUrl();
        } else {
            web.loadUrl(url);
        }
    }

    // ---------- 网址存取 ----------

    private String savedUrl() {
        SharedPreferences p = getSharedPreferences(PREF, Context.MODE_PRIVATE);
        return p.getString(KEY_URL, "");
    }

    private void saveUrl(String url) {
        getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY_URL, url).apply();
    }

    private void askUrl() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        input.setSingleLine(true);
        String cur = savedUrl();
        input.setText(cur.length() == 0 ? "https://" : cur);
        input.setSelection(input.getText().length());

        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(dp(20), dp(8), dp(20), 0);
        wrap.addView(input, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle("输入网址")
                .setMessage("粘贴你那个网站的完整地址，例如 https://xxxx-xxxx-074079.netlify.app/")
                .setView(wrap)
                .setCancelable(false)
                .setPositiveButton("打开", (d, which) -> {
                    String u = input.getText().toString().trim();
                    if (u.length() == 0) { askUrl(); return; }
                    if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://" + u;
                    saveUrl(u);
                    hideError();
                    web.loadUrl(u);
                })
                .setNegativeButton("取消", (d, which) -> {
                    if (savedUrl().length() == 0) {
                        showError("还没设置网址。\n点下面的「改网址」，把你网站的完整地址粘贴进去。");
                    }
                })
                .show();
    }

    // ---------- 出错时挡在页面上的那一层 ----------

    private View buildErrorView() {
        errBox = new LinearLayout(this);
        errBox.setOrientation(LinearLayout.VERTICAL);
        errBox.setGravity(Gravity.CENTER);
        errBox.setBackgroundColor(Color.WHITE);
        errBox.setPadding(dp(28), dp(28), dp(28), dp(28));

        errText = new TextView(this);
        errText.setTextColor(Color.parseColor("#333333"));
        errText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        errText.setGravity(Gravity.CENTER);
        errBox.addView(errText);

        Button b1 = new Button(this);
        b1.setText("改网址");
        b1.setOnClickListener(v -> askUrl());
        errBox.addView(b1);

        Button b2 = new Button(this);
        b2.setText("重试");
        b2.setOnClickListener(v -> {
            String u = savedUrl();
            if (u.length() == 0) {
                askUrl();
            } else {
                hideError();
                web.loadUrl(u);
            }
        });
        errBox.addView(b2);

        return errBox;
    }

    private void showError(String msg) {
        errText.setText(msg);
        errBox.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        errBox.setVisibility(View.GONE);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    // ---------- 其它 ----------

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    public void onBackPressed() {
        if (errBox.getVisibility() != View.VISIBLE && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}

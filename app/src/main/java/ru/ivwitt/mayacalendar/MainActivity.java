package ru.ivwitt.mayacalendar;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final int CREATE_JSON_REQUEST = 1002;
    private static final int OPEN_JSON_REQUEST = 1003;

    private WebView webView;
    private FrameLayout rootView;
    private ImageView splashView;
    private boolean splashDismissed = false;
    private ValueCallback<Uri[]> fileChooserCallback;
    private String pendingJson;
    private String pendingJsonFilename = "праздники_календаря.json";

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(35, 68, 63));
        window.setNavigationBarColor(Color.rgb(35, 68, 63));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(true);
        }

        rootView = new FrameLayout(this);
        webView = new WebView(this);
        rootView.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        FrameLayout splashLayer = new FrameLayout(this);
        splashLayer.setBackgroundColor(Color.rgb(31, 45, 38));

        splashView = new ImageView(this);
        splashView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        splashView.setAdjustViewBounds(false);
        splashView.setImageResource(R.drawable.splash_maya);
        splashLayer.addView(splashView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        TextView splashCaption = new TextView(this);
        splashCaption.setText("Календарь Майя v1.2");
        splashCaption.setTextColor(Color.WHITE);
        splashCaption.setTextSize(20);
        splashCaption.setGravity(Gravity.CENTER);
        splashCaption.setShadowLayer(5f, 0f, 2f, Color.BLACK);
        splashCaption.setPadding(dp(18), dp(9), dp(18), dp(9));
        splashCaption.setBackgroundColor(Color.argb(125, 17, 25, 20));
        FrameLayout.LayoutParams captionParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL
        );
        captionParams.bottomMargin = dp(28);
        splashLayer.addView(splashCaption, captionParams);

        rootView.addView(splashLayer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        setContentView(rootView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        webView.setVerticalScrollBarEnabled(true);
        webView.setHorizontalScrollBarEnabled(false);
        webView.addJavaScriptInterface(new AndroidBridge(this), "AndroidBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                dismissSplashWhenReady();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileChooserCallback != null) fileChooserCallback.onReceiveValue(null);
                fileChooserCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), FILE_CHOOSER_REQUEST);
                } catch (ActivityNotFoundException e) {
                    fileChooserCallback = null;
                    Toast.makeText(MainActivity.this, "Не найден системный выбор файла", Toast.LENGTH_LONG).show();
                }
                return true;
            }
        });

        if (savedInstanceState == null) {
            webView.loadUrl("file:///android_asset/index.html");
        } else {
            webView.restoreState(savedInstanceState);
          dismissSplashWhenReady();
        }
    }

    private void dismissSplashWhenReady() {
        if (splashDismissed || splashView == null) return;
        splashDismissed = true;
        final android.view.View splashLayer = (android.view.View) splashView.getParent();
        splashView.postDelayed(() -> splashLayer.animate()
                .alpha(0f)
                .setDuration(350)
                .withEndAction(() -> {
                    if (splashLayer.getParent() == rootView) rootView.removeView(splashLayer);
                    splashView = null;
                }).start(), 900);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (fileChooserCallback != null) {
                Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
                fileChooserCallback.onReceiveValue(result);
                fileChooserCallback = null;
            }
            return;
        }

        if (requestCode == OPEN_JSON_REQUEST) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                importEventsFromUri(data.getData());
            }
            return;
        }

        if (requestCode == CREATE_JSON_REQUEST) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null && pendingJson != null) {
                try (OutputStream out = getContentResolver().openOutputStream(data.getData(), "w")) {
                    if (out == null) throw new IllegalStateException("Не удалось открыть файл");
                    out.write(pendingJson.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    Toast.makeText(this, "События сохранены", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Ошибка сохранения: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
            pendingJson = null;
        }
    }

    private void importEventsFromUri(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IllegalStateException("Не удалось открыть файл");
            byte[] bytes = readAllBytes(in);
            String json = new String(bytes, StandardCharsets.UTF_8);
            deliverImportedJsonToWebView(json);
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось загрузить события: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private byte[] readAllBytes(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    private void deliverImportedJsonToWebView(String json) {
        if (webView == null) return;
        String quoted;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            quoted = android.webkit.Json.quote(json);
        } else {
            quoted = "\"" + json.replace("\\\", "\\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\"";
        }
        webView.post(() -> {
            webView.evaluateJavascript("if(window.receiveImportedEventsJson){window.receiveImportedEventsJson(" + quoted + ");}void 0;", null);
        });
    }

    private void exportJson(String json, String filename) {
        final String safeFilename = (filename == null || filename.trim().isEmpty()) ? "maya_calendar_events.json" : filename;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Uri uri = null;
            try {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, safeFilename);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MayaCalendar");
                values.put(MediaStore.MediaColumns.IS_PENDING, 1);
                uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new IllegalStateException("Не удалось создать файл");
                try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
                    if (out == null) throw new IllegalStateException("Не удалось открыть файл");
                    out.write(json.getBytes(StandardCharsets.UTF_8));
                }
                ContentValues ready = new ContentValues();
                ready.put(MediaStore.MediaColumns.IS_PENDING, 0);
                getContentResolver().update(uri, ready, null, null);
                Toast.makeText(this, "События сохранены: Загрузки/MayaCalendar/" + safeFilename, Toast.LENGTH_LONG).show();
                return;
            } catch (Exception e) {
                if (uri != null) try { getContentResolver().delete(uri, null, null); } catch (Exception ignored) {}
            }
        }
        startJsonExportPicker(json, safeFilename);
    }

    private void startJsonExportPicker(String json, String filename) {
        pendingJson = json;
        pendingJsonFilename = filename;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, filename);
        try { startActivityForResult(intent, CREATE_JSON_REQUEST); }
        catch (ActivityNotFoundException e) {
            pendingJson = null;
            Toast.makeText(this, "Не найдено приложения для сохранения файла", Toast.LENGTH_LONG).show();
        }
    }

    private void openEventsJson() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, n new String[]{"application/json", "text/json", "text/plain"});
        try {
            startActivityForResult(intent, OPEN_JSON_REQUEST);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Не найдено приложения для открытия JSON", Toast.LENGTH_LONG).show();
        }
    }

    private void printCurrentPage() {
        PrintManager printManager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
        String jobName = getString(R.string.app_name) + " — календарь";
        printManager.print(jobName, webView.createPrintDocumentAdapter(jobName), new PrintAttributes.Builder().build());
    }

    public final class AndroidBridge {
        private final Activity activity;
      AndroidBridge(Activity activity) { this.activity = activity; }

        @JavaScriptInterface
        public void saveEventsJson(String json, String filename) {
            activity.runOnUiThread(() -> MainActivity.this.startJsonExportPicker(json, (filename == null || filename.trim().isEmpty()) ? "maya_calendar_events.json" : filename));
        }

        JavaScriptInterface
        public void exportJson(String json, String filename) { activity.runOnUiThread(() -> MainActivity.this.exportJson(json, filename)); }

        @JavaScriptInterface
        public void openEventsJson() { activity.runOnUiThread(MainActivity.this::openEventsJson); }

        JavaScriptInterface
        public void printPage() { activity.runOnUiThread(MainActivity.this::printCurrentPage); }
    }
}

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
import android.provider.MediaStore;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final int CREATE_JSON_REQUEST = 1002;
    private static final int OPEN_JSON_REQUEST = 1003;

    private WebView webView;
    private ImageView splashView;
    private FrameLayout rootView;
    private boolean splashDismissed = false;
    private ValueCallback<Uri[]> fileChooserCallback;
    private String pendingJson;
    private String pendingJsonFilename = "maya_calendar_events.json";

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface", "ObsoleteSdkInt"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.rgb(35, 68, 63));
        getWindow().setNavigationBarColor(Color.rgb(35, 68, 63));

        rootView = new FrameLayout(this);
        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(243, 239, 230));
        rootView.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        splashView = new ImageView(this);
        splashView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        splashView.setImageResource(R.drawable.splash_maya);
        splashView.setBackgroundColor(Color.rgb(31, 45, 38));
        rootView.addView(splashView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        setContentView(rootView);

        // Android 15 enforces edge-to-edge for targetSdk 35.
        // Insets are applied to the WebView so the app bar stays below the phone status area.
        webView.setOnApplyWindowInsetsListener((view, insets) -> {
            int top = insets.getSystemWindowInsetTop();
            int bottom = insets.getSystemWindowInsetBottom();
            view.setPadding(0, top, 0, bottom);
            return insets;
        });
        webView.requestApplyInsets();

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
        webView.addJavascriptInterface(new AndroidBridge(this), "AndroidBridge");

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
                if (fileChooserCallback != null) {
                    fileChooserCallback.onReceiveValue(null);
                }
                fileChooserCallback = callback;
                Intent intent;
                try {
                    intent = params.createIntent();
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
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
        splashView.postDelayed(() -> splashView.animate()
                .alpha(0f)
                .setDuration(350)
                .withEndAction(() -> {
                    if (splashView != null && splashView.getParent() == rootView) {
                        rootView.removeView(splashView);
                    }
                    splashView = null;
                }).start(), 900);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
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
                readAndImportJson(data.getData());
            }
            return;
        }

        if (requestCode == CREATE_JSON_REQUEST) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null && pendingJson != null) {
                Uri uri = data.getData();
                try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
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

    private void startJsonImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/json", "text/json", "text/plain", "application/octet-stream"
        });
        try {
            startActivityForResult(intent, OPEN_JSON_REQUEST);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Не найдено приложение для выбора JSON-файла", Toast.LENGTH_LONG).show();
        }
    }

    private void readAndImportJson(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            if (in == null) throw new IllegalStateException("Не удалось открыть файл");
            byte[] block = new byte[8192];
            int read;
            while ((read = in.read(block)) != -1) {
                buffer.write(block, 0, read);
            }
            String json = buffer.toString(StandardCharsets.UTF_8.name());
            String quoted = JSONObject.quote(json);
            webView.evaluateJavascript("window.applyImportedEventsJson(" + quoted + ")", null);
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка загрузки событий: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void startJsonExport(String json, String filename) {
        String safeName = (filename == null || filename.trim().isEmpty())
                ? "maya_calendar_events.json"
                : filename;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, safeName);
                values.put(MediaStore.Downloads.MIME_TYPE, "application/json");
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MayaCalendar");
                values.put(MediaStore.Downloads.IS_PENDING, 1);

                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new IllegalStateException("Не удалось создать файл");

                try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
                    if (out == null) throw new IllegalStateException("Не удалось открыть файл");
                    out.write(json.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }

                values.clear();
                values.put(MediaStore.Downloads.IS_PENDING, 0);
                getContentResolver().update(uri, values, null, null);
                Toast.makeText(this, "События сохранены в Загрузки/MayaCalendar", Toast.LENGTH_LONG).show();
                return;
            } catch (Exception e) {
                Toast.makeText(this, "Прямое сохранение не удалось, откроется выбор места", Toast.LENGTH_SHORT).show();
            }
        }

        pendingJson = json;
        pendingJsonFilename = safeName;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, pendingJsonFilename);
        try {
            startActivityForResult(intent, CREATE_JSON_REQUEST);
        } catch (ActivityNotFoundException e) {
            pendingJson = null;
            Toast.makeText(this, "Не найдено приложение для сохранения файла", Toast.LENGTH_LONG).show();
        }
    }

    private void printCurrentPage() {
        PrintManager printManager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
        String jobName = getString(R.string.app_name) + " — календарь";
        printManager.print(jobName, webView.createPrintDocumentAdapter(jobName), new PrintAttributes.Builder().build());
    }

    public final class AndroidBridge {
        private final Activity activity;

        AndroidBridge(Activity activity) {
            this.activity = activity;
        }

        @JavascriptInterface
        public void exportJson(String json, String filename) {
            activity.runOnUiThread(() -> startJsonExport(json, filename));
        }

        @JavascriptInterface
        public void importJson() {
            activity.runOnUiThread(MainActivity.this::startJsonImport);
        }

        @JavascriptInterface
        public void printPage() {
            activity.runOnUiThread(MainActivity.this::printCurrentPage);
        }
    }
}

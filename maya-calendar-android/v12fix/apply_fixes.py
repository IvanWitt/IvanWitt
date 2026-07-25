from pathlib import Path
p=Path('maya-calendar-android/project/app/src/main/assets/index.html')
s=p.read_text('utf-8')
s=s.replace('content="width=device-width, initial-scale=1.0"','content="width=device-width, initial-scale=1.0, viewport-fit=cover"')
s=s.replace('''    .appbar {
      position:sticky; top:0; z-index:300; height:58px; display:flex; align-items:center; gap:12px;
      padding:0 14px; background:rgba(35,68,63,.98); color:#fff; box-shadow:0 4px 14px rgba(0,0,0,.18); backdrop-filter:blur(8px);
    }''','''    .appbar {
      position:sticky; top:0; z-index:300; min-height:58px; height:calc(58px + env(safe-area-inset-top, 0px)); display:flex; align-items:center; gap:12px;
      box-sizing:border-box; padding:env(safe-area-inset-top, 0px) 14px 0; background:rgba(35,68,63,.98); color:#fff; box-shadow:0 4px 14px rgba(0,0,0,.18); backdrop-filter:blur(8px);
    }''')
s=s.replace('top:66px; left:12px;','top:calc(66px + env(safe-area-inset-top, 0px)); left:12px;')
s=s.replace('<div><span>Версия</span><strong>1.2.0</strong></div>','<div><span>Версия</span><strong>1.2</strong></div>')
s=s.replace('            <div><span>Сайт</span><a href="https://iwitt.ru">iwitt.ru</a></div>\n','')
s=s.replace('    <footer>Автономный HTML-файл. Для работы расчётов подключение к интернету не требуется.</footer>\n','')
s=s.replace('    function applyImportedEventsJson(text){\n','    function importEventsJsonText(text){\n')
s=s.replace('      const count=applyImportedEventsJson(text);\n','      const count=importEventsJsonText(text);\n')
s=s.replace('    window.applyImportedEventsJson=function(text){\n','    window.receiveImportedEventsJson=function(text){\n')
s=s.replace('      applyImportedEventsJson(text);\n','      importEventsJsonText(text);\n')
s=s.replace('      const parsed=JSON.parse(text);\n',"      const cleanText=String(text ?? '').replace(/^\\uFEFF/, '').trim();\n      if(!cleanText) throw new Error('Файл пуст');\n      const parsed=JSON.parse(cleanText);\n")
p.write_text(s,'utf-8')

jp=Path('maya-calendar-android/project/app/src/main/java/ru/ivwitt/mayacalendar/MainActivity.java')
j=jp.read_text('utf-8')
j=j.replace('import android.view.ViewGroup;\nimport android.view.WindowInsets;\n','import android.view.Gravity;\nimport android.view.ViewGroup;\nimport android.view.Window;\n')
j=j.replace('import android.widget.ImageView;\nimport android.widget.Toast;\n','import android.widget.ImageView;\nimport android.widget.TextView;\nimport android.widget.Toast;\n')
j=j.replace('''        getWindow().setStatusBarColor(Color.rgb(35, 68, 63));
        getWindow().setNavigationBarColor(Color.rgb(35, 68, 63));

        rootView = new FrameLayout(this);''','''        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(35, 68, 63));
        window.setNavigationBarColor(Color.rgb(35, 68, 63));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(true);
        }

        rootView = new FrameLayout(this);''')
old='''        splashView = new ImageView(this);
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
'''
new='''        FrameLayout splashLayer = new FrameLayout(this);
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
'''
if old not in j: raise SystemExit('splash block not found')
j=j.replace(old,new)
a=j.index('    private void dismissSplashWhenReady()')
b=j.index('\n    @Override\n    protected void onSaveInstanceState',a)
repl='''    private void dismissSplashWhenReady() {
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
'''
j=j[:a]+repl+j[b:]
j=j.replace('webView.evaluateJavascript("window.applyImportedEventsJson(" + quoted + ")", null);','webView.evaluateJavascript("if(window.receiveImportedEventsJson){window.receiveImportedEventsJson(" + quoted + ");}void 0;", null);')
jp.write_text(j,'utf-8')

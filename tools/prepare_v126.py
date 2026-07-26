from pathlib import Path
import re

ROOT=Path('.')
index=ROOT/'app/src/main/assets/index.html'
java=ROOT/'app/src/main/java/ru/ivwitt/mayacalendar/MainActivity.java'

s=index.read_text('utf-8')

# Remove the HTML startup splash markup entirely.
start=s.find('  <div id="startupSplash"')
if start!=-1:
    end=s.find('  <header class="appbar"',start)
    if end==-1:
        raise SystemExit('Could not find appbar after startup splash')
    s=s[:start]+s[end:]

# Remove unused startup splash CSS/keyframes.
css_start=s.find('    #startupSplash{')
if css_start!=-1:
    css_end=s.find('    /* v1.2.3',css_start)
    if css_end!=-1:
        s=s[:css_start]+s[css_end:]

# Remove startup splash JS function and invocation.
fn_start=s.find('    function initStartupSplash(){')
if fn_start!=-1:
    fn_end=s.find('    async function importEvents(file){',fn_start)
    if fn_end==-1:
        raise SystemExit('Could not find importEvents after startup splash function')
    s=s[:fn_start]+s[fn_end:]
s=s.replace('      initStartupSplash();\n','')

# Inject runtime fixes before DOMContentLoaded can fire.
tags='    <script src="tzolkin-sequence-fix.js"></script>\n    <script src="moon-calendar-v2.js"></script>\n'
for tag in ['<script src="tzolkin-sequence-fix.js"></script>','<script src="moon-calendar-v2.js"></script>']:
    s=s.replace('    '+tag+'\n','').replace(tag+'\n','')
if '</body>' not in s:
    raise SystemExit('Missing </body>')
s=s.replace('</body>',tags+'</body>',1)
index.write_text(s,'utf-8')

# Remove the custom native splash layer from MainActivity while preserving WebView behavior.
j=java.read_text('utf-8')
j=j.replace('import android.view.Gravity;\n','')
j=j.replace('import android.widget.ImageView;\n','')
j=j.replace('import android.widget.TextView;\n','')
j=j.replace('    private ImageView splashView;\n','')
j=j.replace('    private boolean splashDismissed = false;\n','')

block_start=j.find('        FrameLayout splashLayer = new FrameLayout(this);')
if block_start!=-1:
    block_end=j.find('        setContentView(rootView);',block_start)
    if block_end==-1:
        raise SystemExit('Could not find setContentView after native splash')
    j=j[:block_start]+j[block_end:]

j=j.replace('                dismissSplashWhenReady();\n','')
j=j.replace('          dismissSplashWhenReady();\n','')
method_start=j.find('    private void dismissSplashWhenReady() {')
if method_start!=-1:
    method_end=j.find('    @Override\n    protected void onSaveInstanceState',method_start)
    if method_end==-1:
        raise SystemExit('Could not find onSaveInstanceState after splash methods')
    j=j[:method_start]+j[method_end:]

j=j.replace('.addJavaScriptInterface(','.addJavascriptInterface(')
java.write_text(j,'utf-8')

for rel in [
    'app/src/main/res/drawable-nodpi/splash_maya.webp',
    'app/src/main/res/drawable/splash_maya.webp',
    'app/src/main/assets/splash_start.png',
]:
    p=ROOT/rel
    if p.exists(): p.unlink()

# Android 12+ mandates a very short system launch surface. Make it neutral, transparent-icon and animation-free.
values_v31=ROOT/'app/src/main/res/values-v31'
values_v31.mkdir(parents=True,exist_ok=True)
(values_v31/'themes.xml').write_text('''<resources>\n    <style name="Theme.MayaCalendar" parent="android:style/Theme.Material.Light.NoActionBar">\n        <item name="android:fontFamily">sans</item>\n        <item name="android:colorAccent">#1F4E78</item>\n        <item name="android:navigationBarColor">#23443F</item>\n        <item name="android:statusBarColor">#23443F</item>\n        <item name="android:windowLightStatusBar">false</item>\n        <item name="android:windowActionModeOverlay">true</item>\n        <item name="android:windowSplashScreenBackground">#F3EFE6</item>\n        <item name="android:windowSplashScreenAnimatedIcon">@drawable/transparent_splash</item>\n        <item name="android:windowSplashScreenAnimationDuration">0</item>\n    </style>\n</resources>\n''','utf-8')

drawable=ROOT/'app/src/main/res/drawable'
drawable.mkdir(parents=True,exist_ok=True)
(drawable/'transparent_splash.xml').write_text('''<?xml version="1.0" encoding="utf-8"?>\n<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">\n    <solid android:color="@android:color/transparent" />\n    <size android:width="1dp" android:height="1dp" />\n</shape>\n''','utf-8')

gradle=ROOT/'app/build.gradle'
g=gradle.read_text('utf-8')
g=re.sub(r'versionCode\s+\d+','versionCode 6',g)
g=re.sub(r"versionName\s+'[^']+'","versionName '1.2.6'",g)
gradle.write_text(g,'utf-8')

print('Prepared v1.2.6: no custom splash, configurable 13-moon calendar, native system splash neutralized.')

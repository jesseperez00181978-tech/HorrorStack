package com.horrorstack.tv;

import android.content.Intent;
import android.os.Bundle;
import android.net.Uri;
import android.webkit.ValueCallback;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private boolean nativeImport;
    private ValueCallback<Uri[]> fileCallback;
    private final ActivityResultLauncher<String[]> playlistPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (nativeImport) {
                    nativeImport = false;
                    if (uri != null) readPlaylist(uri);
                    return;
                }
                if (fileCallback != null) {
                    fileCallback.onReceiveValue(uri == null ? null : new Uri[]{uri});
                    fileCallback = null;
                }
            });

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                    FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                playlistPicker.launch(new String[]{"*/*"});
                return true;
            }
        });
        // Keep the native bridge confined to our bundled page.
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri target = request.getUrl();
                if ("intent".equals(target.getScheme())) {
                    try {
                        Intent parsed = Intent.parseUri(target.toString(), Intent.URI_INTENT_SCHEME);
                        Uri data = parsed.getData();
                        if (data != null && ("https".equals(data.getScheme()) || "http".equals(data.getScheme())))
                            openExternal(data.toString());
                    } catch (java.net.URISyntaxException ignored) { }
                    return true;
                }
                if ("https".equals(target.getScheme()) || "http".equals(target.getScheme())) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, target)); }
                    catch (android.content.ActivityNotFoundException ignored) { }
                }
                return true;
            }
        });
        webView.addJavascriptInterface(new NativeBridge(), "AndroidPlayer");
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void readPlaylist(Uri uri) {
        new Thread(() -> {
            try (java.io.InputStream input = getContentResolver().openInputStream(uri);
                 java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
                if (input == null) throw new java.io.IOException();
                byte[] buffer = new byte[8192];
                int length;
                while ((length = input.read(buffer)) != -1) {
                    if (output.size() + length > 10 * 1024 * 1024) throw new java.io.IOException();
                    output.write(buffer, 0, length);
                }
                String text = output.toString("UTF-8");
                runOnUiThread(() -> webView.evaluateJavascript(
                        "window.importHorrorPlaylist(" + org.json.JSONObject.quote(text) + ")", null));
            } catch (Exception error) {
                runOnUiThread(() -> android.widget.Toast.makeText(this,
                        "Could not read playlist. Try Paste M3U text or choose a file under 10 MB.",
                        android.widget.Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private boolean isWebStream(String url) {
        if (url == null) return false;
        Uri uri = Uri.parse(url.trim());
        return "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
    }

    private void playInTopPlayer(String url, String title) {
        if (!isWebStream(url)) return;
        final String cleanUrl = url.trim();
        final String cleanTitle = title == null || title.trim().isEmpty() ? "HorrorStack" : title.trim();

        runOnUiThread(() -> {
            String qUrl = org.json.JSONObject.quote(cleanUrl);
            String qTitle = org.json.JSONObject.quote(cleanTitle);
            String js =
                    "(function(){"
                    + "const url=" + qUrl + ";"
                    + "const title=" + qTitle + ";"
                    + "const video=document.getElementById('horrorVideo');"
                    + "const msg=document.getElementById('playerMessage');"
                    + "if(!video){AndroidPlayer.playNative(url,title);return;}"
                    + "if(window.__hsTopErrorHandler){video.removeEventListener('error',window.__hsTopErrorHandler);}"
                    + "if(window.__hsTopLoadedHandler){video.removeEventListener('loadedmetadata',window.__hsTopLoadedHandler);}"
                    + "if(window.__hsTopPlayingHandler){video.removeEventListener('playing',window.__hsTopPlayingHandler);}"
                    + "if(window.__hsTopFallbackTimer){clearTimeout(window.__hsTopFallbackTimer);window.__hsTopFallbackTimer=null;}"
                    + "if(window.__hsBridgeHls){try{window.__hsBridgeHls.destroy();}catch(e){}window.__hsBridgeHls=null;}"
                    + "let fallbackStarted=false;"
                    + "const setMsg=(text)=>{if(msg)msg.innerHTML=text;};"
                    + "const clearTimer=()=>{if(window.__hsTopFallbackTimer){clearTimeout(window.__hsTopFallbackTimer);window.__hsTopFallbackTimer=null;}};"
                    + "const fallback=()=>{"
                    + "if(fallbackStarted)return;"
                    + "fallbackStarted=true;"
                    + "clearTimer();"
                    + "if(window.__hsBridgeHls){try{window.__hsBridgeHls.destroy();}catch(e){}window.__hsBridgeHls=null;}"
                    + "try{video.pause();video.removeAttribute('src');video.load();}catch(e){}"
                    + "setMsg('<strong>Internal player could not decode this feed.</strong> Opening the HorrorStack player...');"
                    + "AndroidPlayer.playNative(url,title);"
                    + "};"
                    + "window.__hsTopErrorHandler=()=>fallback();"
                    + "window.__hsTopLoadedHandler=()=>{clearTimer();setMsg('<strong>Signal loaded.</strong> Press play if playback does not start automatically.');};"
                    + "window.__hsTopPlayingHandler=()=>{clearTimer();setMsg('<strong>Now playing.</strong> HorrorStack internal player is active.');};"
                    + "video.addEventListener('error',window.__hsTopErrorHandler);"
                    + "video.addEventListener('loadedmetadata',window.__hsTopLoadedHandler);"
                    + "video.addEventListener('playing',window.__hsTopPlayingHandler);"
                    + "try{video.pause();video.removeAttribute('src');video.load();}catch(e){}"
                    + "setMsg('<strong>Connecting...</strong> Trying the HorrorStack internal player first.');"
                    + "const isHls=/\\.m3u8(?:[?#]|$)/i.test(url);"
                    + "if(isHls&&window.Hls&&Hls.isSupported()){"
                    + "const hls=new Hls();window.__hsBridgeHls=hls;"
                    + "hls.on(Hls.Events.MANIFEST_PARSED,()=>{const p=video.play();if(p&&p.catch)p.catch(err=>{if(!err||err.name!=='NotAllowedError')fallback();});});"
                    + "hls.on(Hls.Events.ERROR,(event,data)=>{if(data&&data.fatal)fallback();});"
                    + "try{hls.loadSource(url);hls.attachMedia(video);}catch(e){fallback();}"
                    + "}else{"
                    + "video.src=url;video.load();"
                    + "try{const p=video.play();if(p&&p.catch)p.catch(err=>{if(!err||err.name!=='NotAllowedError')fallback();});}catch(e){fallback();}"
                    + "}"
                    + "window.__hsTopFallbackTimer=setTimeout(()=>{if(video.readyState===0&&!fallbackStarted)fallback();},15000);"
                    + "})();";
            webView.evaluateJavascript(js, null);
        });
    }

    private void openNativePlayer(String url, String title) {
        if (!isWebStream(url)) return;
        Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
        intent.putExtra("url", url.trim());
        intent.putExtra("title", title == null ? "HorrorStack" : title);
        startActivity(intent);
    }

    private void openExternal(String url) {
        Uri uri = Uri.parse(url);
        if (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme())) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/*");
            startActivity(Intent.createChooser(intent, "Open in video player"));
        } catch (android.content.ActivityNotFoundException error) {
            android.widget.Toast.makeText(this, "No external video player is installed.", android.widget.Toast.LENGTH_LONG).show();
        }
    }

    public final class NativeBridge {
        @JavascriptInterface public void importPlaylist() {
            runOnUiThread(() -> {
                nativeImport = true;
                playlistPicker.launch(new String[]{"*/*"});
            });
        }

        @JavascriptInterface public void playExternal(String url) {
            runOnUiThread(() -> openExternal(url));
        }

        // The existing page calls AndroidPlayer.play(...) for every selected channel/movie.
        // Route that call into the visible top player first. The injected JS calls playNative
        // only when WebView/HLS.js cannot load or decode the stream.
        @JavascriptInterface public void play(String url, String title) {
            playInTopPlayer(url, title);
        }

        @JavascriptInterface public void playNative(String url, String title) {
            runOnUiThread(() -> openNativePlayer(url, title));
        }
    }
}

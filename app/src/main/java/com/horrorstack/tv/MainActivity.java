package com.horrorstack.tv;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import java.util.Locale;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private PlayerView inlineView;
    private ExoPlayer inlinePlayer;
    private String inlineUrl;
    private String inlineTitle = "HorrorStack";
    private long inlinePosition;
    private boolean inlinePlayWhenReady = true;
    private boolean inlineVisible;
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
        inlineView = findViewById(R.id.inlinePlayerView);

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

    @Override protected void onStart() {
        super.onStart();
        if (inlineUrl != null && inlineVisible && inlinePlayer == null) startInlineEngine();
    }

    @Override protected void onStop() {
        releaseInlineEngine(false);
        super.onStop();
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

    private void playInlinePlayer(String url, String title) {
        if (!isWebStream(url)) return;
        inlineUrl = url.trim();
        inlineTitle = title == null || title.trim().isEmpty() ? "HorrorStack" : title.trim();
        inlinePosition = 0;
        inlinePlayWhenReady = true;

        runOnUiThread(() -> {
            installInlinePositionBridge();
            startInlineEngine();
        });
    }

    private void installInlinePositionBridge() {
        String js = "(function(){"
                + "const video=document.getElementById('horrorVideo');"
                + "if(!video){AndroidPlayer.playNative(" + org.json.JSONObject.quote(inlineUrl) + ","
                + org.json.JSONObject.quote(inlineTitle) + ");return;}"
                + "video.style.visibility='hidden';"
                + "if(window.__hsInlineScrollHandler){window.removeEventListener('scroll',window.__hsInlineScrollHandler,true);window.removeEventListener('resize',window.__hsInlineScrollHandler,true);}"
                + "window.__hsInlinePosition=function(){"
                + "const r=video.getBoundingClientRect();const d=window.devicePixelRatio||1;"
                + "const visible=!!(video.offsetParent&&r.width>2&&r.height>2&&r.bottom>0&&r.top<window.innerHeight);"
                + "AndroidPlayer.positionInline(r.left,r.top,r.width,r.height,d,visible);"
                + "};"
                + "window.__hsInlineScrollHandler=function(){if(window.__hsInlineRaf)return;window.__hsInlineRaf=requestAnimationFrame(function(){window.__hsInlineRaf=0;window.__hsInlinePosition();});};"
                + "window.addEventListener('scroll',window.__hsInlineScrollHandler,true);window.addEventListener('resize',window.__hsInlineScrollHandler,true);"
                + "window.__hsInlinePosition();"
                + "})();";
        webView.evaluateJavascript(js, null);
        updatePageMessage("<strong>Connecting...</strong> HorrorStack native internal player is starting.");
    }

    private void startInlineEngine() {
        if (inlineUrl == null || !isWebStream(inlineUrl)) return;
        releaseInlineEngine(false);

        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent("HorrorStack/1.0.3")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(20000);

        inlinePlayer = new ExoPlayer.Builder(this)
                .setRenderersFactory(new DefaultRenderersFactory(this).setEnableDecoderFallback(true))
                .setMediaSourceFactory(new DefaultMediaSourceFactory(this).setDataSourceFactory(http))
                .build();

        inlineView.setPlayer(inlinePlayer);
        inlineView.setCustomErrorMessage(null);

        inlinePlayer.addListener(new Player.Listener() {
            @Override public void onIsPlayingChanged(boolean isPlaying) {
                if (isPlaying) updatePageMessage("<strong>Now playing.</strong> HorrorStack native internal player is active.");
            }

            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING)
                    updatePageMessage("<strong>Buffering...</strong> HorrorStack is locking onto the stream.");
                else if (state == Player.STATE_ENDED)
                    webView.evaluateJavascript("document.getElementById('horrorVideo')?.dispatchEvent(new Event('ended'));", null);
            }

            @Override public void onPlayerError(PlaybackException error) {
                if (error.errorCode >= 4000 && error.errorCode < 5000) {
                    long position = inlinePlayer == null ? inlinePosition : inlinePlayer.getCurrentPosition();
                    String fallbackUrl = inlineUrl;
                    updatePageMessage("<strong>Switching decoder...</strong> Opening HorrorStack software playback.");
                    releaseInlineEngine(true);
                    if (fallbackUrl != null) {
                        Intent fallback = new Intent(MainActivity.this, SoftwarePlayerActivity.class);
                        fallback.putExtra("url", fallbackUrl);
                        fallback.putExtra("position", position);
                        startActivity(fallback);
                    }
                    return;
                }
                inlineView.setCustomErrorMessage("This channel could not play (" + error.getErrorCodeName()
                        + "). Check the connection or try another channel.");
                inlineView.showController();
                updatePageMessage("The native player could not open this source. Check the connection or try another channel.");
            }
        });

        Uri uri = Uri.parse(inlineUrl);
        MediaItem.Builder item = new MediaItem.Builder().setUri(uri);
        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
        String output = uri.getQueryParameter("output");
        if (path.endsWith(".m3u8") || "m3u8".equalsIgnoreCase(output)) item.setMimeType(MimeTypes.APPLICATION_M3U8);
        else if (path.endsWith(".mpd")) item.setMimeType(MimeTypes.APPLICATION_MPD);

        inlinePlayer.setMediaItem(item.build());
        inlinePlayer.seekTo(inlinePosition);
        inlinePlayer.prepare();
        inlinePlayer.setPlayWhenReady(inlinePlayWhenReady);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void releaseInlineEngine(boolean clearSource) {
        if (inlinePlayer != null) {
            inlinePosition = inlinePlayer.isCurrentMediaItemLive() ? 0 : inlinePlayer.getCurrentPosition();
            inlinePlayWhenReady = inlinePlayer.getPlayWhenReady();
            inlineView.setPlayer(null);
            inlinePlayer.release();
            inlinePlayer = null;
        }
        if (clearSource) {
            inlineUrl = null;
            inlinePosition = 0;
            inlinePlayWhenReady = true;
            inlineVisible = false;
            inlineView.setVisibility(View.GONE);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            restoreHtmlVideo();
        }
    }

    private void restoreHtmlVideo() {
        String js = "(function(){"
                + "const video=document.getElementById('horrorVideo');if(video)video.style.visibility='';"
                + "if(window.__hsInlineScrollHandler){window.removeEventListener('scroll',window.__hsInlineScrollHandler,true);window.removeEventListener('resize',window.__hsInlineScrollHandler,true);}"
                + "window.__hsInlineScrollHandler=null;window.__hsInlinePosition=null;"
                + "})();";
        webView.evaluateJavascript(js, null);
    }

    private void updatePageMessage(String html) {
        runOnUiThread(() -> webView.evaluateJavascript(
                "(function(){const m=document.getElementById('playerMessage');if(m)m.innerHTML="
                        + org.json.JSONObject.quote(html) + ";})();", null));
    }

    private void positionInline(double left, double top, double width, double height, double ratio, boolean visible) {
        runOnUiThread(() -> {
            if (!visible || width <= 2 || height <= 2) {
                inlineView.setVisibility(View.GONE);
                inlineVisible = false;
                return;
            }
            double scale = ratio > 0 ? ratio : getResources().getDisplayMetrics().density;
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    Math.max(1, (int)Math.round(width * scale)),
                    Math.max(1, (int)Math.round(height * scale)));
            params.leftMargin = (int)Math.round(left * scale);
            params.topMargin = (int)Math.round(top * scale);
            inlineView.setLayoutParams(params);
            inlineView.setVisibility(View.VISIBLE);
            inlineVisible = true;
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

        @JavascriptInterface public void play(String url, String title) {
            playInlinePlayer(url, title);
        }

        @JavascriptInterface public void playNative(String url, String title) {
            runOnUiThread(() -> openNativePlayer(url, title));
        }

        @JavascriptInterface public void stopInline() {
            runOnUiThread(() -> releaseInlineEngine(true));
        }

        @JavascriptInterface public void positionInline(double left, double top, double width,
                double height, double ratio, boolean visible) {
            MainActivity.this.positionInline(left, top, width, height, ratio, visible);
        }
    }
}

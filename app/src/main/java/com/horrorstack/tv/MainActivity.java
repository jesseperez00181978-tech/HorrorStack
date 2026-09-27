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
    private ValueCallback<Uri[]> fileCallback;
    private final ActivityResultLauncher<String[]> playlistPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (fileCallback != null) {
                    fileCallback.onReceiveValue(uri == null ? null : new Uri[]{uri});
                    fileCallback = null;
                }
            });

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        WebView webView = findViewById(R.id.webView);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
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

    public final class NativeBridge {
        @JavascriptInterface
        public void play(String url, String title) {
            runOnUiThread(() -> {
                Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
                intent.putExtra("url", url);
                intent.putExtra("title", title);
                startActivity(intent);
            });
        }
    }
}

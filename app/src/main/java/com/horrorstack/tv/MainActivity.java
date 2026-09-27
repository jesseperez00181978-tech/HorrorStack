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

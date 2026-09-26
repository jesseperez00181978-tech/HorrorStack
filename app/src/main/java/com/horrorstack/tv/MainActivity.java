package com.horrorstack.tv;

import android.content.Intent;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
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
        webView.setWebChromeClient(new WebChromeClient());
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

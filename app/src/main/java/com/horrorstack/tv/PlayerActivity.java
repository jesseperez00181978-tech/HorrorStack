package com.horrorstack.tv;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import java.util.Locale;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class PlayerActivity extends AppCompatActivity {
    private ExoPlayer player;
    private PlayerView view;
    private Uri uri;
    private long position;
    private boolean playWhenReady = true;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        setContentView(R.layout.activity_player);
        view = findViewById(R.id.playerView);
        view.setFullscreenButtonClickListener(isFullscreen -> finish());

        String url = getIntent().getStringExtra("url");
        if (url == null) { finish(); return; }
        uri = Uri.parse(url.trim());
        if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) {
            finish(); return;
        }
        if (state != null) {
            position = state.getLong("position", 0);
            playWhenReady = state.getBoolean("playWhenReady", true);
        } else {
            position = getIntent().getLongExtra("position", 0);
            playWhenReady = getIntent().getBooleanExtra("playWhenReady", true);
        }
    }

    @Override protected void onStart() {
        super.onStart();
        if (uri == null || isFinishing()) return;
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent("HorrorStack/1.0.3")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000).setReadTimeoutMs(20000);
        player = new ExoPlayer.Builder(this)
                .setRenderersFactory(new androidx.media3.exoplayer.DefaultRenderersFactory(this)
                        .setEnableDecoderFallback(true))
                .setMediaSourceFactory(new DefaultMediaSourceFactory(this).setDataSourceFactory(http)).build();
        view.setPlayer(player);
        view.setCustomErrorMessage(null);
        view.showController();
        player.addListener(new Player.Listener() {
            @Override public void onPlayerError(PlaybackException error) {
                if (error.errorCode >= 4000 && error.errorCode < 5000) {
                    Intent fallback = new Intent(PlayerActivity.this, SoftwarePlayerActivity.class);
                    fallback.putExtra("url", uri.toString());
                    fallback.putExtra("position", player == null ? position : player.getCurrentPosition());
                    startActivity(fallback);
                    finish();
                    return;
                }
                view.setCustomErrorMessage("This channel could not play (" + error.getErrorCodeName()
                        + "). Check the connection or try another channel.");
                view.showController();
            }
        });
        MediaItem.Builder item = new MediaItem.Builder().setUri(uri);
        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
        String output = uri.getQueryParameter("output");
        if (path.endsWith(".m3u8") || "m3u8".equalsIgnoreCase(output)) item.setMimeType(MimeTypes.APPLICATION_M3U8);
        else if (path.endsWith(".mpd")) item.setMimeType(MimeTypes.APPLICATION_MPD);
        player.setMediaItem(item.build());
        player.seekTo(position);
        player.prepare();
        player.setPlayWhenReady(playWhenReady);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putLong("position", player == null ? position : player.getCurrentPosition());
        state.putBoolean("playWhenReady", player == null ? playWhenReady : player.getPlayWhenReady());
        super.onSaveInstanceState(state);
    }

    @Override protected void onStop() {
        if (player != null) {
            position = player.isCurrentMediaItemLive() ? 0 : player.getCurrentPosition();
            playWhenReady = player.getPlayWhenReady();
            view.setPlayer(null);
            player.release();
            player = null;
        }
        super.onStop();
    }
}

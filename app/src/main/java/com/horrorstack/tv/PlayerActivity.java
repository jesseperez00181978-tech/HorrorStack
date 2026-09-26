package com.horrorstack.tv;

import android.net.Uri;
import android.os.Bundle;
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
        setContentView(R.layout.activity_player);
        view = findViewById(R.id.playerView);
        String url = getIntent().getStringExtra("url");
        if (url == null) { finish(); return; }
        uri = Uri.parse(url.trim());
        if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) {
            finish(); return;
        }
        if (state != null) {
            position = state.getLong("position", 0);
            playWhenReady = state.getBoolean("playWhenReady", true);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override protected void onStart() {
        super.onStart();
        if (uri == null || isFinishing()) return;
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent("HorrorStack/0.4.1")
                .setConnectTimeoutMs(15000).setReadTimeoutMs(20000);
        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(this).setDataSourceFactory(http)).build();
        view.setPlayer(player);
        view.setCustomErrorMessage(null);
        player.addListener(new Player.Listener() {
            @Override public void onPlayerError(PlaybackException error) {
                // Never display the private stream URL or provider credentials.
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

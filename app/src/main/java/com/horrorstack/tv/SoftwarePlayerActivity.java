package com.horrorstack.tv;

import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.graphics.Color;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;
import java.util.ArrayList;
import java.util.Locale;

/** In-app software decoder used when Android's media codec fails. */
public class SoftwarePlayerActivity extends AppCompatActivity {
    private LibVLC vlc;
    private MediaPlayer player;
    private VLCVideoLayout video;
    private TextView status;
    private Button pause;
    private SeekBar seek;
    private Uri uri;
    private long position;
    private boolean playWhenReady = true;
    private boolean seeking;
    private boolean initialSeek;

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

        String url = getIntent().getStringExtra("url");
        if (url == null) { finish(); return; }
        uri = Uri.parse(url);
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            finish(); return;
        }
        position = state == null ? getIntent().getLongExtra("position", 0) : state.getLong("position", 0);
        playWhenReady = state == null || state.getBoolean("playing", true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        video = new VLCVideoLayout(this);
        root.addView(video, new LinearLayout.LayoutParams(-1, 0, 1));
        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setPadding(16, 4, 16, 4);
        status.setText("Opening HorrorStack software player…");
        root.addView(status);
        status.setOnLongClickListener(v -> {
            try (java.io.InputStream input = getAssets().open("LIBVLC-NOTICE.txt")) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] bytes = new byte[4096]; int count;
                while ((count = input.read(bytes)) != -1) out.write(bytes, 0, count);
                new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("LibVLC license")
                        .setMessage(out.toString("UTF-8")).setPositiveButton("Close", null).show();
            } catch (java.io.IOException ignored) { }
            return true;
        });
        seek = new SeekBar(this); seek.setMax(1000);
        root.addView(seek);
        LinearLayout controls = new LinearLayout(this);
        addButton(controls, "Back", v -> finish());
        addButton(controls, "−10s", v -> seekBy(-10000));
        pause = addButton(controls, "Pause", v -> {
            if (player == null) return;
            if (player.isPlaying()) player.pause(); else player.play();
        });
        addButton(controls, "+10s", v -> seekBy(10000));
        addButton(controls, "Retry", v -> { releasePlayer(); startPlayer(); });
        root.addView(controls);
        setContentView(root);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) { }
            public void onStartTrackingTouch(SeekBar bar) { seeking = true; }
            public void onStopTrackingTouch(SeekBar bar) {
                if (player != null && player.isSeekable() && player.getLength() > 0)
                    player.setTime(player.getLength() * bar.getProgress() / 1000);
                seeking = false;
            }
        });
    }

    private Button addButton(LinearLayout row, String text, View.OnClickListener action) {
        Button button = new Button(this); button.setText(text); button.setOnClickListener(action);
        row.addView(button, new LinearLayout.LayoutParams(0, -2, 1)); return button;
    }

    private void seekBy(long delta) {
        if (player != null && player.isSeekable()) player.setTime(Math.max(0, player.getTime() + delta));
    }

    @Override protected void onStart() {
        super.onStart();
        if (uri != null && !isFinishing()) startPlayer();
    }

    private void startPlayer() {
        ArrayList<String> options = new ArrayList<>();
        options.add("--network-caching=1500");
        vlc = new LibVLC(this, options);
        player = new MediaPlayer(vlc);
        player.attachViews(video, null, true, false);
        initialSeek = false;
        player.setEventListener(event -> {
            if (player == null) return;
            switch (event.type) {
                case MediaPlayer.Event.Playing:
                    if (!initialSeek) {
                        initialSeek = true;
                        if (position > 0 && player.isSeekable()) player.setTime(position);
                        if (!playWhenReady) player.pause();
                    }
                    pause.setText("Pause");
                    status.setText("Playing inside HorrorStack · software decoding");
                    break;
                case MediaPlayer.Event.Paused:
                    pause.setText("Play");
                    break;
                case MediaPlayer.Event.TimeChanged:
                    long duration = player.getLength();
                    if (!seeking && duration > 0) seek.setProgress((int)(player.getTime() * 1000 / duration));
                    status.setText(time(player.getTime()) + " / " + time(duration) + " · Software player");
                    break;
                case MediaPlayer.Event.EncounteredError:
                    status.setText("The software player could not play this source. Retry or return to channels.");
                    break;
                case MediaPlayer.Event.EndReached:
                    position = 0;
                    pause.setText("Replay");
                    status.setText("Playback finished.");
                    break;
            }
        });
        Media media = new Media(vlc, uri);
        media.setHWDecoderEnabled(false, false);
        media.addOption(":http-user-agent=HorrorStack/1.0.3");
        media.addOption(":http-reconnect");
        player.setMedia(media);
        media.release();
        player.play();
    }

    private String time(long ms) {
        long seconds = Math.max(0, ms / 1000);
        return String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }

    private void releasePlayer() {
        if (player != null) {
            position = Math.max(0, player.getTime());
            playWhenReady = player.isPlaying();
            player.setEventListener(null);
            player.stop();
            player.detachViews();
            player.release();
            player = null;
        }
        if (vlc != null) {
            vlc.release();
            vlc = null;
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putLong("position", player == null ? position : Math.max(0, player.getTime()));
        state.putBoolean("playing", player == null ? playWhenReady : player.isPlaying());
        super.onSaveInstanceState(state);
    }

    @Override protected void onStop() {
        releasePlayer();
        super.onStop();
    }
}

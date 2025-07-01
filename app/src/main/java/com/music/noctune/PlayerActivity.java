package com.music.noctune;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.view.GestureDetector;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.shape.ShapeAppearanceModel;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Objects;

public class PlayerActivity extends AppCompatActivity {

    static Button btnPlay, btnNext, btnPrev, btnFavorite, btnRepeat;
    static TextView txtSongName, txtArtistName, txtTimeStart, txtTimeStop;
    static SeekBar seekMusic;
    static ImageView bgPlayerOld, bgPlayerNew;
    static ShapeableImageView albumArtOld, albumArtNew;;
    public static boolean isRunning;
    public static String source;
    static Handler handler;

    static Runnable fastForwardRunnable;
    Runnable fastRewindRunnable;

    public static PlayerActivity instance;
    public static PlayerActivity getInstance() {
        return instance;
    }


    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        Intent intent = getIntent();
        instance = this;
        isRunning = true;

        getSupportActionBar().setTitle("Now Playing");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Initialize UI Components
        btnPlay = findViewById(R.id.playbtn);
        btnNext = findViewById(R.id.btnnext);
        btnPrev = findViewById(R.id.btnprev);
        txtSongName = findViewById(R.id.txtsn);
        txtArtistName = findViewById(R.id.txtan);
        txtTimeStart = findViewById(R.id.txtsstart);
        txtTimeStop = findViewById(R.id.txtsstop);
        seekMusic = findViewById(R.id.seekbar);
        albumArtOld = findViewById(R.id.imageview_album_old);
        albumArtNew = findViewById(R.id.imageview_album_new);
        bgPlayerOld = findViewById(R.id.bgPlayer_old);
        bgPlayerNew = findViewById(R.id.bgPlayer_new);
        btnFavorite = findViewById(R.id.btn_favorite);
        btnRepeat = findViewById(R.id.btn_repeat);

        handler = MusicService.getInstance().handler;
        fastForwardRunnable = MusicService.getInstance().fastForwardRunnable;
        fastRewindRunnable = MusicService.getInstance().fastRewindRunnable;
        MusicService.remainingTime = false;

        source = intent.getStringExtra("source");

        ShapeAppearanceModel shapeModel;
        shapeModel = ShapeAppearanceModel.builder()
                    .setAllCornerSizes(25f)
                    .build();
        albumArtOld.setShapeAppearanceModel(shapeModel);
        albumArtNew.setShapeAppearanceModel(shapeModel);

        try {
            // Update song info
            MusicService.getInstance().updateSongInfo("PlayerActivity");

            // Single Press and Double press for Next button
            GestureDetector gestureDetectorBtnNext = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                    MusicService.getInstance().nextSong();
                    return false;
                }

                @Override
                public boolean onDoubleTap(@NonNull MotionEvent e) {
                    MusicService.getInstance().fastForward();
                    return false;
                }
            });

            // Single Press and Double press for Previous button
            GestureDetector gestureDetectorBtnPrev = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                    MusicService.getInstance().previousSong();
                    return false;
                }

                @Override
                public boolean onDoubleTap(@NonNull MotionEvent e) {
                    MusicService.getInstance().fastRewind();
                    return false;
                }
            });

            // Play/Pause button
            btnPlay.setOnClickListener(v -> {
                if (MusicService.mediaPlayer != null && MusicService.mediaPlayer.isPlaying()) {
                    MusicService.getInstance().pauseSong();
                } else {
                    MusicService.getInstance().playSong();
                }
            });

            // Next button
            btnNext.setOnTouchListener((v, event) -> {
                if (gestureDetectorBtnNext.onTouchEvent(event)) {
                    return true; // Handle tap actions via GestureDetector
                }

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        handler.postDelayed(fastForwardRunnable, 500);
                        return false;

                    case MotionEvent.ACTION_UP:

                    case MotionEvent.ACTION_CANCEL:
                        handler.removeCallbacks(fastForwardRunnable);
                        return false;
                }
                return false;
            });

            // Previous button
            btnPrev.setOnTouchListener((v, event) -> {
                if (gestureDetectorBtnPrev.onTouchEvent(event)) {
                    return true; // Handle tap actions via GestureDetector
                }

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        handler.postDelayed(fastRewindRunnable, 500);
                        return false;

                    case MotionEvent.ACTION_UP:

                    case MotionEvent.ACTION_CANCEL:
                        handler.removeCallbacks(fastRewindRunnable);
                        return false;
                }
                return false;
            });

            // Favorite button
            btnFavorite.setOnClickListener(v -> {
                MusicService.getInstance().toggleFavorite();
            });

            // Repeat & Shuffle button
            btnRepeat.setOnClickListener(v -> {
                MusicService.getInstance().togglePlayingMode();
            });

            // Remaining time
            txtTimeStop.setOnClickListener(v -> {
                MusicService.getInstance().showRemainingTime();
            });

        } catch (Exception e) {
            Toast.makeText(this, "Something went wrong", Toast.LENGTH_SHORT).show();
        }

    }


    // Cross fade animation for album art
    public void crossfadeAlbumArt(Bitmap newBitmap) {
        albumArtNew.setImageBitmap(newBitmap);
        albumArtNew.setAlpha(0f);

        albumArtNew.animate()
                .alpha(1f)
                .setDuration(200) // Animation duration
                .withEndAction(() -> {
                    // Swap roles
                    albumArtOld.setImageBitmap(newBitmap);
                    albumArtNew.setAlpha(0f);
                })
                .start();
    }


    // Cross fade animation for background
    public void crossfadeBackground(Bitmap newBitmap) {
        bgPlayerNew.setImageBitmap(newBitmap);
        bgPlayerNew.setAlpha(0f);

        bgPlayerNew.animate()
                .alpha(1f)
                .setDuration(200)
                .withEndAction(() -> {
                    // Swap roles
                    bgPlayerOld.setImageBitmap(newBitmap);
                    bgPlayerNew.setAlpha(0f);
                })
                .start();
    }


//    // Album art transition
//    public void setAlbumArtWithTransition() {
//        albumArtOld.animate().alpha(0f).setDuration(100).withEndAction(() -> {
//            albumArtOld.animate().alpha(1f).setDuration(100).start();
//        }).start();
//    }


    // Close PlayerActivity
    public void closePlayerActivity() {
        if (isRunning) {
            isRunning = false;
            MusicService.getInstance().updateThread();
        }
        finish(); // Close the Activity
        if (Objects.equals(source, "MiniPlayer")) {
            overridePendingTransition(0, R.anim.slide_down);
        }
    }


    // Top back button
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onBackPressed();
        MusicService.getInstance().updateSongHighlight();
        closePlayerActivity();
        return super.onOptionsItemSelected(item);
    }


    @Override
    public void onBackPressed() {
        super.onBackPressed();
        MusicService.getInstance().updateSongHighlight();
        closePlayerActivity();
    }


    //Resumes the thread when reopening it from minimized
    @Override
    protected void onResume() {
        super.onResume();
        if (!isRunning) {
            isRunning = true;
            MusicService.getInstance().updateThread();
        }
    }


    //Pause the thread when minimized
    @Override
    protected void onPause() {
        super.onPause();
        if (isRunning) {
            isRunning = false;
            MusicService.getInstance().updateThread();
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isRunning) {
            isRunning = false;
            MusicService.getInstance().updateThread();
        }
        if (Objects.equals(MusicService.source, "Favorite")) {
            FavoriteActivity.getInstance().checkFavorite();
        }
    }

}
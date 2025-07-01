package com.music.noctune;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.shape.ShapeAppearanceModel;

public class BaseActivity extends AppCompatActivity {
    public View parentView;
    public static LinearLayout miniPlayerContainer, miniPlayer;
    public static Button btnPlay, btnPrev, btnNext;
    public static TextView txtSongName, txtArtistName;
    public static ShapeableImageView albumArtOld, albumArtNew;
    public GestureDetector gestureDetector;


    @Override
    protected void onResume() {
        super.onResume();

        FrameLayout rootView = findViewById(android.R.id.content);

        if (parentView == null) {
            parentView = LayoutInflater.from(this).inflate(R.layout.mini_player, rootView, false);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
            );

            rootView.addView(parentView, params);
        }

        showMiniPlayer();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            float scale = getResources().getDisplayMetrics().density;
            int otherPaddingInPx = (int) (16 * scale); // Convert dp to px
            int bottomPaddingInPx = (int) (8 * scale); // Convert dp to px

            miniPlayerContainer = findViewById(R.id.miniPlayerContainer);
            miniPlayerContainer.setPadding(otherPaddingInPx, otherPaddingInPx, otherPaddingInPx, bottomPaddingInPx);

            swipeUpGesture();
        }

        miniPlayer.setVisibility(View.GONE);

        if (MusicService.getInstance() != null && MusicService.currentSongUri != null) {
            MusicService.getInstance().updateMiniPlayer("MiniPlayer");
        }
    }


    // Mini Player
    void showMiniPlayer() {
        miniPlayer = parentView.findViewById(R.id.miniPlayer);
        btnPlay = parentView.findViewById(R.id.btnplay);
        txtSongName = parentView.findViewById(R.id.txtsn);
        txtArtistName = parentView.findViewById(R.id.txtan);
        albumArtOld = parentView.findViewById(R.id.imageAlbumOld);
        albumArtNew = parentView.findViewById(R.id.imageAlbumNew);
        btnPlay = parentView.findViewById(R.id.btnplay);
        btnNext = parentView.findViewById(R.id.btnnext);
        btnPrev = parentView.findViewById(R.id.btnprev);

        ShapeAppearanceModel shapeModel = ShapeAppearanceModel.builder()
                .setAllCornerSizes(10f)
                .build();
        albumArtOld.setShapeAppearanceModel(shapeModel);
        albumArtNew.setShapeAppearanceModel(shapeModel);

        // Setup mini player listeners
        miniPlayer.setOnClickListener(v -> {
            Intent intent = new Intent(this, PlayerActivity.class);
            startActivity(intent);
        });

        // Long press mini player
        miniPlayer.setOnLongClickListener(v -> {
            if (MusicService.mediaPlayer != null && MusicService.mediaPlayer.isPlaying()) {
                return false;
            }
            MusicService.getInstance().exitPlayer();
            return true;
        });

        btnPlay.setOnClickListener(v -> {
            if (MusicService.mediaPlayer != null && MusicService.mediaPlayer.isPlaying()) {
                MusicService.getInstance().pauseSong();
            } else {
                MusicService.getInstance().playSong();
            }
        });

        btnNext.setOnClickListener(v -> {
            MusicService.getInstance().nextSong();
        });

        btnPrev.setOnClickListener(v -> {
            MusicService.getInstance().previousSong();
        });

    }


    // Swipe up gesture of Mini player
    void swipeUpGesture() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 100;
            private static final int SWIPE_VELOCITY_THRESHOLD = 100;

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                float diffY = e2.getY() - e1.getY();
                float diffX = e2.getX() - e1.getX();

                if (Math.abs(diffY) > Math.abs(diffX)) {
                    if (Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffY < 0) {
                            // Swipe Up
                            Intent intent = new Intent(BaseActivity.this, PlayerActivity.class);
                            intent.putExtra("source", "MiniPlayer");
                            startActivity(intent);
                            overridePendingTransition(R.anim.slide_up, 0);
                        }
                        return true;
                    }
                }
                return false;
            }
        });
        miniPlayer.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));
    }


    // Cross fade animation for album art
    public static void crossfadeAlbumArt(Bitmap newBitmap) {
        albumArtNew.setImageBitmap(newBitmap);
        albumArtNew.setAlpha(0f);

        albumArtNew.animate()
                .alpha(1f)
                .setDuration(200)
                .withEndAction(() -> {
                    // Swap roles
                    albumArtOld.setImageBitmap(newBitmap);
                    albumArtNew.setAlpha(0f);
                })
                .start();
    }

}


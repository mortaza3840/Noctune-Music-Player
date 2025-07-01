package com.music.noctune;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.shape.ShapeAppearanceModel;

import java.io.File;
import java.util.Objects;

public class ExternalPlayerActivity extends AppCompatActivity {

    Button btnplay, btnstop;
    TextView txtsname, txtaname, txtsstart, txtsstop;
    ShapeableImageView imageView;
    SeekBar seekmusic;
    MediaPlayer mediaPlayer;
    String songName;
    String artistName;
    boolean isPlay = true;
    Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_external_player);

        // Initialize UI Components
        btnplay = findViewById(R.id.btnplay);
        btnstop = findViewById(R.id.btnstop);
        txtsname = findViewById(R.id.txtsn);
        txtaname = findViewById(R.id.txtan);
        txtsstart = findViewById(R.id.txtsstart);
        txtsstop = findViewById(R.id.txtsstop);
        seekmusic = findViewById(R.id.seekbar);
        imageView = findViewById(R.id.imageview);

        ShapeAppearanceModel shapeModel;
        shapeModel = ShapeAppearanceModel.builder()
                .setAllCornerSizes(5f)
                .build();
        imageView.setShapeAppearanceModel(shapeModel);

        // Check if the activity was launched with an intent
        Intent intent = getIntent();
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri songUri = intent.getData();
            if (songUri != null) {
                songName = getSongTitle(songUri);
                artistName = getArtistName(songUri);
                playSong(songUri);
            }
        }

        // Play/Pause button
        btnplay.setOnClickListener(v -> {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                    btnplay.setBackgroundResource(R.drawable.ic_play);
                    isPlay = false;
                } else {
                    mediaPlayer.start();
                    btnplay.setBackgroundResource(R.drawable.ic_pause);
                    isPlay = true;
                }
                updateThread();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // Music control from SeekBar
        seekmusic.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null) {
                    mediaPlayer.seekTo(progress);
                    txtsstart.setText(createTime(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                    updateThread();
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (mediaPlayer != null && isPlay) {
                    mediaPlayer.start();
                    updateThread();
                }
            }
        });

        // Stop button to finish the activity
        btnstop.setOnClickListener(v -> {
            finish();
        });

    }


    // Function to play the song
    private void playSong(Uri songUri) {
        if (MusicService.mediaPlayer != null && MusicService.mediaPlayer.isPlaying()) {
            MusicService.getInstance().pauseSong();
        }

        // Display song name
        txtsname.setText(songName);

        try {
            mediaPlayer = MediaPlayer.create(this, songUri);
            if (mediaPlayer == null) {
                txtaname.setVisibility(View.GONE);
                throw new Exception("MediaPlayer is null for URI: " + songUri);
            }

            Bitmap albumArt = getAlbumArt(songUri);
            imageView.setImageBitmap(albumArt);

            // Display artist name if found
            if (Objects.equals(artistName, "Unknown Artist")) {
                txtaname.setVisibility(View.GONE);
            } else {
                txtaname.setVisibility(View.VISIBLE);
                txtaname.setText(artistName);
            }

            mediaPlayer.start();
            txtsstop.setText(createTime(mediaPlayer.getDuration()));
            btnplay.setBackgroundResource(R.drawable.ic_pause);

            seekmusic.setMax(mediaPlayer.getDuration());
            updateThread();

            mediaPlayer.setOnCompletionListener(mp -> {
                btnplay.setBackgroundResource(R.drawable.ic_play);
                updateThread();
            });

        } catch (Exception e) {
            Toast.makeText(this, "Somethings went wrong", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }


    // Start / Stop thread
    void updateThread() {
        handler.removeCallbacks(updateSeekBar);
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            handler.postDelayed(updateSeekBar, 100);
        }
    }


    // Update SeekBar
    private Runnable updateSeekBar = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null) {
                seekmusic.setProgress(mediaPlayer.getCurrentPosition());
                txtsstart.setText(createTime(mediaPlayer.getCurrentPosition()));
            }
//            System.out.println("running");
            handler.postDelayed(this, 100);
        }
    };


    // Time converter; Milliseconds to hh:mm:ss
    private String createTime(int duration) {
        int hours = duration / 1000 / 3600;
        int minutes = (duration / 1000 / 60) % 60;
        int seconds = (duration / 1000) % 60;

        // If the duration is more than 1 hour, include hours in the format
        if (hours > 0) {
            return hours + ":" +
                    (minutes < 10 ? "0" + minutes : minutes) + ":" +
                    (seconds < 10 ? "0" + seconds : seconds);
        } else {
            return minutes + ":" +
                    (seconds < 10 ? "0" + seconds : seconds);
        }
    }


    // Fetch file name from external sources
    String getFileName(Uri uri) {
        String fileName = "Unknown Song";
        try {
            if ("content".equals(uri.getScheme())) {
                String[] projection = {android.provider.OpenableColumns.DISPLAY_NAME};
                try (Cursor cursor = getContentResolver().query(uri, projection, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        fileName = cursor.getString(cursor.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME));
                    }
                }
            } else if ("file".equals(uri.getScheme())) {
                fileName = new File(uri.getPath()).getName();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return fileName;
    }


    // Fetch song title from song metadata
    String getSongTitle(Uri uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        String songTitle = getFileName(uri);

        try {
            retriever.setDataSource(this, uri);
            String title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            if (title != null && !title.isEmpty()) {
                songTitle = title;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {}
        }

        return songTitle;
    }


    // Fetch artist name from song metadata
    String getArtistName(Uri songUri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        String artistName = "Unknown Artist";

        try {
            // Set data source properly
            retriever.setDataSource(this, songUri);
            String retrievedArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);

            if (retrievedArtist != null && !retrievedArtist.isEmpty()) {
                artistName = retrievedArtist; // Update artist if found
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                retriever.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return artistName;
    }


    // Retrieve album art
    Bitmap getAlbumArt(Uri uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(this, uri);

        byte[] art = retriever.getEmbeddedPicture();

        try {
            retriever.release();
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (art != null) {
            return BitmapFactory.decodeByteArray(art, 0, art.length);
        } else {
            return null; // No album art found
        }
    }


    //Pause the song when minimized
    @Override
    protected void onPause() {
        super.onPause();
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            btnplay.setBackgroundResource(R.drawable.ic_play);
            updateThread();
        }
    }


    //Resumes the song when reopening it from minimized
    @Override
    protected void onResume() {
        super.onResume();
        if (mediaPlayer != null && isPlay) {
            mediaPlayer.start();
            btnplay.setBackgroundResource(R.drawable.ic_pause);
            updateThread();
        }
    }


    // Music close
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        handler.removeCallbacks(updateSeekBar);
    }

}
package com.music.noctune;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.View;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.palette.graphics.Palette;

import com.music.noctune.database.AppDatabase;
import com.music.noctune.database.FavoriteDao;
import com.music.noctune.database.FavoriteSong;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Random;

public class MusicService extends Service {
    public static MediaPlayer mediaPlayer;
    public static ArrayList<String> songUris;
    public static ArrayList<String> songNames;
    public static ArrayList<String> artistNames;
    public static int position = 0;
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "MusicPlayerChannel";
    private MediaSessionCompat mediaSession;
    public static Uri songUri;
    public static String songName;
    public static String artistName;
    public static String currentSongUri;
    public Bitmap albumArt;
    public int songDuration = 0;
    public boolean onSeekBarTouch = false;
    public static boolean isFavorite = false;
    public static boolean remainingTime = false;
    public static String source;
    public static String playingMode = "repeat_off";

    public static FavoriteDao favoriteDao;

    Random random = new Random();
    Handler handler = new Handler();
    Runnable skipSongRunnable = () -> skipSong();

    public static MusicService instance;
    public static MusicService getInstance() {
        return instance;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    @Override
    public void onCreate() {
        super.onCreate();
        mediaSession = new MediaSessionCompat(this, "MusicService");
        setupMediaSessionCallback();
        instance = this;
        favoriteDao = AppDatabase.getInstance(this).favoriteDao();
    }


    @Override
    public int onStartCommand(Intent startIntent, int flags, int startId) {
        if (startIntent != null) {
            startSong();
        }
        return START_STICKY;
    }


    void setupMediaSessionCallback() {
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            public void onPlay() {
                playSong();
            }

            @Override
            public void onPause() {
                pauseSong();
            }

            @Override
            public void onSkipToNext() {
                nextSong();
            }

            @Override
            public void onSkipToPrevious() {
                previousSong();
            }

            @Override
            public void onSeekTo(long pos) {
                if (mediaPlayer != null) {
                    mediaPlayer.seekTo((int) pos);
                    updatePlaybackState();
                }
            }

            @Override
            public void onCustomAction(String action, Bundle extras) {
                if (action.equals("ACTION_EXIT")) {
                    exitPlayer();
                }
                if (action.equals("ACTION_TOGGLE_MODE")) {
                    togglePlayingMode();
                }
            }
        });
    }


    // Start song
    void startSong() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.reset();
            mediaPlayer.release();
            mediaPlayer = null;
        }

        try {
            songUri = Uri.parse(songUris.get(position));
            mediaPlayer = MediaPlayer.create(this, songUri);
            songName = getSongTitle(songUri);
            albumArt = getAlbumArt(songUri);
            songDuration = mediaPlayer.getDuration();
            artistName = artistNames.get(position);
            currentSongUri = songUris.get(position);

            mediaPlayer.start();
            mediaPlayer.setOnCompletionListener(mp -> {
                songCompletion();
            });
            updateMetadata();
            updatePlaybackState();
            showNotification();
        }
        // Invalid song
        catch (Exception e) {
            songName = songNames.get(position);
            albumArt = null;
            songDuration = 0;
            artistName = artistNames.get(position);
            currentSongUri = songUris.get(position);
            updateMetadata();
            showNotification();
            handler.removeCallbacks(skipSongRunnable);
            handler.postDelayed(skipSongRunnable, 5000); // Skip song after 5 seconds
            Toast.makeText(this, "Invalid song\nFailed to update song info", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }


    // After song end
    void songCompletion() {
        switch (playingMode) {
            case "repeat_off": // Stop the player after last song
                if (position == songUris.size() - 1) {
                    Toast.makeText(this, "No next song", Toast.LENGTH_SHORT).show();
                    PlayerActivity.btnPlay.setBackgroundResource(R.drawable.ic_play);
                    BaseActivity.btnPlay.setBackgroundResource(R.drawable.ic_play);
                    cancelNotification();
                    updateThread();
                    if (!PlayerActivity.isRunning) exitPlayer();
                } else {
                    position = position + 1;
                    startSong();
                    updateSongInfo("MusicService");
                    updateMiniPlayer("MusicService");
                    updateSongHighlight();
                }
                break;

            case "repeat_all": // Play first song after last song
                if (position == songUris.size() - 1) {
                    position = 0;
                } else {
                    position = position + 1;
                }
                startSong();
                updateSongInfo("MusicService");
                updateMiniPlayer("MusicService");
                updateSongHighlight();
                break;

            case "repeat_one": // Loop one song
                startSong();
                updateSongInfo("MusicService");
                updateMiniPlayer("MusicService");
                break;

            case "single_play": // Stop player after current song
                PlayerActivity.btnPlay.setBackgroundResource(R.drawable.ic_play);
                BaseActivity.btnPlay.setBackgroundResource(R.drawable.ic_play);
                cancelNotification();
                updateThread();
                if (!PlayerActivity.isRunning) exitPlayer();
                break;

            case "shuffle": // Play random song after current song
                if (songUris.size() > 1) {
                    shufflePosition();
                    startSong();
                    updateSongInfo("MusicService");
                    updateMiniPlayer("MusicService");
                    updateSongHighlight();
                } else {
                    Toast.makeText(this, "Can't shuffle song", Toast.LENGTH_SHORT).show();
                    PlayerActivity.btnPlay.setBackgroundResource(R.drawable.ic_play);
                    BaseActivity.btnPlay.setBackgroundResource(R.drawable.ic_play);
                    cancelNotification();
                    updateThread();
                    if (!PlayerActivity.isRunning) exitPlayer();
                }
                break;
        }
    }


    // Update song information on PlayerActivity
    void updateSongInfo(String key) {
        isFavorite = (favoriteDao.getFavoriteByUri(String.valueOf(songUri)) != null);
        if (isFavorite) {
            PlayerActivity.btnFavorite.setBackgroundResource(R.drawable.ic_favorite);
        } else {
            PlayerActivity.btnFavorite.setBackgroundResource(R.drawable.ic_favorite_border);
        }

        PlayerActivity.txtSongName.setText(songName);

        if (Objects.equals(artistName, "Unknown Artist")) {
            PlayerActivity.txtArtistName.setText("");
        } else {
            PlayerActivity.txtArtistName.setText(artistName);
        }

        Bitmap bgArt;
        if (albumArt == null) {
            bgArt = BitmapFactory.decodeResource(getResources(), R.drawable.bg_player); // Use default background image
        } else {
            bgArt = albumArt; // Use album art as background image
        }

        // Resized background image
        Bitmap bgArtResized = resizeBitmap(bgArt, 64, 64);

        // Background art
        Bitmap blurredBackground = blurBitmap(this, bgArtResized.copy(Bitmap.Config.ARGB_8888, true), 25f);


        // Album art and background
        if (Objects.equals(key, "MusicService")) {
            PlayerActivity.getInstance().crossfadeAlbumArt(albumArt);
            PlayerActivity.getInstance().crossfadeBackground(blurredBackground);
        } else {
            PlayerActivity.albumArtOld.setImageBitmap(albumArt);
            PlayerActivity.albumArtNew.setImageBitmap(albumArt);
            PlayerActivity.bgPlayerOld.setImageBitmap(blurredBackground);
            PlayerActivity.bgPlayerNew.setImageBitmap(blurredBackground);
        }

        // Dynamic album art border color
        if (albumArt != null) {
            Palette.from(albumArt).generate(palette -> {
                assert palette != null;
                int color = palette.getDominantColor(Color.DKGRAY);
                PlayerActivity.albumArtOld.setStrokeColor(ColorStateList.valueOf(color));
                PlayerActivity.albumArtNew.setStrokeColor(ColorStateList.valueOf(color));
            });
        } else {
            int color = getResources().getColor(R.color.colorPrimary); // Use Primary color
            PlayerActivity.albumArtOld.setStrokeColor(ColorStateList.valueOf(color));
            PlayerActivity.albumArtNew.setStrokeColor(ColorStateList.valueOf(color));
        }

        switch (playingMode) {
            case "repeat_all":
                PlayerActivity.btnRepeat.setBackgroundResource(R.drawable.ic_repeat_all);
                break;
            case "repeat_one":
                PlayerActivity.btnRepeat.setBackgroundResource(R.drawable.ic_repeat_one);
                break;
            case "single_play":
                PlayerActivity.btnRepeat.setBackgroundResource(R.drawable.ic_single_play);
                break;
            case "shuffle":
                PlayerActivity.btnRepeat.setBackgroundResource(R.drawable.ic_shuffle);
                break;
            default:
                PlayerActivity.btnRepeat.setBackgroundResource(R.drawable.ic_repeat_off);
                break;
        }

        PlayerActivity.txtTimeStop.setText(createTime(songDuration));
        PlayerActivity.seekMusic.setMax(songDuration);

        updateThread();

        // Handle Invalid song
        if (mediaPlayer == null) {
            PlayerActivity.txtTimeStart.setText("0:00");
            PlayerActivity.txtTimeStop.setText("0:00");
            PlayerActivity.seekMusic.setProgress(0);
            PlayerActivity.seekMusic.setMax(100);
            return;
        }

//        PlayerActivity.txtsname.setSelected(false);
//        PlayerActivity.txtsname.postDelayed(() -> PlayerActivity.txtsname.setSelected(true), 2000); // Start marquee after 2 seconds

        if (mediaPlayer.isPlaying()) {
            PlayerActivity.btnPlay.setBackgroundResource(R.drawable.ic_pause);
        } else {
            PlayerActivity.btnPlay.setBackgroundResource(R.drawable.ic_play);
        }

        // Music control from SeekBar
        PlayerActivity.seekMusic.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int pos;
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null) {
                    pos = progress;
                    PlayerActivity.txtTimeStart.setText(createTime(progress));
                    if (remainingTime) {
                        int timeLeft = songDuration - progress;
                        PlayerActivity.txtTimeStop.setText(createTime(timeLeft));
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                onSeekBarTouch = true;
                updateThread();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                onSeekBarTouch = false;
                if (mediaPlayer != null) {
                    mediaPlayer.seekTo(pos);
                }
                updateThread();
                updatePlaybackState();
            }
        });

    }


    // Update song information on Mini player
    void updateMiniPlayer(String key) {

        MainActivity.getInstance().updateBottomPadding();

        if (FavoriteActivity.getInstance() != null) {
            FavoriteActivity.getInstance().updateBottomPadding();
        }

        if (BaseActivity.miniPlayer.getVisibility() == View.GONE) {
            BaseActivity.miniPlayer.setVisibility(View.VISIBLE);
        }

        BaseActivity.txtSongName.setText(songName);

        if (Objects.equals(artistName, "Unknown Artist")) {
            BaseActivity.txtArtistName.setVisibility(View.GONE);
        } else {
            BaseActivity.txtArtistName.setVisibility(View.VISIBLE);
            BaseActivity.txtArtistName.setText(artistName);
        }

        if (Objects.equals(key, "MusicService")) {
            BaseActivity.crossfadeAlbumArt(albumArt);
        } else {
            BaseActivity.albumArtOld.setImageBitmap(albumArt);
            BaseActivity.albumArtNew.setImageBitmap(albumArt);
        }

        if (mediaPlayer!= null && mediaPlayer.isPlaying()) {
            BaseActivity.btnPlay.setBackgroundResource(R.drawable.ic_pause);
        } else {
            BaseActivity.btnPlay.setBackgroundResource(R.drawable.ic_play);
        }

        // Dynamic album art and MiniPlayer border color
        if (albumArt != null) {
            Palette.from(albumArt).generate(palette -> {
                assert palette != null;
                int color = palette.getVibrantColor(getResources().getColor(R.color.colorPrimary));
                BaseActivity.albumArtOld.setStrokeColor(ColorStateList.valueOf(color));
                BaseActivity.albumArtNew.setStrokeColor(ColorStateList.valueOf(color));

                // MiniPlayer border color
                GradientDrawable drawable = (GradientDrawable) BaseActivity.miniPlayer.getBackground();
                drawable.setStroke(4, color);

                // MiniPlayer buttons color
                int pressedColor = Color.WHITE;
                int defaultColor = color;
                int[][] states = new int[][] {
                        new int[] { android.R.attr.state_pressed }, // pressed
                        new int[] {} // default
                };
                int[] colors = new int[] {
                        pressedColor,
                        defaultColor
                };
                ColorStateList colorStateList = new ColorStateList(states, colors);
                BaseActivity.btnPlay.setBackgroundTintList(colorStateList);
                BaseActivity.btnPrev.setBackgroundTintList(colorStateList);
                BaseActivity.btnNext.setBackgroundTintList(colorStateList);
            });
        } else {
            int color = getResources().getColor(R.color.colorPrimary); // Use Primary color
            BaseActivity.albumArtOld.setStrokeColor(ColorStateList.valueOf(color));
            BaseActivity.albumArtNew.setStrokeColor(ColorStateList.valueOf(color));
            GradientDrawable drawable = (GradientDrawable) BaseActivity.miniPlayer.getBackground();
            drawable.setStroke(4, color);
            BaseActivity.btnPlay.setBackgroundTintList(ColorStateList.valueOf(color));
            BaseActivity.btnPrev.setBackgroundTintList(ColorStateList.valueOf(color));
            BaseActivity.btnNext.setBackgroundTintList(ColorStateList.valueOf(color));
        }
    }


    // Resize the background image
    Bitmap resizeBitmap(Bitmap source, int maxWidth, int maxHeight) {
        int width = source.getWidth();
        int height = source.getHeight();

        float aspectRatio = (float) width / height;

        int newWidth = maxWidth;
        int newHeight = (int) (maxWidth / aspectRatio);

        if (newHeight > maxHeight) {
            newHeight = maxHeight;
            newWidth = (int) (maxHeight * aspectRatio);
        }

        return Bitmap.createScaledBitmap(source, newWidth, newHeight, true);
    }


    // Blur effect for background image
    Bitmap blurBitmap(Context context, Bitmap bitmap, float radius) {
        RenderScript rs = RenderScript.create(context);
        final Allocation input = Allocation.createFromBitmap(rs, bitmap);
        final Allocation output = Allocation.createTyped(rs, input.getType());
        ScriptIntrinsicBlur script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
        script.setRadius(radius); // 0 < radius <= 25
        script.setInput(input);
        script.forEach(output);
        output.copyTo(bitmap);
        rs.destroy();
        return bitmap;
    }


    // Play song
    void playSong() {
        if (mediaPlayer != null) {
            mediaPlayer.start();
            updatePlaybackState();
            showNotification();
            updateThread();
            PlayerActivity.btnPlay.setBackgroundResource(R.drawable.ic_pause);
            BaseActivity.btnPlay.setBackgroundResource(R.drawable.ic_pause);
        }
    }


    // Pause song
    void pauseSong() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            updatePlaybackState();
            showNotification();
            stopForeground(false);
            updateThread();
            PlayerActivity.btnPlay.setBackgroundResource(R.drawable.ic_play);
            BaseActivity.btnPlay.setBackgroundResource(R.drawable.ic_play);
        }
    }


    // Next song
    void nextSong() {
        switch (playingMode) {
            case "repeat_all":
                if (position == songUris.size() - 1) {
                    position = 0;
                } else {
                    position = position + 1;
                }
                break;

            case "shuffle":
                if (songUris.size() > 1) {
                    shufflePosition();
                } else {
                    Toast.makeText(this, "Can't shuffle song", Toast.LENGTH_SHORT).show();
                    return;
                }
                break;

            default:
                if (position == songUris.size() - 1) {
                    Toast.makeText(this, "No next song", Toast.LENGTH_SHORT).show();
                    return;
                } else {
                    position = position + 1;
                }
                break;
        }
        startSong();
        updateSongInfo("MusicService");
        updateMiniPlayer("MusicService");
        updateSongHighlight();
    }


    // Previous song
    void previousSong() {
        switch (playingMode) {
            case "repeat_all":
                if (position == 0) {
                    position = songUris.size() - 1;
                } else {
                    position = position - 1;
                }
                break;

            case "shuffle":
                if (songUris.size() > 1) {
                    shufflePosition();
                } else {
                    Toast.makeText(this, "Can't shuffle song", Toast.LENGTH_SHORT).show();
                    return;
                }
                break;

            default:
                if (position == 0) {
                    Toast.makeText(this, "No previous song", Toast.LENGTH_SHORT).show();
                    return;
                } else {
                    position = position - 1;
                }
                break;
        }
        startSong();
        updateSongInfo("MusicService");
        updateMiniPlayer("MusicService");
        updateSongHighlight();
    }


    // Fast forward
    void fastForward() {
        if (mediaPlayer != null) {
            mediaPlayer.seekTo(mediaPlayer.getCurrentPosition() + 10000);
            updateTimeStamp();
        }
    }


    // Hold fast forward button
    Runnable fastForwardRunnable = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null) {
                mediaPlayer.seekTo(mediaPlayer.getCurrentPosition() + 1000);
                updateTimeStamp();
            }
            handler.postDelayed(this, 100);
        }
    };


    // Fast rewind
    void fastRewind() {
        if (mediaPlayer != null) {
            mediaPlayer.seekTo(mediaPlayer.getCurrentPosition() - 10000);
            updateTimeStamp();
        }
    }


    // Hold fast rewind button
    Runnable fastRewindRunnable = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null) {
                mediaPlayer.seekTo(mediaPlayer.getCurrentPosition() - 1000);
                updateTimeStamp();
            }
            handler.postDelayed(this, 100);
        }
    };


    // Fetch song title from song metadata
    String getSongTitle(Uri uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        String songTitle = "Unknown Song";

        try {
            retriever.setDataSource(this, uri);
            String title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            if (title != null && !title.isEmpty()) {
                songTitle = title;
            } else {
                songTitle = songNames.get(position);
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


    // Retrieve album art
    Bitmap getAlbumArt(Uri uri) {
        if (mediaPlayer == null) {
            return null;
        }

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


    // Toggle Favorite
    void toggleFavorite() {
        if (isFavorite) {
            favoriteDao.deleteByUri(String.valueOf(songUri));
            isFavorite = false;
            PlayerActivity.btnFavorite.setBackgroundResource(R.drawable.ic_favorite_border);
        } else if (mediaPlayer != null) {
            favoriteDao.insert(new FavoriteSong(String.valueOf(songUri)));
            isFavorite = true;
            PlayerActivity.btnFavorite.setBackgroundResource(R.drawable.ic_favorite);
        }

        Intent updateIntent = new Intent("UPDATE_FAVORITES");
        sendBroadcast(updateIntent);
    }


    // Shuffle song position
    void shufflePosition() {
        int randomIndex;
        do {
            randomIndex = random.nextInt(songUris.size());
        } while (randomIndex == position); // Ensure it's a different song
        position = randomIndex;
    }


    // Change Playing Mode
    void togglePlayingMode() {
        switch (playingMode) {
            case "repeat_off":
                playingMode = "repeat_all";
                PlayerActivity.btnRepeat.setBackgroundResource(R.drawable.ic_repeat_all);
                break;
            case "repeat_all":
                playingMode = "repeat_one";
                PlayerActivity.btnRepeat.setBackgroundResource(R.drawable.ic_repeat_one);
                break;
            case "repeat_one":
                playingMode = "single_play";
                PlayerActivity.btnRepeat.setBackgroundResource(R.drawable.ic_single_play);
                break;
            case "single_play":
                playingMode = "shuffle";
                PlayerActivity.btnRepeat.setBackgroundResource(R.drawable.ic_shuffle);
                break;
            default:
                playingMode = "repeat_off";
                PlayerActivity.btnRepeat.setBackgroundResource(R.drawable.ic_repeat_off);
                break;
        }
        updatePlaybackState();
    }


    // Display remaining time
    void showRemainingTime() {
        if (remainingTime) {
            remainingTime = false;
            PlayerActivity.txtTimeStop.setText(createTime(songDuration));
        } else {
            remainingTime = true;
            if (mediaPlayer != null) {
                updateTimeStamp();
            }
        }
    }


    // Update current song name highlights
    void updateSongHighlight() {
        if (MainActivity.getInstance() != null) {
            ((SongListAdapter) MainActivity.getInstance().listView.getAdapter()).refreshFavorites();
        }
        if (FavoriteActivity.getInstance() != null) {
            ((FavSongListAdapter) FavoriteActivity.getInstance().listView.getAdapter()).refreshFavorites();
        }
    }


    // Start / Stop thread
    void updateThread() {
        handler.removeCallbacks(mainThread);
        if (mediaPlayer != null && !onSeekBarTouch && PlayerActivity.isRunning) {
            updateTimeStamp();
            if (mediaPlayer.isPlaying()) {
                handler.postDelayed(mainThread, 1000);
            }
        }
//        System.out.println("updateThread");
    }


    // Thread for Update SeekBar and Current song time
    final Runnable mainThread = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null) {
                updateTimeStamp(); // Update current timing
            }
//            System.out.println("Running...");
            handler.postDelayed(this, 1000);
        }
    };


    // Update time stamp and seekbar
    void updateTimeStamp() {
        PlayerActivity.seekMusic.setProgress(mediaPlayer.getCurrentPosition());
        PlayerActivity.txtTimeStart.setText(createTime(mediaPlayer.getCurrentPosition()));
        if (remainingTime) {
            int timeLeft = songDuration - mediaPlayer.getCurrentPosition();
            PlayerActivity.txtTimeStop.setText(createTime(timeLeft));
        }
//        System.out.println("UpdateTimeStamp");
    }


    // Skip invalid song
    void skipSong() {
        if (mediaPlayer == null) {
            nextSong();
            Toast.makeText(this, "Auto skip to next song", Toast.LENGTH_SHORT).show();
        }
    }


    // Time converter; Milliseconds to hh:mm:ss
    String createTime(int duration) {
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


    // Update metadata
    void updateMetadata() {
        int duration = 0;
        if (mediaPlayer != null) {
            duration = mediaPlayer.getDuration();
        }
        Bitmap bgNotification;
        if (albumArt != null) {
            bgNotification = albumArt;
        } else {
            bgNotification = BitmapFactory.decodeResource(this.getResources(), R.drawable.bg_notification);
        }
        MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, songName)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artistName)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bgNotification) // Background image
                .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, bgNotification)
                .build();
        mediaSession.setMetadata(metadata);
    }


    // Update Playback state
    void updatePlaybackState() {
        if (mediaPlayer == null) {
            return;
        }

        int state;
        if (mediaPlayer.isPlaying()) {
            state = PlaybackStateCompat.STATE_PLAYING;
        } else {
            state = PlaybackStateCompat.STATE_PAUSED;
        }

        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(
                        PlaybackStateCompat.ACTION_PLAY |
                                PlaybackStateCompat.ACTION_PAUSE |
                                PlaybackStateCompat.ACTION_SEEK_TO |
                                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
                .setState(state, mediaPlayer.getCurrentPosition(), 1.0f);
        int icon;
        String modeText;
        switch (playingMode) {
            case "repeat_all":
                icon = R.drawable.ic_repeat_all;
                modeText = "Repeat all";
                break;
            case "repeat_one":
                icon = R.drawable.ic_repeat_one;
                modeText = "repeat one";
                break;
            case "single_play":
                icon = R.drawable.ic_single_play;
                modeText = "Single play";
                break;
            case "shuffle":
                icon = R.drawable.ic_shuffle;
                modeText = "Shuffle";
                break;
            default:
                icon = R.drawable.ic_repeat_off;
                modeText = "Repeat off";
                break;
        }

        stateBuilder.addCustomAction(
                new PlaybackStateCompat.CustomAction.Builder(
                        "ACTION_TOGGLE_MODE",
                        modeText,
                        icon
                ).build()
        );

        stateBuilder.addCustomAction(
                new PlaybackStateCompat.CustomAction.Builder(
                        "ACTION_EXIT",
                        getString(R.string.action_exit),
                        R.drawable.ic_exit_noti
                ).build()
        );
        mediaSession.setPlaybackState(stateBuilder.build());
    }


    // Notification function
    void showNotification() {
        // Create Notification Channel (Required for Android 8 and above)
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Music Player", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Channel for music player notifications");
            channel.setSound(null, null);
            channel.enableVibration(false);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }

        // Notification for Android 11 and above
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            newNotification();
        }
        // Notification for Android 10 or below
        else {
            oldNotification();
        }

    }


    // MediaSession Notification
    void newNotification() {
        // Intent to open PlayerActivity
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_music)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent) // Attach the PendingIntent
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle().setMediaSession(mediaSession.getSessionToken()))
                .build();
        startForeground(NOTIFICATION_ID, notification);
    }


    // MediaStyle Notification
    void oldNotification() {
        // Intent to open PlayerActivity
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Intent for Play/Pause Action
        Intent playIntent = new Intent(this, NotificationReceiver.class).setAction("ACTION_PLAY");
        PendingIntent playPendingIntent = PendingIntent.getBroadcast(this, 0, playIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Intent for Next Action
        Intent nextIntent = new Intent(this, NotificationReceiver.class).setAction("ACTION_NEXT");
        PendingIntent nextPendingIntent = PendingIntent.getBroadcast(this, 1, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Intent for Previous Action
        Intent prevIntent = new Intent(this, NotificationReceiver.class).setAction("ACTION_PREVIOUS");
        PendingIntent prevPendingIntent = PendingIntent.getBroadcast(this, 2, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Intent for Exit Action
        Intent exitIntent = new Intent(this, NotificationReceiver.class).setAction("ACTION_EXIT");
        PendingIntent exitPendingIntent = PendingIntent.getBroadcast(this, 3, exitIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Determine the correct Play/Pause icon
        int playPauseIcon = (mediaPlayer != null && mediaPlayer.isPlaying())
                ? R.drawable.ic_pause
                : R.drawable.ic_play;

        Bitmap bgNotification;
        if (albumArt != null) {
            bgNotification = albumArt;
        } else {
            bgNotification = BitmapFactory.decodeResource(this.getResources(), R.drawable.ic_music_logo);
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_music)
                .setLargeIcon(bgNotification)
                .setContentTitle(songName)
                .setContentText(artistName)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setOngoing(mediaPlayer != null && mediaPlayer.isPlaying())
                .setContentIntent(pendingIntent) // Attach the PendingIntent
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle().setShowActionsInCompactView(0, 1, 2))
                .addAction(R.drawable.ic_notiprev, "Previous", prevPendingIntent)
                .addAction(playPauseIcon, "Play/Pause", playPendingIntent) // Use dynamic icon
                .addAction(R.drawable.ic_notinext, "Next", nextPendingIntent)
                .addAction(R.drawable.ic_exit, "Exit", exitPendingIntent)
                .build();
        startForeground(NOTIFICATION_ID, notification);
    }


    // Cancel notification
    void cancelNotification() {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.cancel(NOTIFICATION_ID);
        stopForeground(true);
    }


    // Exit and release resources
    void exitPlayer() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.reset();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        currentSongUri = null;
        if (PlayerActivity.getInstance() != null) {
            PlayerActivity.getInstance().closePlayerActivity();
        }
        updateSongHighlight();
        cancelNotification();
        handler.removeCallbacks(mainThread);
        handler.removeCallbacks(skipSongRunnable);
        BaseActivity.miniPlayer.setVisibility(View.GONE);
        MainActivity.getInstance().listView.setPadding(0, 0, 0, 0);
        if (FavoriteActivity.getInstance() != null) {
            FavoriteActivity.getInstance().listView.setPadding(0, 0, 0, 0);
        }
        stopSelf();
    }


    // Clear from Recent apps
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        exitPlayer();
        stopSelf();
    }
}
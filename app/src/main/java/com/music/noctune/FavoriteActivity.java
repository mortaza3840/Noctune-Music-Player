package com.music.noctune;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.music.noctune.database.AppDatabase;
import com.music.noctune.database.FavoriteDao;
import com.music.noctune.database.FavoriteSong;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class FavoriteActivity extends BaseActivity {
    ListView listView;
    TextView textView;
    SwipeRefreshLayout swipeRefresh;

    ArrayList<String> favSongUris;
    ArrayList<String> favSongNames;
    ArrayList<String> favArtistNames;

    public static ArrayList<String> songUris;
    public static ArrayList<String> songNames;
    public static ArrayList<String> artistNames;
    private static FavoriteDao favoriteDao;

    public static FavoriteActivity instance;
    public static FavoriteActivity getInstance() {
        return instance;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Background); //Set Background
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorite);
        instance = this;

        listView = findViewById(R.id.listViewSong);
        textView = findViewById(R.id.textView);
        swipeRefresh = findViewById(R.id.swipeRefresh);

        getSupportActionBar().setTitle("Favorites");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        favSongUris = new ArrayList<>();
        favSongNames = new ArrayList<>();
        favArtistNames = new ArrayList<>();

        listView.setOnTouchListener(new View.OnTouchListener() {
            private boolean isScrolling = false;
            private int totalItems;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                totalItems = listView.getCount();
                int listHeight = listView.getHeight();

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (isTouchNearScrollbar(event)) {
                            isScrolling = true;
                            initialTouchY = event.getY();
                            return true; // Start fast scrolling mode
                        }
                        break;

                    case MotionEvent.ACTION_MOVE:
                        if (isScrolling) {
                            float deltaY = event.getY() - initialTouchY;
                            float scrollFraction = event.getY() / listHeight;
                            int newScrollPosition = (int) (totalItems * scrollFraction);

                            listView.setSelection(newScrollPosition);
                            return true;
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                        isScrolling = false; // Stop fast scrolling only when released
                        break;
                }
                return false;
            }

            private boolean isTouchNearScrollbar(MotionEvent event) {
                int scrollbarWidth = 50; // Approximate width of default scrollbar
                int screenWidth = listView.getWidth();
                return event.getX() > (screenWidth - scrollbarWidth);
            }
        });

        swipeRefresh.setEnabled(true);
        swipeRefresh.setColorSchemeResources(R.color.colorPrimary);
        swipeRefresh.setOnRefreshListener(this::refreshSongList);

        favoriteDao = AppDatabase.getInstance(this).favoriteDao();

        getFavorite(); // Fetch favorite songs
        displaySongs();
    }


    // Refresh the music list
    public void refreshSongList() {
        favSongUris.clear();
        favSongNames.clear();
        favArtistNames.clear();
        getFavorite();
        displaySongs();
        new Handler().postDelayed(() -> {
            swipeRefresh.setRefreshing(false); // Stop refresh animation
        }, 1000);
    }


    // Fetch the favorite from database
    public void getFavorite() {
        int i = 0;
        while (i < songUris.size()) {
            if (favoriteDao.getFavoriteByUri(songUris.get(i)) != null) {
                favSongUris.add(songUris.get(i));
                favSongNames.add(songNames.get(i));
                favArtistNames.add(artistNames.get(i));
            }
            i++;
        }
    }


    // Display the songs
    void displaySongs() {
        if (favSongNames.isEmpty()) {
            textView.setText("No favorite song found");
        } else {
            textView.setText("");
        }

        if (Objects.equals(MusicService.source, "Favorite")) {
            updatePosition();
        }

        // Update the list view
        listView.setAdapter(new FavSongListAdapter(this, favSongUris, favSongNames, favArtistNames));

        // Handle song click
        listView.setOnItemClickListener((adapterView, view, position, id) -> {
            if (MusicService.mediaPlayer != null && Objects.equals(favSongUris.get(position), MusicService.currentSongUri)) {
                Intent intent = new Intent(this, PlayerActivity.class);
                MusicService.songUris = favSongUris;
                MusicService.songNames = favSongNames;
                MusicService.artistNames = favArtistNames;
                MusicService.position = position;
                MusicService.source = "Favorite";
                startActivity(intent);
            } else {
                Intent intent = new Intent(this, PlayerActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);

                Intent serviceIntent = new Intent(this, MusicService.class);
                MusicService.songUris = favSongUris;
                MusicService.songNames = favSongNames;
                MusicService.artistNames = favArtistNames;
                MusicService.position = position;
                MusicService.source = "Favorite";
                startService(serviceIntent);
            }
        });

        if (Objects.equals(MusicService.source, "Favorite")) {
            try {
                MusicService.songUris = favSongUris;
                MusicService.songNames = favSongNames;
                MusicService.artistNames = favArtistNames;
            } catch (Exception e) {
                Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show();
            }
        }
    }


    // Update song position after add/remove favorite
    void updatePosition() {
        boolean stopPlayer = true;
        int i = 0;
        while (i < favSongUris.size()) {
            if (Objects.equals(String.valueOf(MusicService.songUri), favSongUris.get(i))) {
                MusicService.position = i;
                stopPlayer = false;
                break;
            }
            i++;
        }

        if (stopPlayer && MusicService.mediaPlayer != null) {
            Toast.makeText(this, "Song has been removed from Favorite", Toast.LENGTH_SHORT).show();
            MusicService.getInstance().exitPlayer();
        }

    }


    // Check favorite
    void checkFavorite() {
        boolean stopPlayer = true;
        List<FavoriteSong> favorites = favoriteDao.getAllFavorites();
        for (FavoriteSong song : favorites) {
            if (Objects.equals(String.valueOf(MusicService.songUri), (song.songUri))) {
                stopPlayer = false;
                break;
            }
        }
        if (stopPlayer && MusicService.mediaPlayer != null) {
            Toast.makeText(this, "Song has been removed from Favorite", Toast.LENGTH_SHORT).show();
            MusicService.getInstance().exitPlayer();
        }
    }


    void updateBottomPadding() {
        int bottomPaddingInDp = 85; // Default
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            bottomPaddingInDp = 75;
        }
        float scale = getResources().getDisplayMetrics().density;
        int bottomPaddingInPx = (int) (bottomPaddingInDp * scale); // Convert dp to px

        listView.setPadding(0, 0, 0, bottomPaddingInPx);
        listView.setClipToPadding(false);
    }


    // Top Back button
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onBackPressed();
        return super.onOptionsItemSelected(item);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        if (Objects.equals(MusicService.source, "Favorite")) {
            refreshSongList();
        }
    }

}
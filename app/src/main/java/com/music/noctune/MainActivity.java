package com.music.noctune;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.ContextThemeWrapper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import kotlin.Triple;

public class MainActivity extends BaseActivity {

    View actionBarView;
    ListView listView;
    TextView textView;
    Menu menu;
    SwipeRefreshLayout swipeRefresh;

    ArrayList<String> songUris = new ArrayList<>();
    ArrayList<String> songNames = new ArrayList<>();
    ArrayList<String> artistNames = new ArrayList<>();

    public static MainActivity instance;
    public static MainActivity getInstance() {
        return instance;
    }

    @SuppressLint({"UnspecifiedRegisterReceiverFlag", "ClickableViewAccessibility"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Background); //Set Background
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        instance = this;

        listView = findViewById(R.id.listViewSong);
        textView = findViewById(R.id.textView);
        swipeRefresh = findViewById(R.id.swipeRefresh);

        IntentFilter filter = new IntentFilter("UPDATE_FAVORITES");

        // Action bar for pop-up menu
        if (getSupportActionBar() != null) {
            actionBarView = findViewById(androidx.appcompat.R.id.action_bar);
            if (actionBarView != null) {
                actionBarView.setOnLongClickListener(v -> {
                    showPopupMenu(actionBarView);
                    return true;
                });
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(favoritesUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(favoritesUpdateReceiver, filter);
        }

        // Fast scrolling
        listView.setOnTouchListener(new View.OnTouchListener() {
            private boolean isScrolling = false;
            private int totalItems;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                totalItems = listView.getCount();

                // Adjust height to exclude paddings
                int listHeight = listView.getHeight() - listView.getPaddingTop() - listView.getPaddingBottom();

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (isTouchNearScrollbar(event)) {
                            isScrolling = true;
                            initialTouchY = event.getY();
                            return true;
                        }
                        break;

                    case MotionEvent.ACTION_MOVE:
                        if (isScrolling) {
                            float adjustedY = event.getY() - listView.getPaddingTop(); // adjust Y for top padding
                            float scrollFraction = adjustedY / listHeight;
                            scrollFraction = Math.max(0, Math.min(1, scrollFraction)); // clamp to [0,1]

                            int newScrollPosition = (int) (totalItems * scrollFraction);
                            listView.setSelection(newScrollPosition);
                            return true;
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                        isScrolling = false;
                        break;
                }
                return false;
            }

            private boolean isTouchNearScrollbar(MotionEvent event) {
                int scrollbarWidth = 50;
                int screenWidth = listView.getWidth();
                return event.getX() > (screenWidth - scrollbarWidth);
            }

        });

        // Swipe to refresh
        swipeRefresh.setColorSchemeResources(R.color.colorPrimary);
        swipeRefresh.setOnRefreshListener(this::refreshSongList);

        requestPermissions(); // Request the permissions
    }


    private final BroadcastReceiver favoritesUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (listView.getAdapter() instanceof SongListAdapter) {
                ((SongListAdapter) listView.getAdapter()).refreshFavorites();
            }
        }
    };


    // Request the permissions
    void requestPermissions() {
        // For Android 13 or above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Dexter.withContext(this)
                    .withPermissions(
                            android.Manifest.permission.READ_MEDIA_AUDIO
                    )
                    .withListener(new MultiplePermissionsListener() {
                        @Override
                        public void onPermissionsChecked(MultiplePermissionsReport report) {
                            if (report.areAllPermissionsGranted()) {
                                displaySongs();
                            } else {
                                Toast.makeText(MainActivity.this, "Permissions denied", Toast.LENGTH_SHORT).show();
                                textView.setText("Go to Settings and\ngive the necessary permissions");
                            }
                        }

                        @Override
                        public void onPermissionRationaleShouldBeShown(List<com.karumi.dexter.listener.PermissionRequest> permissions, PermissionToken token) {
                            token.continuePermissionRequest();
                        }
                    }).check();
        }
        // For Android 12 or below
        else {
            Dexter.withContext(this).withPermissions(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    .withListener(new MultiplePermissionsListener() {
                        @Override
                        public void onPermissionsChecked(MultiplePermissionsReport report) {
                            if (report.areAllPermissionsGranted()) {
                                displaySongs();
                            } else {
                                Toast.makeText(MainActivity.this, "Permissions denied", Toast.LENGTH_SHORT).show();
                                textView.setText("Go to Settings and\ngive the necessary permissions");
                            }
                        }

                        @Override
                        public void onPermissionRationaleShouldBeShown(List<com.karumi.dexter.listener.PermissionRequest> permissions, PermissionToken token) {
                            token.continuePermissionRequest();
                        }
                    }).check();
        }
    }


    // Refresh the music list
    private void refreshSongList() {
        songUris.clear();
        songNames.clear();
        artistNames.clear();
        displaySongs();
        new Handler().postDelayed(() -> {
            swipeRefresh.setRefreshing(false); // Stop refresh animation
        }, 1000);
    }


    // Display the songs
    private void displaySongs() {
        ContentResolver contentResolver = getContentResolver();
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.ARTIST
        };

        Cursor cursor = contentResolver.query(uri, projection, null, null, null);

        if (cursor != null) {
            while (cursor.moveToNext()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID));
                String name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME));
                String artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST));

                if (artist == null || artist.equals("<unknown>")) {
                    artist = "Unknown Artist";
                }

                Uri songUri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));

                songUris.add(songUri.toString());
                songNames.add(name);
                artistNames.add(artist);
            }
            cursor.close();
        } else {
            Toast.makeText(MainActivity.this, "No songs found on device", Toast.LENGTH_SHORT).show();
            return;
        }

        if (songNames.isEmpty()) {
            textView.setText("No songs found");
            return;
        }

        // Pair song names with URIs for sorting
        ArrayList<Triple<String, String, String>> songsData = new ArrayList<>();
        for (int i = 0; i < songNames.size(); i++) {
            songsData.add(new Triple<>(songNames.get(i), songUris.get(i), artistNames.get(i)));
        }

        songsData.sort((s1, s2) -> s1.getFirst().compareToIgnoreCase(s2.getFirst()));

        // Update lists after sorting
        songNames.clear();
        songUris.clear();
        artistNames.clear();
        for (Triple<String, String, String> song : songsData) {
            songNames.add(song.getFirst());
            songUris.add(song.getSecond());
            artistNames.add(song.getThird());
        }

        // Show the list view
        listView.setAdapter(new SongListAdapter(this, songUris, songNames, artistNames));

        // Handle song click
        listView.setOnItemClickListener((adapterView, view, position, id) -> {
            if (MusicService.mediaPlayer != null && Objects.equals(songUris.get(position), MusicService.currentSongUri)) {
                Intent intent = new Intent(this, PlayerActivity.class);
                MusicService.songUris = songUris;
                MusicService.songNames = songNames;
                MusicService.artistNames = artistNames;
                MusicService.position = position;
                MusicService.source = "Main";
                startActivity(intent);
            } else {
                // Launch PlayerActivity
                Intent intent = new Intent(this, PlayerActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                //Launch MusicService
                Intent serviceIntent = new Intent(this, MusicService.class);
                MusicService.songUris = songUris; // Pass song list to the service
                MusicService.songNames = songNames;
                MusicService.artistNames = artistNames; // Pass artist names to MusicService
                MusicService.position = position;
                MusicService.source = "Main";
                // Start the service
                startService(serviceIntent);
            }
        });

    }


    // Search function
    public boolean onCreateOptionsMenu(Menu menu) {
        this.menu = menu; // Store the menu instance

        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);

        // Menu option
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            MenuItem favoriteItem = menu.add("Favorites");
            favoriteItem.setOnMenuItemClickListener(item -> {
                launchFavorite();
                return true;
            });
        }

        // Find the search item
        MenuItem searchItem = menu.findItem(R.id.action_search);

        // Get the SearchView and set the searchable configuration
        SearchView searchView = (SearchView) searchItem.getActionView();

        searchView.setMaxWidth(Integer.MAX_VALUE);

        searchView.setOnSearchClickListener(view -> {
            // Disable swipe-to-refresh when search is active
            swipeRefresh.setEnabled(false);
        });

        // Set a query text listener
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                // Handle search query submission
                // Perform the search logic here
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                // Filter the song list based on the input text
                ArrayList<String> filteredSongNames = new ArrayList<>();
                ArrayList<String> filteredArtistNames = new ArrayList<>();
                ArrayList<String> filteredSongUris = new ArrayList<>();
                ArrayList<Integer> filteredIndices = new ArrayList<>(); // To track indices in the original list

                if (MusicService.getInstance() != null) {
                    MusicService.getInstance().updateSongHighlight();
                }

                for (int i = 0; i < songNames.size(); i++) {
                    if (songNames.get(i).toLowerCase().contains(newText.toLowerCase()) || (artistNames.get(i).toLowerCase().contains(newText.toLowerCase()))) {
                        filteredSongNames.add(songNames.get(i));
                        filteredArtistNames.add(artistNames.get(i));
                        filteredSongUris.add(songUris.get(i));
                        filteredIndices.add(i);// Store the original index
                    }
                }

                // Update the list view with the filtered results
                SongListAdapter adapter = new SongListAdapter(MainActivity.this, filteredSongUris, filteredSongNames, filteredArtistNames);
                listView.setAdapter(adapter);

                // Update item click listener for the list
                listView.setOnItemClickListener((adapterView, view, position, id) -> {
                    int originalIndex = filteredIndices.get(position);

                    if (MusicService.mediaPlayer != null && Objects.equals(filteredSongUris.get(position), MusicService.currentSongUri)) {
                        Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
                        MusicService.songUris = songUris;
                        MusicService.songNames = songNames;
                        MusicService.artistNames = artistNames;
                        MusicService.position = originalIndex;
                        MusicService.source = "Main";
                        startActivity(intent);
                    } else {
                        // Launch PlayerActivity
                        Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);

                        Intent serviceIntent = new Intent(MainActivity.this, MusicService.class);
                        MusicService.songUris = songUris; // Pass song list to the service
                        MusicService.songNames = songNames;
                        MusicService.artistNames = artistNames; // Pass artist names to MusicService
                        MusicService.position = originalIndex;
                        MusicService.source = "Main";
                        startService(serviceIntent);
                    }
                });

                // Check if songs are present
                if (filteredSongNames.isEmpty()) {
                    textView.setText("No songs found");
                } else {
                    textView.setText("");
                }
                return true;
            }
        });

        searchView.setOnCloseListener(() -> {
            swipeRefresh.setEnabled(true);
            return false;
        });

        return true;
    }


    // Update bottom padding of ListView when Mini player is visible
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


    // Open favorite activity
    void launchFavorite() {
        Intent intent = new Intent(this, FavoriteActivity.class);
        FavoriteActivity.songUris = songUris;
        FavoriteActivity.songNames = songNames;
        FavoriteActivity.artistNames = artistNames;
        startActivity(intent);
    }


    // When click on the back button
    @Override
    public void onBackPressed() {
        MenuItem searchItem = menu.findItem(R.id.action_search); // Get search item
        SearchView searchView = (SearchView) searchItem.getActionView();

        if (!searchView.isIconified()) {
            searchView.setIconified(true); // Collapse search view
            searchView.onActionViewCollapsed(); // Ensure full reset
            swipeRefresh.setEnabled(true);
        } else {
            super.onBackPressed(); // If search is already closed, exit activity
        }
    }


    // Popup menu
    private void showPopupMenu(View anchorView) {
        ContextThemeWrapper contextWrapper = new ContextThemeWrapper(this, R.style.CustomPopupMenu);
        PopupMenu popupMenu = new PopupMenu(contextWrapper, anchorView);
        SpannableString headerTitle = new SpannableString("MADE BY");
        headerTitle.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), 0, headerTitle.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        popupMenu.getMenu().add(headerTitle);
        popupMenu.getMenu().add("Mrinmoy Choudhury \uD83D\uDDFF");
        popupMenu.getMenu().add("Golam Ahmed Mortaza \uD83D\uDC26");
        popupMenu.show();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (MusicService.mediaPlayer != null && !MusicService.mediaPlayer.isPlaying()) {
            MusicService.getInstance().exitPlayer();
        }
        unregisterReceiver(favoritesUpdateReceiver);
    }

}
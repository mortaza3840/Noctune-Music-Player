package com.music.noctune;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.shape.ShapeAppearanceModel;

import com.music.noctune.database.AppDatabase;
import com.music.noctune.database.FavoriteDao;
import com.music.noctune.database.FavoriteSong;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class SongListAdapter extends android.widget.ArrayAdapter<String> {

    private final ArrayList<String> songNames;
    private final ArrayList<String> songUris;
    private final ArrayList<String> artistNames;
    private final Context context;
    private final Map<String, Bitmap> albumArtCache = new HashMap<>(); // Cache for album art
    private final ColorStateList colorStateList;
    private final ColorStateList colorStateListGrey;
    private final int primaryColor;
    private final ShapeAppearanceModel shapeModel;
    private Set<String> favoriteSongs;
    private FavoriteDao favoriteDao;
    private Bitmap defaultArt;


    @SuppressLint("ResourceType")
    public SongListAdapter(@NonNull Context context, ArrayList<String> songUris, ArrayList<String> songNames, ArrayList<String> artistNames) {
        super(context, R.layout.list_item, songNames);
        this.context = context;
        this.songUris = songUris;
        this.songNames = songNames;
        this.artistNames = artistNames;
        this.favoriteDao = AppDatabase.getInstance(context).favoriteDao();
        loadFavorites();
        this.colorStateList = ContextCompat.getColorStateList(context, R.drawable.button_color_selector_primary);
        this.colorStateListGrey = ContextCompat.getColorStateList(context, R.drawable.button_color_selector_grey);
        this.primaryColor = ContextCompat.getColor(context, R.color.colorPrimary);
        this.shapeModel = ShapeAppearanceModel.builder().setAllCornerSizes(5f).build();
        this.defaultArt = resizeBitmap(BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_music_logo), 256, 256); // Use a default image
    }


    private static class ViewHolder {
        TextView txtSongName;
        ImageView favoriteIcon;
        TextView txtArtistName;
        ShapeableImageView imageView;
    }


    private void loadFavorites() {
        List<FavoriteSong> favorites = favoriteDao.getAllFavorites();
        favoriteSongs = new HashSet<>();
        for (FavoriteSong song : favorites) {
            favoriteSongs.add(song.songUri);
        }
    }


    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            LayoutInflater inflater = LayoutInflater.from(context);
            convertView = inflater.inflate(R.layout.list_item, parent, false);
            holder = new ViewHolder();
            holder.txtSongName = convertView.findViewById(R.id.txtsongname);
            holder.txtArtistName = convertView.findViewById(R.id.txtartistname);
            holder.imageView = convertView.findViewById(R.id.imgsong);
            holder.favoriteIcon = convertView.findViewById(R.id.favoriteIcon);
            holder.imageView.setShapeAppearanceModel(shapeModel);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        String songName = songNames.get(position);
        String artistName = artistNames.get(position);
        Uri songUri = Uri.parse(songUris.get(position));


        // Set song name & artist name
        holder.txtSongName.setText(songName);
        if (Objects.equals(artistName, "Unknown Artist")) {
            holder.txtArtistName.setVisibility(View.GONE);
        } else {
            holder.txtArtistName.setVisibility(View.VISIBLE);
            holder.txtArtistName.setText(artistName);
        }

        // Favorite icon click
        holder.favoriteIcon.setOnClickListener(v -> {
            if (favoriteSongs.contains(String.valueOf(songUri))) {
                favoriteDao.deleteByUri(songUris.get(position));
            } else {
                favoriteDao.insert(new FavoriteSong(songUris.get(position)));
            }
            refreshFavorites();

            if (FavoriteActivity.getInstance() != null && Objects.equals(MusicService.source, "Favorite")) {
                FavoriteActivity.getInstance().refreshSongList();
            }
        });

        // Hold Favorite icon
        holder.favoriteIcon.setOnLongClickListener(v -> {
            MainActivity.getInstance().launchFavorite();
            return true;
        });

        // Highlight currently playing song
        if (songUris.get(position).equals(MusicService.currentSongUri)) {
            holder.txtSongName.setTextColor(this.primaryColor);
        }
        else {
            holder.txtSongName.setTextColor(this.colorStateList);
        }

        // Highlight artist currently playing song
        if (songUris.get(position).equals(MusicService.currentSongUri)) {
            holder.txtArtistName.setTextColor(this.primaryColor);
        } else {
            holder.txtArtistName.setTextColor(this.colorStateListGrey);
        }

        // Load album art (use cache if available)
        if (albumArtCache.containsKey(songUri.toString())) {
            holder.imageView.setImageBitmap(albumArtCache.get(songUri.toString()));
        } else {
            holder.imageView.setTag(songUri.toString()); // Set a tag to track the correct song
            holder.imageView.setImageBitmap(defaultArt);
            new Thread(() -> {
                Bitmap albumArt = getAlbumArt(songUri);
                albumArtCache.put(songUri.toString(), albumArt);

                ((android.app.Activity) context).runOnUiThread(() -> {
                    if (holder.imageView.getTag().equals(songUri.toString())) { // Ensure it's still the right item
                        holder.imageView.setImageBitmap(albumArt);
                    }
                });
            }).start();
        }

        if (favoriteSongs.contains(songUris.get(position))) {
            holder.favoriteIcon.setImageResource(R.drawable.ic_favorite_list);
        } else {
            holder.favoriteIcon.setImageResource(R.drawable.ic_not_favorite_list);
        }

        return convertView;
    }


    // Refresh the list item
    public void refreshFavorites() {
        loadFavorites();
        notifyDataSetChanged();
    }


    // Fetch artist name from song metadata
    private Bitmap getAlbumArt(Uri uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            byte[] art = retriever.getEmbeddedPicture();
            if (art != null) {
                Bitmap originalBitmap = BitmapFactory.decodeByteArray(art, 0, art.length);
                return resizeBitmap(originalBitmap, 256, 256);
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
        return null;
    }


    // Resize Bitmap with keeping the Aspect ratio
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

}

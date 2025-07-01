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
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.shape.ShapeAppearanceModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class FavSongListAdapter extends android.widget.ArrayAdapter<String> {

    private final ArrayList<String> songNames;
    public static ArrayList<String> songUris;
    public static ArrayList<String> artistNames;
    private final Context context;
    private final Map<String, Bitmap> albumArtCache = new HashMap<>();
    private final ColorStateList colorStateList;
    private final ColorStateList colorStateListGrey;
    private final int primaryColor;
    private final ShapeAppearanceModel shapeModel;
    private Bitmap defaultArt;


    @SuppressLint("ResourceType")
    public FavSongListAdapter(@NonNull Context context, ArrayList<String> songUris, ArrayList<String> songNames, ArrayList<String> artistNames) {
        super(context, R.layout.list_item_favorite, songNames);
        this.context = context;
        this.songUris = songUris;
        this.songNames = songNames;
        this.artistNames = artistNames;
        this.colorStateList = ContextCompat.getColorStateList(context, R.drawable.button_color_selector_primary);
        this.colorStateListGrey = ContextCompat.getColorStateList(context, R.drawable.button_color_selector_grey);
        this.primaryColor = ContextCompat.getColor(context, R.color.colorPrimary);
        this.shapeModel = ShapeAppearanceModel.builder().setAllCornerSizes(5f).build();
        this.defaultArt = resizeBitmap(BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_music_logo), 256, 256); // Use a default image
    }


    private static class ViewHolder {
        TextView txtSongName;
        TextView txtArtistName;
        ShapeableImageView imageView;
    }


    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            LayoutInflater inflater = LayoutInflater.from(context);
            convertView = inflater.inflate(R.layout.list_item_favorite, parent, false);
            holder = new ViewHolder();
            holder.txtSongName = convertView.findViewById(R.id.txtsongname);
            holder.txtArtistName = convertView.findViewById(R.id.txtartistname);
            holder.imageView = convertView.findViewById(R.id.imgsong);
            holder.imageView.setShapeAppearanceModel(shapeModel);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        String songName = songNames.get(position);
        String artistName = artistNames.get(position);
        Uri songUri = Uri.parse(songUris.get(position));

        // Set song name
        holder.txtSongName.setText(songName);
        if (Objects.equals(artistName, "Unknown Artist")) {
            holder.txtArtistName.setVisibility(View.GONE);
        } else {
            holder.txtArtistName.setVisibility(View.VISIBLE);
            holder.txtArtistName.setText(artistName);
        }

        // Highlight currently playing song
        if (songUris.get(position).equals(MusicService.currentSongUri)) {
            holder.txtSongName.setTextColor(this.primaryColor);
        } else {
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

        return convertView;
    }


    // Refresh list item
    public void refreshFavorites() {
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

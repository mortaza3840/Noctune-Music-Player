package com.music.noctune.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "favorites")
public class FavoriteSong {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String songUri;

    public FavoriteSong(String songUri) {
        this.songUri = songUri;
    }
}
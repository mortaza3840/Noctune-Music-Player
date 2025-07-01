package com.music.noctune.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(FavoriteSong song);

    @Query("DELETE FROM favorites WHERE songUri = :uri")
    void deleteByUri(String uri);

    @Query("SELECT * FROM favorites WHERE songUri = :uri LIMIT 1")
    FavoriteSong getFavoriteByUri(String uri);

    @Query("SELECT * FROM favorites")
    List<FavoriteSong> getAllFavorites();
}
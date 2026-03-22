package com.photomatch.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "favorites")
public class FavoritePhoto {
    @PrimaryKey(autoGenerate = true)
    public int    id;
    public String originalBase64;   // resp.originalB64 (null for burst photos)
    public String correctedBase64;  // resp.finalB64
    public String retrieved;        // resp.retrieved — used for dedup lookup
    public long   timestamp;
    public String improvements;     // JSON map of defect scores + metadata
}

package com.music.noctune;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class NotificationReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action != null) {
            switch (action) {
                case "ACTION_PLAY":
                    if (MusicService.mediaPlayer != null && MusicService.mediaPlayer.isPlaying()) {
                        MusicService.getInstance().pauseSong();
                    } else {
                        MusicService.getInstance().playSong();
                    }
                    break;
                case "ACTION_NEXT":
                    MusicService.getInstance().nextSong();
                    break;
                case "ACTION_PREVIOUS":
                    MusicService.getInstance().previousSong();
                    break;
                case "ACTION_EXIT":
                    MusicService.getInstance().exitPlayer();
                    break;
            }
        }
    }
}
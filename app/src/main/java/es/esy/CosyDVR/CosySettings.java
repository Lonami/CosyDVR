package es.esy.CosyDVR;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;

public class CosySettings {
    private final Context ctx;
    private final SharedPreferences prefs;

    public CosySettings(Context context) {
        ctx = context;
        prefs = PreferenceManager.getDefaultSharedPreferences(ctx);

    }

    boolean getAutoStart() {
        return prefs.getBoolean("auto_start_recording", false);
    }

    boolean getReverseLandscape() {
        return prefs.getBoolean("reverse_landscape", false);
    }

    int getMaxVideoBitRate() {
        return prefs.getInt("video_bitrate", 5000000);
    }

    int getVideoWidth() {
        return prefs.getInt("video_width", 1280);
    }

    int getVideoHeight() {
        return prefs.getInt("video_height", 720);
    }

    int getVideoFrameRate() {
        return prefs.getInt("video_frame_rate", 30);
    }

    int getTimeLapseFactor() {
        return 1;
    }

    int getMaxVideoDuration() {
        return prefs.getInt("video_duration", 600000);
    }

    int getMaxTempFolderSize() {
        return prefs.getInt("max_temp_folder_size", 600000);
    }

    int getMinFreeSpace() {
        return prefs.getInt("min_free_space", 600000);
    }

    String getSdCardPath() {
        return prefs.getString("sd_card_path", Environment
                .getExternalStorageDirectory().getAbsolutePath());
    }
}

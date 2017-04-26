package io.github.lonamiwebs.hidrok;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;

class HidrokSettings {
    private final SharedPreferences prefs;

    HidrokSettings(final Context context) {
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    boolean getAutoStart() {
        return prefs.getBoolean("auto_start_recording", false);
    }

    boolean getReverseLandscape() {
        return prefs.getBoolean("reverse_landscape", false);
    }

    int getMaxVideoBitRate() {
        return getInt("video_bitrate", 5000000);
    }

    int getVideoWidth() {
        return getInt("video_width", 1280);
    }

    int getVideoHeight() {
        return getInt("video_height", 720);
    }

    int getVideoFrameRate() {
        return getInt("video_frame_rate", 30);
    }

    int getMaxVideoDuration() {
        return getInt("video_duration", 600000);
    }

    int getMaxTempFolderSize() {
        return getInt("max_temp_folder_size", 600000);
    }

    int getMinFreeSpace() {
        return getInt("min_free_space", 600000);
    }

    String getSdCardPath() {
        return prefs.getString("sd_card_path", Environment
                .getExternalStorageDirectory().getAbsolutePath());
    }

    private int getInt(String name, int defaultValue) {
        String value = prefs.getString(name, null);
        return value == null ? defaultValue : Integer.valueOf(value);
    }
}

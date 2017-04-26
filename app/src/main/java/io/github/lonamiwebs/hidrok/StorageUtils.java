// Original: stackoverflow.com/q/5694933
package io.github.lonamiwebs.hidrok;

import java.io.FileReader;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashSet;
import java.util.StringTokenizer;
import android.os.Environment;
import java.io.FileNotFoundException;
import java.io.IOException;

class StorageUtils {
    static class StorageInfo {
        final String path;
        final boolean internal;
        final boolean readonly;
        final int displayNumber;

        StorageInfo(String path, boolean internal, boolean readonly, int displayNumber) {
            this.path = path;
            this.internal = internal;
            this.readonly = readonly;
            this.displayNumber = displayNumber;
        }

        String getDisplayName() {
            StringBuilder sb = new StringBuilder();
            if (internal) {
                sb.append("Internal SD card" + " [")
                        .append(path)
                        .append("]");
            } else {
                sb.append("SD card ")
                        .append(displayNumber)
                        .append(" [")
                        .append(path)
                        .append("]");
            }
            if (readonly) {
                sb.append(" (Read only)");
            }
            return sb.toString();
        }
    }

    static List<StorageInfo> getStorageList() {
        List<StorageInfo> list = new ArrayList<StorageInfo>();

        String defPath = Environment.getExternalStorageDirectory().getPath();
        String defPathState = Environment.getExternalStorageState();

        boolean defPathInternal = !Environment.isExternalStorageRemovable();
        boolean defPathAvailable =
                defPathState.equals(Environment.MEDIA_MOUNTED) ||
                        defPathState.equals(Environment.MEDIA_MOUNTED_READ_ONLY);
        boolean defPathReadonly =
                Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED_READ_ONLY);

        BufferedReader reader = null;
        try {
            HashSet<String> paths = new HashSet<String>();
            reader = new BufferedReader(new FileReader("/proc/mounts"));
            String line;
            int curDisplayNumber = 1;
            while ((line = reader.readLine()) != null) {
                if (line.contains("vfat") || line.contains("/mnt")) {
                    StringTokenizer tokens = new StringTokenizer(line, " ");
                    tokens.nextToken(); // Device
                    String mountPoint = tokens.nextToken(); // Mount point
                    if (paths.contains(mountPoint))
                        continue;

                    tokens.nextToken(); // File system
                    List<String> flags = Arrays.asList(tokens.nextToken().split(",")); //flags
                    boolean readonly = flags.contains("ro");

                    if (mountPoint.equals(defPath)) {
                        paths.add(defPath);
                        list.add(0, new StorageInfo(defPath, defPathInternal, readonly, 0));
                    } else if (line.contains("/dev/block/vold")) {
                        if (!line.contains("/mnt/secure")
                            && !line.contains("/mnt/asec")
                            && !line.contains("/mnt/obb")
                            && !line.contains("/dev/mapper")
                            && !line.contains("tmpfs")) {
                            paths.add(mountPoint);
                            list.add(new StorageInfo(mountPoint, false, readonly, curDisplayNumber++));
                        }
                    }
                }
            }

            if (!paths.contains(defPath) && defPathAvailable)
                list.add(0, new StorageInfo(defPath, defPathInternal, defPathReadonly, -1));

        } catch (FileNotFoundException ex) {
            ex.printStackTrace();
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
        return list;
    }    
}

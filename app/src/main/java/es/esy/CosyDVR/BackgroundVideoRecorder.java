package es.esy.CosyDVR;

import android.app.PendingIntent;
import android.app.Service;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.Gravity;
import android.view.SurfaceView;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.media.AudioManager;
import android.os.Environment;
import android.text.format.DateFormat;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Binder;
import android.os.Message;
import android.os.SystemClock;

import java.util.Date;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Timer;
import java.util.TimerTask;
import java.io.File;
import java.lang.String;
//import java.net.InetAddress;
//import java.net.Socket;
//import java.net.UnknownHostException;

import android.os.PowerManager;
import android.widget.Toast;

public class BackgroundVideoRecorder extends Service implements
        SurfaceHolder.Callback, MediaRecorder.OnInfoListener {
    // CONSTANTS-OPTIONS
    public long MAX_TEMP_FOLDER_SIZE = 10000000;
    public long MIN_FREE_SPACE = 1000000;
    public int MAX_VIDEO_DURATION = 600000;
    public int VIDEO_WIDTH = 1280;// 1920;
    public int VIDEO_HEIGHT = 720;// 1080;
    public int VIDEO_FRAME_RATE = 30;
    public int TIME_LAPSE_FACTOR = 1;
    public int MAX_VIDEO_BIT_RATE = 5000000;
    // public int MAX_VIDEO_BIT_RATE = 256000; //=for streaming;
    public int REFRESH_TIME = 1000;
    public String VIDEO_FILE_EXT = ".mp4";
    // public int AUDIO_SOURCE = CAMERA;
    public String SD_CARD_PATH = Environment.getExternalStorageDirectory()
            .getAbsolutePath();
    //~ public String BASE_FOLDER = "/CosyDVR";
    public String BASE_FOLDER = "/Android/data/es.esy.CosyDVR/files"; //possible fix for KitKat
    public String FAV_FOLDER = "/fav/";
    public String TEMP_FOLDER = "/temp/";
    /*for KitKat we can use something like:
    * final File[] dirs = context.getExternalFilesDirs(null); //null means default type
    * //find a dir that has most of the space and save using StatFs
    */
    public boolean AUTO_START = false;
    public boolean REVERSE_ORIENTATION = false;

    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();
    private WindowManager windowManager = null;
    private SurfaceView surfaceView = null;
    private Camera camera = null;
    private MediaRecorder mediaRecorder = null;
    private boolean isRecording = false;
    private int focusMode = 0;
    private int sceneMode = 0;
    private int flashMode = 0;
    private int timeLapseMode = 0;
    private int zoomFactor = 0;
    private String currentFile = null;

    private SurfaceHolder mSurfaceHolder = null;
    private PowerManager.WakeLock mWakeLock = null;
    private Timer mTimer = null;
    private TimerTask mTimerTask = null;

    public TextView mTextView = null;
    public TextView mBatteryView = null;
    public long mSrtCounter = 0;
    public Handler mHandler = null;

    private long mSrtBegin = 0;
    private long mNewFileBegin = 0;

    // private List<String> mFocusModes;
    private String[] mFocusModes = { Parameters.FOCUS_MODE_INFINITY,
            Parameters.FOCUS_MODE_CONTINUOUS_VIDEO, Parameters.FOCUS_MODE_AUTO,
            Parameters.FOCUS_MODE_MACRO, Parameters.FOCUS_MODE_EDOF, };

    private String[] mSceneModes = { Parameters.SCENE_MODE_AUTO,
            Parameters.SCENE_MODE_NIGHT, };


    private String[] mFlashModes = { Parameters.FLASH_MODE_OFF,
            Parameters.FLASH_MODE_TORCH, };

    private CosySettings settings;

    // some troubles with video files @SuppressLint("HandlerLeak")
    private final class HandlerExtension extends Handler {
        public void handleMessage(Message msg) {
            if (!isRecording) {
                return;
            }
            String srt = "";
            Date datetime = new Date();
            long tick = (mSrtBegin - mNewFileBegin)/TIME_LAPSE_FACTOR; // relative srt text begin/
            // i.e. prev tick time
            int hour = (int) (tick / (1000 * 60 * 60));
            int min = (int) (tick % (1000 * 60 * 60) / (1000 * 60));
            int sec = (int) (tick % (1000 * 60) / (1000));
            int mil = (int) (tick % (1000));
            srt = srt
                    + String.format("%d\n%02d:%02d:%02d,%03d --> ",
                    mSrtCounter, hour, min, sec, mil);

            mSrtBegin = SystemClock.elapsedRealtime();
            tick = (mSrtBegin - mNewFileBegin)/TIME_LAPSE_FACTOR; // relative srt text end. i.e.
            // this tick time
            hour = (int) (tick / (1000 * 60 * 60));
            min = (int) (tick % (1000 * 60 * 60) / (1000 * 60));
            sec = (int) (tick % (1000 * 60) / (1000));
            mil = (int) (tick % (1000));
            srt = srt
                    + String.format("%02d:%02d:%02d,%03d\n", hour, min, sec,
                    mil);
            srt = srt
                    + DateFormat.format("yyyy-MM-dd_kk-mm-ss",
                    datetime.getTime()).toString() + "\n";

            mTextView.setText(srt);

            int bat = getBatteryLevel(getApplicationContext());
            if (bat >= 0) {
                mBatteryView.setText(String.valueOf(bat)+"%");
            } else {
                mBatteryView.setText("");
            }
        }
    }

    /**
     * Class used for the client Binder. Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        BackgroundVideoRecorder getService() {
            // Return this instance of LocalService so clients can call public
            // methods
            return BackgroundVideoRecorder.this;
        }
    }

    @Override
    public void onCreate() {
        settings = new CosySettings(this);

        AUTO_START = settings.getAutoStart();
        REVERSE_ORIENTATION = settings.getReverseLandscape();
        SD_CARD_PATH = settings.getSdCardPath();

        // Start foreground service to avoid unexpected kill

        Intent myIntent = new Intent(this, CosyDVR.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                myIntent, Intent.FLAG_ACTIVITY_NEW_TASK);

        Notification notification = new Notification.Builder(this)
                .setContentTitle("CosyDVR Background Recorder Service")
                .setContentText("") .setSmallIcon(R.drawable.cosydvricon)
                .setContentIntent(pendingIntent) .build();
        startForeground(1, notification);

        // Create new SurfaceView, set its size to 1x1, move it to the top left
        // corner and set this service as a callback
        windowManager = (WindowManager) this
                .getSystemService(Context.WINDOW_SERVICE);
        surfaceView = new SurfaceView(this);
        LayoutParams layoutParams = new WindowManager.LayoutParams(
                // WindowManager.LayoutParams.WRAP_CONTENT,
                // WindowManager.LayoutParams.WRAP_CONTENT,
                1, 1, WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);

        layoutParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
        windowManager.addView(surfaceView, layoutParams);

        mTextView = new TextView(this);
        layoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        layoutParams.gravity = Gravity.START | Gravity.TOP;
        windowManager.addView(mTextView, layoutParams);
        mTextView.setTextColor(Color.parseColor("#FFFFFF"));
        mTextView.setShadowLayer(5, 0, 0, Color.parseColor("#000000"));
        mTextView.setText("--");

        mBatteryView = new TextView(this);
        layoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        layoutParams.gravity = Gravity.CENTER;
        windowManager.addView(mBatteryView, layoutParams);
        mBatteryView.setTextColor(Color.parseColor("#FFFFFF"));
        mBatteryView.setShadowLayer(5, 0, 0, Color.parseColor("#000000"));
        mBatteryView.setTextSize(80);
        mBatteryView.setText("");

        surfaceView.getHolder().addCallback(this);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "CosyDVRWakeLock");

        mHandler = new HandlerExtension();
    }

    // Method called right after Surface created (initializing and starting
    // MediaRecorder)
    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        mSurfaceHolder = surfaceHolder;
        if (AUTO_START) {
            StartRecording();
        }
    }

    public int getFocusMode() {
        return focusMode;
    }

    public boolean isRecording() {
        return isRecording;
    }

    public void StopRecording() {
        if (isRecording) {
            Stop();
            ResetReleaseLock();
            mTimer.cancel();
            mTimer.purge();
            mTimer = null;
            mTimerTask.cancel();
            mTimerTask = null;

            if (currentFile != null) {
                File tmpFile = new File(SD_CARD_PATH + BASE_FOLDER + TEMP_FOLDER // "/CosyDVR/temp/"
                        + currentFile + VIDEO_FILE_EXT);
                File favFile = new File(SD_CARD_PATH + BASE_FOLDER + FAV_FOLDER // "/CosyDVR/fav/"
                        + currentFile + VIDEO_FILE_EXT);
                if (!tmpFile.renameTo(favFile))
                    Log.w("BackgroundVideoRecorder", "Could not rename "+tmpFile);
            }
            isRecording = false;
        }
    }

    public void UpdateLayoutInterface() {
        Intent intent = new Intent();
        intent.setAction("es.esy.CosyDVR.updateInterface");
        sendBroadcast(intent);
    }

    public void setMute(boolean mute) {
        AudioManager manager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        manager.setStreamSolo(AudioManager.STREAM_SYSTEM, mute);
        manager.setStreamMute(AudioManager.STREAM_SYSTEM, mute);
    }

    public void StartRecording() {
		/* Rereading preferences */
		MAX_VIDEO_BIT_RATE = settings.getMaxVideoBitRate();
        VIDEO_WIDTH = settings.getVideoWidth();
        VIDEO_HEIGHT = settings.getVideoHeight();
        VIDEO_FRAME_RATE = settings.getVideoFrameRate();
        TIME_LAPSE_FACTOR = settings.getTimeLapseFactor();
        MAX_VIDEO_DURATION = settings.getMaxVideoDuration();
        MAX_TEMP_FOLDER_SIZE = settings.getMaxTempFolderSize();
        MIN_FREE_SPACE = settings.getMinFreeSpace();
        SD_CARD_PATH = settings.getSdCardPath();

        // Create temp and favourite folders
        File mFolder = new File(SD_CARD_PATH + BASE_FOLDER + TEMP_FOLDER); //"/CosyDVR/temp/");
        if (!mFolder.exists()) {
            if (!mFolder.mkdirs())
                Log.w("BackgroundVideoRecorder", "Could not create "+mFolder);
        }
        mFolder = new File(SD_CARD_PATH + BASE_FOLDER + FAV_FOLDER); //"/CosyDVR/fav/");
        if (!mFolder.exists()) {
            if (!mFolder.mkdirs())
                Log.w("BackgroundVideoRecorder", "Could not create "+mFolder);
        }

        // First of all make sure we have enough free space
        freeSpace();

		// Start
        OpenUnlockPrepareStart();
        applyCameraParameters();
        mSrtCounter = 0;

        mNewFileBegin = SystemClock.elapsedRealtime();
        mSrtBegin = mNewFileBegin;

        mTimer = new Timer();
        mTimerTask = new TimerTask() {
            @Override
            public void run() {
                mSrtCounter++;
                mHandler.obtainMessage(1).sendToTarget();
            }
        };
        mTimer.scheduleAtFixedRate(mTimerTask, 0, REFRESH_TIME * TIME_LAPSE_FACTOR);
        UpdateLayoutInterface();
    }

    private void Stop() {
        if (isRecording) {
            mediaRecorder.stop();
        }
    }

    private void ResetReleaseLock() {
        if (isRecording) {
            mediaRecorder.reset();
            mediaRecorder.release();

            camera.lock();
            camera.release();
            mWakeLock.release();
        }
    }

    private void OpenUnlockPrepareStart() {
        if (!isRecording) {
            mWakeLock.acquire();
            try {
                camera = Camera.open(/* CameraInfo.CAMERA_FACING_BACK */);
                if(REVERSE_ORIENTATION) {
                    camera.setDisplayOrientation(180);
                } else {
                    camera.setDisplayOrientation(0);
                }
                mediaRecorder = new MediaRecorder();
                camera.unlock();

                // mediaRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());
                mediaRecorder.setCamera(camera);

                // Step 2: Set sources
                mediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
                mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

                // Step 3: Set a CamcorderProfile (requires API Level 8 or
                // higher)
                // mediaRecorder.setProfile(CamcorderProfile
                // .get(CamcorderProfile.QUALITY_HIGH));
                mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                mediaRecorder.setAudioEncoder(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH).audioCodec);// MediaRecorder.AudioEncoder.HE_AAC
                mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);

                mediaRecorder.setVideoEncodingBitRate(this.MAX_VIDEO_BIT_RATE);
                mediaRecorder.setVideoSize(this.VIDEO_WIDTH, this.VIDEO_HEIGHT);// 640x480,800x480
                mediaRecorder.setVideoFrameRate(this.VIDEO_FRAME_RATE);
                if (this.TIME_LAPSE_FACTOR > 1) {
                    mediaRecorder.setCaptureRate(1.0 * this.VIDEO_FRAME_RATE / this.TIME_LAPSE_FACTOR);
                }
                currentFile = DateFormat.format("yyyy-MM-dd_kk-mm-ss",
                        new Date().getTime()).toString();

                // If we write to file
                mediaRecorder.setOutputFile(SD_CARD_PATH + BASE_FOLDER + TEMP_FOLDER //"/CosyDVR/temp/"
                        + currentFile + VIDEO_FILE_EXT);

                // Step 4: Set the preview output
                mediaRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());
                // Step 5: Duration and listener
                mediaRecorder.setMaxDuration(this.MAX_VIDEO_DURATION);
                mediaRecorder.setMaxFileSize(0); // 0 - no limit
                mediaRecorder.setOnInfoListener(this);
                if(REVERSE_ORIENTATION) {
                    mediaRecorder.setOrientationHint(180);
                } else {
                    mediaRecorder.setOrientationHint(0);
                }

                mediaRecorder.prepare();
                mediaRecorder.start();
                isRecording = true;
            } catch (Exception e) {
                isRecording = true;
                ResetReleaseLock();
            }
        }
    }

    public void freeSpace() {
        File dir = new File(SD_CARD_PATH + BASE_FOLDER + TEMP_FOLDER); //"/CosyDVR/temp/");
        File[] fileList = dir.listFiles();
        Arrays.sort(fileList, new Comparator<File>() {
            public int compare(File f1, File f2) {
                return Long.valueOf(f2.lastModified()).compareTo(
                        f1.lastModified());
            }
        });
        long totalSize = 0; // in kB
        int i;
        for (i = 0; i < fileList.length; i++) {
            totalSize += fileList[i].length() / 1024;
        }
        i = fileList.length - 1;
        while (i > 0
                && (totalSize > this.MAX_TEMP_FOLDER_SIZE
                || dir.getFreeSpace() < this.MIN_FREE_SPACE)) {
            totalSize -= fileList[i].length() / 1024;
            if (!fileList[i].delete())
                Log.w("BackgroundVideoRecorder", "Could not delete "+fileList[i]);
            i--;
        }
    }

    public void autoFocus() {
        if (mFocusModes[focusMode].equals(Parameters.FOCUS_MODE_AUTO)
                || mFocusModes[focusMode].equals(Parameters.FOCUS_MODE_MACRO)) {
            camera.autoFocus(null);
        }
    }

    public void applyCameraParameters() {
        if (camera != null) {
            Parameters parameters = camera.getParameters();
            if(parameters.getSupportedFocusModes().contains(mFocusModes[focusMode])) {
                parameters.setFocusMode(mFocusModes[focusMode]);
            }
            if(parameters.getSupportedSceneModes() != null
                    && parameters.getSupportedSceneModes().contains(mSceneModes[sceneMode])) {
                parameters.setSceneMode(mSceneModes[sceneMode]);
            }
            if(parameters.getSupportedFlashModes() != null
                    && parameters.getSupportedFlashModes().contains(mFlashModes[flashMode])) {
                parameters.setFlashMode(mFlashModes[flashMode]);
            }
            if (parameters.isZoomSupported()) {
                parameters.setZoom(zoomFactor);
                camera.setParameters(parameters);
            }
            camera.setParameters(parameters);
        }
    }

    public void toggleFocus() {
        if (camera != null) {
            Parameters parameters = camera.getParameters();
            do {
                focusMode = (focusMode + 1) % mFocusModes.length;
            } while (!parameters.getSupportedFocusModes().contains(
                    mFocusModes[focusMode])); // SKIP unsupported modes
            applyCameraParameters();
        }
    }

    public void toggleNight() {
        if (camera != null) {
            Parameters parameters = camera.getParameters();
            do {
                sceneMode = (sceneMode + 1) % mSceneModes.length;
            } while (!parameters.getSupportedSceneModes().contains(
                    mSceneModes[sceneMode])); // SKIP unsupported modes
            applyCameraParameters();
        }
    }

    public void toggleFlash() {
        if (camera != null) {
            Parameters parameters = camera.getParameters();
            do {
                flashMode = (flashMode + 1) % mFlashModes.length;
            } while (!parameters.getSupportedFlashModes().contains(
                    mFlashModes[flashMode])); // SKIP unsupported modes
            applyCameraParameters();
        }
    }

    public void setZoom(float mval) {
        if (camera != null) {
            Parameters parameters = camera.getParameters();
            if (parameters.isZoomSupported()) {
                zoomFactor = (int) (parameters.getMaxZoom() * (mval - 4) / 10.0);
                parameters.setZoom(zoomFactor);
                camera.setParameters(parameters);
            }
        }
    }

    public void toggleRecording() {
        if (isRecording) {
            StopRecording();
        } else {
            StartRecording();
        }
    }

    public void ChangeSurface(int width, int height) {
        if (this.VIDEO_WIDTH / this.VIDEO_HEIGHT > width / height) {
            height = width * this.VIDEO_HEIGHT / this.VIDEO_WIDTH;
        } else {
            width = height * this.VIDEO_WIDTH / this.VIDEO_HEIGHT; //debug
        }
        LayoutParams layoutParams = new WindowManager.LayoutParams(
                // WindowManager.LayoutParams.WRAP_CONTENT,
                // WindowManager.LayoutParams.WRAP_CONTENT,
                width, height, WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        if (width == 1) {
            layoutParams.gravity = Gravity.START | Gravity.TOP;
        } else {
            layoutParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
        }
        windowManager.updateViewLayout(surfaceView, layoutParams);
        if (width > 1) {
            mTextView.setVisibility(TextView.VISIBLE);
            mBatteryView.setVisibility(TextView.VISIBLE);
        } else {
            mTextView.setVisibility(TextView.INVISIBLE);
            mBatteryView.setVisibility(TextView.INVISIBLE);
        }
    }

    // Stop isRecording and remove SurfaceView
    @Override
    public void onDestroy() {
        StopRecording();

        windowManager.removeView(surfaceView);
        windowManager.removeView(mTextView);
        windowManager.removeView(mBatteryView);
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format,
                               int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onInfo(MediaRecorder mr, int what, int extra) {
        if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
            Toast.makeText(this, R.string.max_duration_reached, Toast.LENGTH_LONG).show();
            StopRecording();
        }
    }

    private int getBatteryLevel(Context context) {
        int batteryLevel = 0;
        try {
            IntentFilter intentFilter = new IntentFilter(
                    Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = context.registerReceiver(null, intentFilter);
            if (batteryStatus == null)
                return -1;

            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            if (status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL) {
                return -1;
            }

            batteryLevel = (int) (100 * level / (float) scale);

            if (batteryLevel < 0) {
                batteryLevel = 0;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return batteryLevel;
    }
}

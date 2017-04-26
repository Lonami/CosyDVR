package io.github.lonamiwebs.hidrok;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
//import android.os.SystemClock;
import android.view.Display;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnTouchListener;
import android.widget.Button;
//import android.widget.Toast;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.graphics.Point;
import android.os.IBinder;

import io.github.lonamiwebs.R;


public class Hidrok extends Activity{

    BackgroundVideoRecorder mService;
    Button prefsButton, recButton, focusButton, flashButton, exitButton;
    View mainView;
    boolean mBound = false;
    boolean recording = false;
    boolean mayClick = false;
    private int mWidth = 1, mHeight = 1;
    private float mScaleFactor = 4.0f;
    private String[] mFocusNames = {"I",
            "V",
            "A",
            "M",
            "E",
    };

    private final class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            mScaleFactor *= detector.getScaleFactor();

            // Don't let the object get too small or too large.
            mScaleFactor = Math.max(4.0f, Math.min(mScaleFactor, 14.0f));
            if (mBound) {
                mService.setZoom(mScaleFactor);
            }
            return true;
        }
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        recording = false;

        HidrokSettings settings = new HidrokSettings(this);
        if (settings.getReverseLandscape()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }


        setContentView(R.layout.main);

        prefsButton = (Button)findViewById(R.id.prefs_button);
        recButton = (Button)findViewById(R.id.rec_button);
        focusButton = (Button)findViewById(R.id.focus_button);
        flashButton = (Button)findViewById(R.id.flash_button);
        exitButton = (Button)findViewById(R.id.exit_button);
        mainView = findViewById(R.id.mainview);

        prefsButton.setOnClickListener(prefsButtonOnClickListener);
        recButton.setOnClickListener(recButtonOnClickListener);
        focusButton.setOnClickListener(focButtonOnClickListener);
        flashButton.setOnClickListener(flsButtonOnClickListener);
        exitButton.setOnLongClickListener(exiButtonOnLongClickListener);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        final ScaleGestureDetector mScaleDetector = new ScaleGestureDetector(this, new ScaleListener());
        mainView.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                mScaleDetector.onTouchEvent(event);
                //Extra analysis for single tap detection. Swipes are detected as autofocus too for now
                int action = event.getAction() & MotionEvent.ACTION_MASK;
                switch (action) {
                    case MotionEvent.ACTION_DOWN : {
                        mayClick = true;	// First finger = OK click
                        break;
                    }
                    case MotionEvent.ACTION_POINTER_DOWN : {
                        mayClick = false;	// Second finger is not a click anymore
                        break;
                    }
                    case MotionEvent.ACTION_UP : {
                        if (mayClick) {
                            if (mBound) {
                                mService.autoFocus();
                            }
                        }
                        mayClick = false;
                    }
                }
                return true;
            }

        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            Display display = getWindowManager().getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);
            mWidth = size.x;
            mHeight = size.y - prefsButton.getHeight();

            Intent intent = new Intent(getApplicationContext(), BackgroundVideoRecorder.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startService(intent);
            bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    public void onDestroy() {
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
        super.onDestroy();
    }

    @Override
    public void onPause() {
        if (mBound) {
            mService.ChangeSurface(1, 1);
        }
        super.onPause();
        this.unregisterReceiver(receiver);
    }

    @Override
    public void onResume() {
        if (mBound) {
            mService.ChangeSurface(mWidth, mHeight);
        }
        super.onResume();
        this.registerReceiver(receiver,filter);
    }

    public void updateInterface() {
        if (mBound) {
            if (mService.isRecording()) {
                recButton.setText(getString(R.string.stop));
            } else {
                recButton.setText(getString(R.string.start));
            }
        }
    }

    Button.OnClickListener prefsButtonOnClickListener = new Button.OnClickListener() {
        @Override
        public void onClick(View v) {
            mService.ChangeSurface(1, 1);
            Intent myIntent = new Intent(getApplicationContext(), HidrokPreferenceActivity.class);
            startActivity(myIntent);
        }
    };

    Button.OnClickListener recButtonOnClickListener = new Button.OnClickListener() {
        @Override
        public void onClick(View v) {
            mService.setMute(true);
            if (mService.isRecording()) {
                mService.stopRecording();
                recButton.setText(getString(R.string.start));
            } else {
                mService.StartRecording();
                recButton.setText(getString(R.string.stop));
            }
            mService.setMute(false);
        }
    };

    Button.OnClickListener focButtonOnClickListener = new Button.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mBound) {
                mService.toggleFocus();
                focusButton.setText(getString(R.string.focus) + " [" + mFocusNames[mService.getFocusMode()] + "]");
            }
        }
    };

    Button.OnClickListener flsButtonOnClickListener = new Button.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mBound) {
                mService.toggleFlash();
            }
        }
    };

    Button.OnLongClickListener exiButtonOnLongClickListener = new Button.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            if (mBound) {
                unbindService(Hidrok.this.mConnection);
                Hidrok.this.mBound = false;
            }
            stopService(new Intent(Hidrok.this, BackgroundVideoRecorder.class));
            Hidrok.this.finish();
            return true;
        }
    };

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            BackgroundVideoRecorder.LocalBinder binder =
                    (BackgroundVideoRecorder.LocalBinder)service;

            mService = binder.getService();
            mBound = true;
            mService.ChangeSurface(mWidth, mHeight);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    private IntentFilter filter = new IntentFilter("io.github.lonamiwebs.hidrok.updateInterface");

    private BroadcastReceiver receiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context c, Intent i) {
            updateInterface();
        }
    };
}

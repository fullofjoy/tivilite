package com.tivilite.tv.touch;

import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.Window;
import android.view.WindowManager;

/**
 * TiviLite Touch Gesture Overlay Engine for Mobile Phone & Tablet
 * Handles brightness, volume, seek scrubbing, double-tap play/pause,
 * and long-press 2.0x playback speed preview.
 */
public class TouchGestureOverlayManager {
    public interface GestureListener {
        void onBrightnessChanged(int percent);
        void onVolumeChanged(int current, int max);
        void onSeekPreview(long offsetSeconds);
        void onSeekCommit(long offsetSeconds);
        void onDoubleTap();
        void onSingleTap();
        void onSpeedBoostStart(float speed);
        void onSpeedBoostEnd();
    }

    private static final int GESTURE_NONE = 0;
    private static final int GESTURE_BRIGHTNESS = 1;
    private static final int GESTURE_VOLUME = 2;
    private static final int GESTURE_SEEK = 3;

    private final Activity activity;
    private final AudioManager audioManager;
    private final int touchSlop;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private GestureListener listener;

    private float downX, downY;
    private long downTime;
    private int activeGesture = GESTURE_NONE;
    private float currentBrightness = -1f; // -1 represents system default
    private int currentVolume = 0;
    private int maxVolume = 15;
    private long previewSeekSeconds = 0;
    private boolean isSpeedBoosting = false;

    private long lastTapTime = 0;
    private float lastTapX = 0, lastTapY = 0;
    private Runnable singleTapRunnable;

    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (activeGesture == GESTURE_NONE) {
                isSpeedBoosting = true;
                if (listener != null) listener.onSpeedBoostStart(2.0f);
            }
        }
    };

    public TouchGestureOverlayManager(Activity activity) {
        this.activity = activity;
        this.audioManager = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            this.maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        }
        this.touchSlop = ViewConfiguration.get(activity).getScaledTouchSlop();
    }

    public void setGestureListener(GestureListener listener) {
        this.listener = listener;
    }

    public boolean onTouchEvent(MotionEvent event) {
        int width = activity.getResources().getDisplayMetrics().widthPixels;
        int height = activity.getResources().getDisplayMetrics().heightPixels;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                downTime = System.currentTimeMillis();
                activeGesture = GESTURE_NONE;
                previewSeekSeconds = 0;

                // Query current brightness
                Window window = activity.getWindow();
                WindowManager.LayoutParams lp = window.getAttributes();
                currentBrightness = lp.screenBrightness;
                if (currentBrightness < 0) currentBrightness = 0.5f;

                // Query current volume
                if (audioManager != null) {
                    currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                }

                // Schedule long press for 2.0x speed preview
                handler.postDelayed(longPressRunnable, 500);
                return true;

            case MotionEvent.ACTION_MOVE:
                float deltaX = event.getX() - downX;
                float deltaY = event.getY() - downY;
                float absDeltaX = Math.abs(deltaX);
                float absDeltaY = Math.abs(deltaY);

                if (absDeltaX > touchSlop || absDeltaY > touchSlop) {
                    handler.removeCallbacks(longPressRunnable);
                }

                if (isSpeedBoosting) {
                    // In speed boost mode, ignore normal gestures
                    return true;
                }

                if (activeGesture == GESTURE_NONE) {
                    if (absDeltaY > touchSlop && absDeltaY > absDeltaX * 1.5f) {
                        if (downX < width * 0.45f) {
                            activeGesture = GESTURE_BRIGHTNESS;
                        } else if (downX > width * 0.55f) {
                            activeGesture = GESTURE_VOLUME;
                        }
                    } else if (absDeltaX > touchSlop * 1.5f && absDeltaX > absDeltaY * 1.5f) {
                        activeGesture = GESTURE_SEEK;
                    }
                }

                if (activeGesture == GESTURE_BRIGHTNESS) {
                    // Vertical drag for brightness: dragging up increases brightness
                    float change = -deltaY / (height * 0.65f);
                    float newBrightness = Math.max(0.01f, Math.min(1.0f, currentBrightness + change));
                    Window win = activity.getWindow();
                    WindowManager.LayoutParams params = win.getAttributes();
                    params.screenBrightness = newBrightness;
                    win.setAttributes(params);

                    int percent = Math.round(newBrightness * 100);
                    if (listener != null) listener.onBrightnessChanged(percent);
                    return true;
                } else if (activeGesture == GESTURE_VOLUME) {
                    // Vertical drag for volume
                    float change = -deltaY / (height * 0.65f);
                    int targetVolume = Math.round(currentVolume + (change * maxVolume));
                    targetVolume = Math.max(0, Math.min(maxVolume, targetVolume));
                    if (audioManager != null) {
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0);
                    }
                    if (listener != null) listener.onVolumeChanged(targetVolume, maxVolume);
                    return true;
                } else if (activeGesture == GESTURE_SEEK) {
                    // Horizontal drag for seeking (+/- up to 120 seconds)
                    float ratio = deltaX / (width * 0.6f);
                    previewSeekSeconds = Math.round(ratio * 90);
                    if (listener != null) listener.onSeekPreview(previewSeekSeconds);
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(longPressRunnable);

                if (isSpeedBoosting) {
                    isSpeedBoosting = false;
                    if (listener != null) listener.onSpeedBoostEnd();
                    return true;
                }

                if (activeGesture == GESTURE_SEEK) {
                    if (previewSeekSeconds != 0 && listener != null) {
                        listener.onSeekCommit(previewSeekSeconds);
                    }
                    activeGesture = GESTURE_NONE;
                    return true;
                }

                if (activeGesture != GESTURE_NONE) {
                    activeGesture = GESTURE_NONE;
                    return true;
                }

                // Check tap gestures
                long clickDuration = System.currentTimeMillis() - downTime;
                float moveDist = (float) Math.hypot(event.getX() - downX, event.getY() - downY);

                if (clickDuration < 300 && moveDist < touchSlop * 2) {
                    long now = System.currentTimeMillis();
                    float distFromLastTap = (float) Math.hypot(event.getX() - lastTapX, event.getY() - lastTapY);

                    if (now - lastTapTime < 350 && distFromLastTap < 100) {
                        // Double tap detected!
                        if (singleTapRunnable != null) {
                            handler.removeCallbacks(singleTapRunnable);
                            singleTapRunnable = null;
                        }
                        lastTapTime = 0;
                        if (listener != null) listener.onDoubleTap();
                        return true;
                    } else {
                        // Potential single tap
                        lastTapTime = now;
                        lastTapX = event.getX();
                        lastTapY = event.getY();

                        singleTapRunnable = () -> {
                            if (listener != null) listener.onSingleTap();
                        };
                        handler.postDelayed(singleTapRunnable, 300);
                    }
                }
                break;
        }

        return false;
    }
}

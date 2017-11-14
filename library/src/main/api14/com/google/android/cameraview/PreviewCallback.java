package com.google.android.cameraview;

import android.content.Context;
import android.hardware.Camera;
import android.util.Log;

import com.wellthapp.android.camera.OutputConfigurations;

/**
 * Callback that handles determining whether preview frames are good to capture.
 */
public final class PreviewCallback implements Camera.PreviewCallback {

    public static final String TAG = "CameraPreviewCallback";

    private final OnPreviewFrameAsyncTask asyncTask;
    private final ReactNativeEventListener reactNativeEventListener;
    private final Context context;
    private volatile boolean readyForCapture = false;
    private volatile boolean cameraIsFocused = false;
    private volatile OutputConfigurations outputConfigurations;

    public PreviewCallback(final Context context, OutputConfigurations outputConfigurations, ReactNativeEventListener reactNativeEventListener) {
        this.context = context;
        this.asyncTask = new OnPreviewFrameAsyncTask(context, reactNativeEventListener);
        this.reactNativeEventListener = reactNativeEventListener;
        this.asyncTask.start();
        this.outputConfigurations = outputConfigurations;
    }

    @Override
    public void onPreviewFrame(final byte[] data, final Camera camera) {
        if (this.cameraIsFocused && this.readyForCapture) {
            this.cameraIsFocused = false;
            this.readyForCapture = false;
            if (!this.asyncTask.isRunning()) {
                this.asyncTask.start();
            }
            final OnPreviewFrameAsyncTask.CameraCaptureRequest cameraCaptureRequest = new OnPreviewFrameAsyncTask.CameraCaptureRequest(data, camera, this.getOutputConfigurations());
            this.asyncTask.queue(cameraCaptureRequest);
        } else {
            Log.i(TAG, "Preview frame was ignored because cameraIsFocused or readyForCapture was false!");
        }
    }

    public final void setOutputConfigurations(final OutputConfigurations outputConfigurations) {
        this.outputConfigurations = outputConfigurations;
    }

    public final void setReadyForCapture(final boolean shouldCapture) {
        this.readyForCapture = shouldCapture;
    }

    public final void setCameraIsFocused(final boolean cameraIsFocused) {
        this.cameraIsFocused = cameraIsFocused;
    }

    public final OutputConfigurations getOutputConfigurations() {
        return this.outputConfigurations;
    }

}

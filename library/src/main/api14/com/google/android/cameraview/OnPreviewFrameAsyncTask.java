package com.google.android.cameraview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.wellthapp.android.camera.OutputConfiguration;
import com.wellthapp.android.camera.OutputConfigurations;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

public class OnPreviewFrameAsyncTask extends AsyncTask<Void, Void, Void> {

    public static final String TAG = "OnPreviewFrameAsyncTask";

    public static class CameraCaptureRequest {

        private byte[] bytes;
        private Camera camera;
        private final OutputConfigurations OutputConfigurations;

        public CameraCaptureRequest(final byte[] bytes, final Camera camera, final OutputConfigurations OutputConfigurations) {
            this.bytes = bytes;
            this.camera = camera;
            this.OutputConfigurations = OutputConfigurations;
            Log.d(TAG, "CameraCaptureRequest() --> Initialized a camera capture request!");
        }

        public byte[] getBytes() {
            return this.bytes;
        }

        public Camera getCamera() {
            return this.camera;
        }

        public OutputConfigurations getOutputConfigurations() {
            return this.OutputConfigurations;
        }

    }

    private final LinkedBlockingQueue<CameraCaptureRequest> queue = new LinkedBlockingQueue<>();
    private final Handler handler;
    private final ReactNativeEventListener reactNativeEventListener;
    private final Context contxt;


    private volatile boolean isRunning = false;
    private volatile boolean proceed = true;

    public OnPreviewFrameAsyncTask(final Context context, final ReactNativeEventListener reactNativeEventListener) {
        this.contxt = context;
        this.handler = new Handler(Looper.getMainLooper());
        this.reactNativeEventListener = reactNativeEventListener;
    }

    /**
     * Queues up a capture request for processing.
     * @param cameraCaptureRequest
     */
    public final void queue(final CameraCaptureRequest cameraCaptureRequest) {
        try {
            if (this.queue.size() == 0) {
                queue.put(cameraCaptureRequest);
            } else {
                Log.d(TAG, "Didn't put the capture request in the queue!");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected Void doInBackground(Void... params) {

        while(this.proceed) {

            // Unpack the picture bytes
            final CameraCaptureRequest cameraCaptureRequest;
            try {
                // Unpack the byte array wrapper
                cameraCaptureRequest = queue.take();
                final byte[] bytes = cameraCaptureRequest.getBytes();
                final Camera camera = cameraCaptureRequest.getCamera();
                final OutputConfigurations configurations = cameraCaptureRequest.getOutputConfigurations();

                // Determine the camera settings
                final Camera.Parameters parameters = camera.getParameters();
                final int previewWidth = parameters.getPreviewSize().width;
                final int previewHeight = parameters.getPreviewSize().height;

                // Do the actual save
                this.emitEvent(this.saveImageAndReturnEvent(bytes, previewWidth, previewHeight, configurations));

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * Passes the map of data to the React Native event listener interface
     * @param mapList
     */
    private void emitEvent(final List<Map<String, String>> mapList) {
        this.handler.post(new Runnable() {
            @Override
            public void run() {
                reactNativeEventListener.emitEvent(mapList);
            }
        });
    }


    /**
     *
     * @param bytes
     * @param sourceWidth
     * @param sourceHeight
     * @param configurations
     * @return
     */
    private List<Map<String, String>> saveImageAndReturnEvent(final byte[] bytes, final int sourceWidth, final int sourceHeight, final OutputConfigurations configurations) {
        final List<Map<String, String>> outputList = new ArrayList<>();

        // Build output
        if (configurations != null) {
            final int numberOfConfigurations = configurations.getSize();
            for (int i = 0; i < numberOfConfigurations; i++) {
                final Map<String, String> outputMap = new HashMap<>();
                final OutputConfiguration configuration = configurations.getConfiguration(i);
                if (configuration != null) {
                    final File savedFile = this.saveImageUsingConfiguration(bytes, sourceWidth, sourceHeight, configuration);
                    outputMap.put("name", configuration.getName());
                    outputMap.put("file", savedFile.getAbsolutePath());
                    outputList.add(outputMap);
                }
            }
        }

        return outputList;
    }

    private ByteArrayOutputStream toByteArrayOutputStream(final byte[] data, final int width, final int height, final int quality) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        YuvImage yuvImage = new YuvImage(data, ImageFormat.NV21, width, height, null);
        Rect rect = new Rect(0, 0, width, height);
        yuvImage.compressToJpeg(rect, quality, out);
        return out;
    }

    public final File getFileUsingConfiguration(OutputConfiguration configuration) {
        if (configuration != null) {
            final String fileName = configuration.getName().contains(".jpg") ? configuration.getName() : configuration.getName().concat(".jpg");
            final OutputConfiguration.Directory directory = configuration.getDirectory();

            switch(directory) {
                case Cache:
                    return new File(this.contxt.getCacheDir(), fileName);
                case Private:
                    return new File(this.contxt.getFilesDir(), fileName);
                case Public:
                    return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), fileName);
            }
        }
        return null;
    }

    private Bitmap fixOrientation(final Bitmap bitmap, final int degrees) {
        if (bitmap.getWidth() > bitmap.getHeight()) {
            Matrix matrix = new Matrix();
            matrix.postRotate(degrees);
            return Bitmap.createBitmap(bitmap , 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        }
        return null;
    }

    /**
     * Saves an image
     * @param data
     * @param width
     * @param height
     * @param configuration
     * @return
     */
    public File saveImageUsingConfiguration(final byte[] data, int width, int height, final OutputConfiguration configuration) {
        if (configuration != null) {

            final int adjustedHeight = (int) (height * configuration.getSize());
            final int adjustedWidth = (int) (width * configuration.getSize());
            final double adjustedQuality = configuration.getQuality() * 100d;

            // Get the image byte array output stream
            final ByteArrayOutputStream byteArrayOutputStream = toByteArrayOutputStream(data, width, height, (int)adjustedQuality);
            final File file = getFileUsingConfiguration(configuration);
            final OutputStream fileOutputStream;
            try {
                fileOutputStream = new FileOutputStream(file);
                if (width == adjustedWidth && height == adjustedHeight) {
                    // In this case, the image isn't scaled.  Save it as-is.
                    final byte[] imageBytes = byteArrayOutputStream.toByteArray();
                    final Bitmap bitmapImage = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                    final Bitmap rotatedBitmap = fixOrientation(bitmapImage, 90);

                    rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, (int)adjustedQuality, fileOutputStream);
//                    byteArrayOutputStream.writeTo(fileOutputStream);
                    fileOutputStream.flush();
                    fileOutputStream.close();
                    byteArrayOutputStream.flush();
                    byteArrayOutputStream.close();
                    bitmapImage.recycle();
                    rotatedBitmap.recycle();
                } else {
                    // In this case, the image is scaled and we need to do some manipulation.
                    final byte[] imageBytes = byteArrayOutputStream.toByteArray();
                    final Bitmap bitmapImage = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                    final Bitmap resizedBitmapImage = Bitmap.createScaledBitmap(bitmapImage, adjustedWidth, adjustedHeight, true);
                    final Bitmap rotatedBitmap = fixOrientation(resizedBitmapImage, 90);

                    rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, (int)adjustedQuality, fileOutputStream);
                    fileOutputStream.flush();
                    fileOutputStream.close();
                    byteArrayOutputStream.flush();
                    byteArrayOutputStream.close();
                    bitmapImage.recycle();
                    resizedBitmapImage.recycle();
                    rotatedBitmap.recycle();
                }
            } catch (IOException exception) {
                exception.printStackTrace();
            }

            return file;

        } else {
            Log.w("PreviewFrameAsyncTask", "The configuration for save image was null!");
            return null;
        }
    }

    public boolean isRunning() {
        synchronized (this) {
            return this.isRunning;
        }
    }

    public final void start() {
        synchronized (this) {
            this.isRunning = true;
        }
        this.execute();
    }

    public final void stop() {
        synchronized (this) {
            this.proceed = false;
            this.isRunning = false;
        }
        this.queue.clear();
        this.cancel(true);
    }

}

package com.google.android.cameraview;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.media.ImageReader;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.wellthapp.android.camera.OutputConfiguration;
import com.wellthapp.android.camera.OutputConfigurations;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

public class OnPreviewFrameAsyncTask2 extends AsyncTask<Void, Void, Void> {

    public static final String TAG = "OnPreviewFrameAsyncTask";

    public static class BitmapProcessRequest {

        private Bitmap bitmap;
        private final OutputConfigurations outputConfigurations;

        public BitmapProcessRequest(final Bitmap bitmap, final OutputConfigurations OutputConfigurations) {
            this.bitmap = bitmap;
            this.outputConfigurations = OutputConfigurations;
            Log.d(TAG, "BitmapProcessRequest() --> Initialized an bitmap process request!");
        }

        public Bitmap getBitmap() {
            return this.bitmap;
        }

        public OutputConfigurations getOutputConfigurations() {
            return this.outputConfigurations;
        }

    }

    private final LinkedBlockingQueue<BitmapProcessRequest> queue = new LinkedBlockingQueue<>();
    private final Handler handler;
    private final ReactNativeEventListener reactNativeEventListener;
    private final Context contxt;


    private volatile boolean isRunning = false;
    private volatile boolean proceed = true;

    public OnPreviewFrameAsyncTask2(final Context context, final ReactNativeEventListener reactNativeEventListener) {
        this.contxt = context;
        this.handler = new Handler(Looper.getMainLooper());
        this.reactNativeEventListener = reactNativeEventListener;
    }

    /**
     * Queues up a capture request for processing.
     * @param bitmapProcessRequest
     */
    public final void queue(final BitmapProcessRequest bitmapProcessRequest) {
        try {
            if (this.queue.size() == 0) {
                queue.put(bitmapProcessRequest);
            } else {
                Log.w(TAG, "Didn't put the capture request in the queue!");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    @Override
    protected Void doInBackground(Void... params) {

        while(this.proceed) {

            // Unpack the picture bytes
            final BitmapProcessRequest bitmapProcessRequest;

            try {
                // Unpack the byte array wrapper
                bitmapProcessRequest = queue.take();
                final Bitmap bitmap = bitmapProcessRequest.getBitmap();
                final OutputConfigurations outputConfigurations = bitmapProcessRequest.getOutputConfigurations();
                this.emitEvent(this.saveImageAndReturnEvent(bitmap, outputConfigurations));

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
     * @param bitmap
     * @param configurations
     * @return
     */
    private List<Map<String, String>> saveImageAndReturnEvent(final Bitmap bitmap, final OutputConfigurations configurations) {
        final List<Map<String, String>> outputList = new ArrayList<>();

        // Build output
        if (configurations != null) {
            final int numberOfConfigurations = configurations.getSize();
            for (int i = 0; i < numberOfConfigurations; i++) {
                final Map<String, String> outputMap = new HashMap<>();
                final OutputConfiguration configuration = configurations.getConfiguration(i);
                if (configuration != null) {
                    final File savedFile = this.saveImageUsingConfiguration(bitmap, configuration);
                    outputMap.put("name", configuration.getName());
                    outputMap.put("file", savedFile.getAbsolutePath());
                    outputList.add(outputMap);
                }
            }
        }

        return outputList;
    }

    public final File getFileUsingConfiguration(OutputConfiguration configuration) {
        if (configuration != null) {
            final String fileName = configuration.getName().contains(".jpg") ? configuration.getName() : configuration.getName().concat(".jpg");
            final OutputConfiguration.Directory directory = configuration.getDirectory();

            switch(directory) {
                case CACHE:
                    return new File(this.contxt.getCacheDir(), fileName);
                case PRIVATE:
                    return new File(this.contxt.getFilesDir(), fileName);
                case PUBLIC:
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
        } else {
            return bitmap;
        }
    }

    /**
     * Saves an image
     * @param bitmapImage
     * @param configuration
     * @return
     */
    public File saveImageUsingConfiguration(final Bitmap bitmapImage, final OutputConfiguration configuration) {
        if (bitmapImage != null && configuration != null) {

            final int height = bitmapImage.getHeight();
            final int width = bitmapImage.getWidth();

            final int adjustedHeight = (int) (height * configuration.getSize());
            final int adjustedWidth = (int) (width * configuration.getSize());
            final double adjustedQuality = configuration.getQuality() * 100d;

            // Get the image byte array output stream
            final File file = getFileUsingConfiguration(configuration);
            final OutputStream fileOutputStream;
            try {
                fileOutputStream = new FileOutputStream(file);
                if (width == adjustedWidth && height == adjustedHeight) {
                    // In this case, the image isn't scaled.  Save it as-is.
                    final Bitmap rotatedBitmap = fixOrientation(bitmapImage, 90);
                    rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, (int)adjustedQuality, fileOutputStream);
                    fileOutputStream.flush();
                    fileOutputStream.close();
                    bitmapImage.recycle();
                    rotatedBitmap.recycle();
                } else {
                    // In this case, the image is scaled and we need to do some manipulation.
                    final Bitmap resizedBitmapImage = Bitmap.createScaledBitmap(bitmapImage, adjustedWidth, adjustedHeight, true);
                    final Bitmap rotatedBitmap = fixOrientation(resizedBitmapImage, 90);

                    rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, (int)adjustedQuality, fileOutputStream);
                    fileOutputStream.flush();
                    fileOutputStream.close();
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

package com.vladd11.app.robot;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.SessionConfiguration;
import android.media.Image;
import android.media.ImageReader;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.os.Handler;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

public class StreamingController {
    private static final String TAG = "StreamingController";
    private CameraCaptureSession cameraSession;
    private Surface imReaderSurface;
    private ImageReader imageReader;
    public final int width = 720;
    public final int height = 1280;
    private final Context context;
    private final SurfaceView surfaceView;
    private final Handler handler;

    public StreamingController(Context context, SurfaceView surfaceView) {
        this.context = context;
        this.surfaceView = surfaceView;
        this.handler = new Handler(Looper.myLooper());
    }

    public void send(OnImageReadyListener listener) throws CameraAccessException {
        CaptureRequest.Builder builder = cameraSession.getDevice().createCaptureRequest(
                CameraDevice.TEMPLATE_STILL_CAPTURE);
        builder.addTarget(imReaderSurface);
        cameraSession.capture(builder.build(), new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                final Image image = imageReader.acquireLatestImage();
                if (image == null) return;

                try {
                    listener.call(image.getPlanes()[0].getBuffer());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                image.close();
            }
        }, handler);
    }

    public void start() throws CameraAccessException {
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                imageReader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 2);

// Remember to call this only *after* SurfaceHolder.Callback.surfaceCreated()
                //final Surface previewSurface = surfaceView.getHolder().getSurface();
                imReaderSurface = imageReader.getSurface();
                final Surface[] targets = new Surface[]{imReaderSurface, surfaceView.getHolder().getSurface()};

                try {
                    getBackCamera(device -> {
                        try {
                            device.createCaptureSession(Arrays.asList(targets), new CameraCaptureSession.StateCallback() {
                                @Override
                                public void onConfigured(@NonNull CameraCaptureSession session) {
                                    cameraSession = session;
                                    try {
                                        CaptureRequest.Builder captureRequest = session.getDevice().createCaptureRequest(
                                                CameraDevice.TEMPLATE_PREVIEW);
                                        captureRequest.addTarget(surfaceView.getHolder().getSurface());
                                        session.setRepeatingRequest(captureRequest.build(), null, handler);
                                    } catch (CameraAccessException e) {
                                        e.printStackTrace();
                                    }
                                }

                                @Override
                                public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                                }
                            }, handler);
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }
                    });
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {

            }
        });
    }

    @SuppressLint("MissingPermission")
    public void getBackCamera(OnCameraReadyListener listener) throws CameraAccessException {
        final CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

        final String[] preCameraIds = cameraManager.getCameraIdList();
        final List<String> cameraIds = new ArrayList<>();
        // Get list of all compatible cameras
        for (String id :
                preCameraIds) {
            try {
                final CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                final int[] capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
                for (int capability :
                        capabilities) {
                    if (capability == CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE) {
                        cameraIds.add(id);
                    }
                }
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        // Iterate over the list of cameras and return the first one matching desired
        // lens-facing configuration
        for (String id : cameraIds) {
            final CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
            if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_BACK) {
                cameraManager.openCamera(id, new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(@NonNull CameraDevice camera) {
                        listener.call(camera);
                    }

                    @Override
                    public void onDisconnected(@NonNull CameraDevice camera) {

                    }

                    @Override
                    public void onError(@NonNull CameraDevice camera, int error) {

                    }
                }, handler);
            }
        }
    }

    public interface OnCameraReadyListener {
        void call(CameraDevice device);
    }

    public interface OnImageReadyListener {
        void call(ByteBuffer buffer) throws InterruptedException;
    }
}

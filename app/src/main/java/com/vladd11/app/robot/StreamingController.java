package com.vladd11.app.robot;

import android.app.Activity;
import android.graphics.Bitmap;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.hardware.usb.UsbDevice;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;

import com.jiangdg.usbcamera.UVCCameraHelper;
import com.serenegiant.usb.CameraDialog;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.common.AbstractUVCCameraHandler;
import com.serenegiant.usb.widget.CameraViewInterface;
import com.serenegiant.usb.widget.UVCCameraTextureView;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;

public class StreamingController implements CameraDialog.CameraDialogParent {
    private static final int WIDTH = 640;
    private static final int HEIGHT = 480;
    private static final String TAG = "StreamingController";
    private final Activity activity;
    private final CameraViewInterface surfaceView;
    private final Handler handler;
    public UVCCameraHelper mCameraHelper;
    private boolean isPreview;
    private boolean isRequest;
    private boolean twice;

    public StreamingController(Activity activity, UVCCameraTextureView surfaceView) {
        this.activity = activity;
        this.surfaceView = surfaceView;
        this.handler = new Handler(Looper.myLooper());
    }

    public void send(OnImageReadyListener listener) throws InterruptedException {
        final ByteArrayOutputStream stream = new ByteArrayOutputStream();

        surfaceView.captureStillImage(WIDTH, HEIGHT).compress(Bitmap.CompressFormat.JPEG, 90, stream);
        listener.call(stream.toByteArray());
    }

    public void stop() {
        if(mCameraHelper != null) {
            mCameraHelper.unregisterUSB();
            mCameraHelper.stopPreview();
        }
    }

    public void start() throws CameraAccessException {
        if(twice) return;
        twice = true;

        mCameraHelper = UVCCameraHelper.getInstance(640, 480);

        surfaceView.setCallback(new CameraViewInterface.Callback() {
            @Override
            public void onSurfaceCreated(CameraViewInterface view, Surface surface) {
                if (!isPreview && mCameraHelper.isCameraOpened()) {
                    mCameraHelper.startPreview(surfaceView);
                    isPreview = true;
                }
            }

            @Override
            public void onSurfaceChanged(CameraViewInterface view, Surface surface, int width, int height) {

            }

            @Override
            public void onSurfaceDestroy(CameraViewInterface view, Surface surface) {

            }
        });

        mCameraHelper.setOnPreviewFrameListener(nv21Yuv -> Log.d(TAG, "onPreviewResult: "+nv21Yuv.length));
        mCameraHelper.setDefaultPreviewSize(WIDTH, HEIGHT);
        mCameraHelper.setDefaultFrameFormat(UVCCameraHelper.FRAME_FORMAT_MJPEG);
// set default preview size
        /*m
        mCameraHelper.setDefaultFrameFormat(UVCCameraHelper.FRAME_FORMAT_MJPEG);*/
// set default frame format，defalut is UVCCameraHelper.FRAME_FORMAT_MJPEG
// if using mpeg can not record mp4,please try yuv
// mCameraHelper.setDefaultFrameFormat(UVCCameraHelper.FRAME_FORMAT_YUYV);
        mCameraHelper.initUSBMonitor(activity, surfaceView, new UVCCameraHelper.OnMyDevConnectListener() {
            @Override
            public void onAttachDev(UsbDevice device) {
                Log.d(TAG, "onAttachDev");
                if (!isRequest) {
                    isRequest = true;
                    mCameraHelper.requestPermission(0);
                }
            }

            @Override
            public void onDettachDev(UsbDevice device) {
                Log.d(TAG, "onDettachDev");
                if (isRequest) {
                    isRequest = false;
                    mCameraHelper.closeCamera();
                }
            }

            @Override
            public void onConnectDev(UsbDevice device, boolean isConnected) {
                if (isConnected) {
                    Log.d(TAG, "onConnectDev");
                } else Log.e(TAG, "onConnectDev: fail");
            }

            @Override
            public void onDisConnectDev(UsbDevice device) {

            }
        });
        //handler.postDelayed(() -> mCameraHelper.requestPermission(0), 3000);
    }

    @Override
    public USBMonitor getUSBMonitor() {
        return mCameraHelper.getUSBMonitor();
    }

    @Override
    public void onDialogResult(boolean canceled) {
        Log.d(TAG, "onDialogResult");
    }

    public interface OnCameraReadyListener {
        void call(CameraDevice device);
    }

    public interface OnImageReadyListener {
        void call(byte[] buffer) throws InterruptedException;
    }
}

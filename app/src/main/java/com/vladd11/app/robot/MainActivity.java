package com.vladd11.app.robot;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.common.util.ArrayUtils;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.harrysoft.androidbluetoothserial.BluetoothManager;
import com.harrysoft.androidbluetoothserial.BluetoothSerialDevice;
import com.harrysoft.androidbluetoothserial.SimpleBluetoothDeviceInterface;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.List;
import java.util.Queue;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity implements PathFinder.PathFinderListener {
    public static final String TAG = "MainActivity";
    private static final int USB_SERIAL_TIMEOUT = 1000;
    private final ConnectionManager connectionManager = new ConnectionManager();
    private FusedLocationProviderClient locationClient;
    private PathFinder pathFinder;
    private StreamingController controller;

    private TextView actionTextView;
    private TextView headingTextView;
    private TextView bearingTextView;
    private TextView diffTextView;

    private SimpleBluetoothDeviceInterface deviceInterface;

    @Override
    protected void onStart() {
        super.onStart();
        if (controller.mCameraHelper != null) controller.mCameraHelper.registerUSB();
    }

    @Override
    protected void onStop() {
        if (controller.mCameraHelper != null) controller.mCameraHelper.unregisterUSB();
        super.onStop();
    }

    @SuppressLint("CheckResult")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        locationClient = LocationServices.getFusedLocationProviderClient(this);

        actionTextView = findViewById(R.id.currentActionText);
        headingTextView = findViewById(R.id.headingTextView);
        bearingTextView = findViewById(R.id.bearingTextView);
        diffTextView = findViewById(R.id.diffTextView);

        final Compass compass = new Compass();
        pathFinder = new PathFinder(this);

        compass.setListener(heading -> {
            pathFinder.onHeadingChanged(heading);
            headingTextView.setText(String.valueOf(heading));
        });
        compass.start(this);

        controller = new StreamingController(this, findViewById(R.id.surfaceView));
        try {
            controller.start();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        if (BuildConfig.BLUETOOTH) {
            final BluetoothManager bluetoothManager = BluetoothManager.getInstance();
            bluetoothManager.openSerialDevice(bluetoothManager.getPairedDevicesList().get(0).getAddress())
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(this::onConnected, this::onError);
        } else {
            connectControllerUSB();
        }
    }

    public void connectControllerUSB() {
        final UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        final List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        if (availableDrivers.isEmpty()) {
            return;
        }

        // Open a connection to the first available driver.
        final UsbSerialDriver driver = availableDrivers.get(0);
        final UsbDeviceConnection connection = manager.openDevice(driver.getDevice());
        if (connection == null) {
            // add UsbManager.requestPermission(driver.getDevice(), ..) handling here
            return;
        }

        final UsbSerialPort port = driver.getPorts().get(0); // Most devices have just one port (port 0)
        try {
            port.open(connection);
            port.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
        } catch (IOException e) {
            e.printStackTrace();
        }

        final SerialInputOutputManager IOManager = new SerialInputOutputManager(port, new SerialInputOutputManager.Listener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onNewData(byte[] data) {
                // TODO: make it work again
            }

            @Override
            public void onRunError(Exception e) {
                e.printStackTrace();
            }
        });
        IOManager.start();
    }

    private void onError(Throwable throwable) {
        throwable.printStackTrace();
    }

    private void onConnected(BluetoothSerialDevice connectedDevice) {
        deviceInterface = connectedDevice.toSimpleDeviceInterface();

        // Listen to bluetooth events
        deviceInterface.setListeners(this::onMessageReceived, this::onMessageSent, this::onError);
    }

    @Override
    public void whenActionChanged(Action action) {

        if (deviceInterface != null) {
            switch (action) {
                case FORWARD:
                    deviceInterface.sendMessage("f");
                    break;
                case BACK:
                    deviceInterface.sendMessage("b");
                    break;
                case LEFT:
                    deviceInterface.sendMessage("l");
                    break;
                case RIGHT:
                    deviceInterface.sendMessage("r");
                    break;
                case STOP:
                    deviceInterface.sendMessage("s");
                    break;
            }
        }
        actionTextView.setText(action.toString());
    }

    @Override
    public void whenTargetAngleChanged(float angle) {
        bearingTextView.setText(String.valueOf(angle));
    }

    private void onMessageSent(String s) {

    }

    private void onMessageReceived(String s) {
        Log.d(TAG, s);
    }

    @SuppressLint("MissingPermission")
    private void requestLocationUpdates() {
        final LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(100);
        locationClient.requestLocationUpdates(locationRequest, pathFinder, Looper.getMainLooper());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 0);
            return;
        }
        requestLocationUpdates();
    }

    private void followPathOfPoints(Queue<Location> locationQueue) {
        pathFinder.followPathOfPoints(locationQueue);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 0) {
            if (!ArrayUtils.contains(permissions, PackageManager.PERMISSION_DENIED)) {
                requestLocationUpdates();
            } else Log.e(TAG, "onRequestPermissionsResult: Failed to get some permissions");
        }
    }

    @Override
    public void whenLocationAccurate() {
        connectionManager.connect(() -> {
            try {
                connectionManager.getLocationPoints(pathFinder.location, this::followPathOfPoints);
                connectionManager.receiveImageRequests(listener -> controller.send((buffer) -> {
                    Log.d(TAG, "received");
                    listener.received(buffer);
                }), (roadPosition, frameTime) -> {
                    for (List<Double> cords : roadPosition) {
                        double x = cords.get(0) - 0.5f;
                        //double y = cords.get(1);

                        if (x < -0.2f || x > 0.2) {
                            if (pathFinder.isFrameValid(frameTime)) {
                                int diff = (int) (x * 60);

                                diffTextView.setText(String.valueOf(diff));
                                pathFinder.setAngleDiff(diff);
                            }
                        }
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    protected void onDestroy() {
        controller.stop();
        connectionManager.disconnect();
        super.onDestroy();
    }
}
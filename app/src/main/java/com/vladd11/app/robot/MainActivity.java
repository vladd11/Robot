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
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
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
import java.nio.charset.StandardCharsets;
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

    private TextView actionTextView;
    private SimpleBluetoothDeviceInterface deviceInterface;

    @SuppressLint("CheckResult")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        locationClient = LocationServices.getFusedLocationProviderClient(this);

        actionTextView = findViewById(R.id.currentActionText);
        pathFinder = new PathFinder(this);

        final StreamingController controller = new StreamingController(this, findViewById(R.id.surfaceView));
        try {
            controller.start();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        connectionManager.connect(() -> {
            try {
                connectionManager.getLocationPoints(this::followPathOfPoints);
                connectionManager.receiveImageRequests(listener -> {
                    try {
                        controller.send(listener::received);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

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
                if (data[0] == 0xC) { // 0xC means that we should send commands
                    try {
                        if (!pathFinder.shouldForward()) {
                            actionTextView.setText("STOP");
                            port.write("s".getBytes(StandardCharsets.UTF_8), USB_SERIAL_TIMEOUT);
                        } else if (pathFinder.getAngle() != Integer.MIN_VALUE) {
                            port.write(("r" + pathFinder.getAngle()).getBytes(StandardCharsets.UTF_8), USB_SERIAL_TIMEOUT);
                            actionTextView.setText("ROTATING " + pathFinder.getAngle());
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
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
        final Handler handler = new Handler(Looper.getMainLooper());
        final Runnable runnable = new Runnable() {
            @SuppressLint("SetTextI18n") // TODO: Fix in production
            @Override
            public void run() {
                if (!pathFinder.shouldForward()) {
                    actionTextView.setText("STOP");
                    deviceInterface.sendMessage("s");
                } else if (pathFinder.getAngle() != Integer.MIN_VALUE) {
                    deviceInterface.sendMessage("r" + pathFinder.getAngle());
                    actionTextView.setText("ROTATING " + pathFinder.getAngle());
                }

                handler.postDelayed(this, 100);
            }
        };
        runnable.run();
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
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 0) {
            if (!ArrayUtils.contains(permissions, PackageManager.PERMISSION_DENIED)) {
                requestLocationUpdates();
            } else Log.e(TAG, "onRequestPermissionsResult: Failed to get some permissions");
        }
    }

    @Override
    public void endOfPath() {

    }
}
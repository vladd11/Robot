package com.vladd11.app.robot;


import android.hardware.camera2.CameraAccessException;
import android.location.Location;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class ConnectionManager extends WebSocketListener {
    private static final String TAG = "ConnectionManager";
    private final Gson gson = new Gson();
    private OkHttpClient client;
    private WebSocket webSocket;

    private OnLocationPointsAvailableListener onResult;
    private OnImageRequestReceivedListener onFrameRequest;
    private OnConnectedListener onConnected;
    private AfterImageCapturedListener afterImageCaptured;
    private long frameTime;

    public void connect(OnConnectedListener onConnected) {
        client = new OkHttpClient.Builder().build();

        final Request request = new Request.Builder()
                .url(Secrets.WS_SERVER_ADDRESS)
                .build();
        webSocket = client.newWebSocket(request, this);

        client.dispatcher().executorService().shutdown();

        this.onConnected = onConnected;
    }

    @Override
    public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, @Nullable Response response) {
        Log.e(TAG, "onFailure", t);
    }

    @Override
    public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
        Log.d(TAG, "onOpen");
        onConnected.call();
    }

    public void disconnect() {
        webSocket.close(1000, "");
    }

    public void getLocationPoints(Location current, OnLocationPointsAvailableListener onResult) throws IOException {
        this.onResult = onResult;
        webSocket.send("route " + current.getLongitude() + ' ' + current.getLatitude());
    }

    @Override
    public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
        if (text.equals("img")) {
            frameTime = System.currentTimeMillis();
            try {
                onFrameRequest.call(buffer -> webSocket.send(ByteString.of(buffer)));
            } catch (CameraAccessException | InterruptedException e) {
                e.printStackTrace();
            }
        } else if (text.startsWith("route")) {
            final List<List<Double>> rawCoordinates = gson.fromJson(
                    text.replace("route ", ""),
                    TypeToken.getParameterized(List.class, List.class).getType());

            final Queue<Location> locationQueue = new ArrayDeque<>();
            for (List<Double> coordinates : rawCoordinates) {
                final Location location = new Location("");
                location.setLatitude(coordinates.get(1));
                location.setLongitude(coordinates.get(0));

                locationQueue.add(location);
            }
            onResult.call(locationQueue);
        } else {
            final List<List<Double>> roadPosition = gson.fromJson(text,
                    TypeToken.getParameterized(List.class, List.class).getType());

            afterImageCaptured.call(roadPosition, frameTime);
        }
    }

    public void receiveImageRequests(OnImageRequestReceivedListener listener, AfterImageCapturedListener afterListener) {
        onFrameRequest = listener;
        afterImageCaptured = afterListener;
    }

    public interface OnLocationPointsAvailableListener {
        void call(Queue<Location> result);
    }

    public interface OnImageRequestReceivedListener {
        void call(ImageReceivedListener listener) throws CameraAccessException, InterruptedException;
    }

    public interface ImageReceivedListener {
        void received(byte[] buffer) throws InterruptedException;
    }

    public interface OnConnectedListener {
        void call();
    }

    public interface AfterImageCapturedListener {
        void call(List<List<Double>> cords, long frameTime);
    }
}

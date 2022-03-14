package com.vladd11.app.robot;


import android.location.Location;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

public class ConnectionManager {
    private static final String TAG = "ConnectionManager";
    private Socket socket;
    private OutputStream writer;

    public void connect(OnConnectedListener onConnected) {
        new Thread(() -> {
            try {
                socket = new Socket(InetAddress.getByName(Secrets.SERVER_ADDRESS), Secrets.SERVER_PORT);
                writer = socket.getOutputStream();
                onConnected.call();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void getLocationPoints(OnLocationPointsAvailableListener onResult) throws IOException {
        writer.write("r{\"c\": [50.242092, 53.211953],\"e\":[50.240560, 53.213407]}".getBytes(StandardCharsets.UTF_8));
        writer.flush();

        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        for (int length; (length = socket.getInputStream().read(buffer)) != -1; ) {
            result.write(buffer, 0, length);
            if (buffer[buffer.length - 1] == 0) {
                break;
            }
        }

        final List<List<Double>> rawCoordinates = new Gson().fromJson(result.toString("UTF-8"),
                TypeToken.getParameterized(List.class, List.class).getType());
        final Queue<Location> locationQueue = new ArrayDeque<>();
        for (List<Double> coordinates : rawCoordinates) {
            final Location location = new Location("");
            location.setLatitude(coordinates.get(1));
            location.setLongitude(coordinates.get(0));

            locationQueue.add(location);
        }
        onResult.call(locationQueue);
    }

    public void receiveImageRequests(OnImageRequestReceivedListener listener) {
        new Thread(() -> {
            try {
                while (true) {
                    while (socket.getInputStream().read() != 128) {

                    }

                    listener.call(buffer -> {
                        final Thread thread = new Thread(() -> {
                            try {
                                writer.write(106);
                                writer.flush();

                                for (int i = 0; i < buffer.remaining(); i++) {
                                    writer.write(buffer.get());
                                }
                                writer.flush();

                                writer.write(106);
                                writer.flush();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
                        thread.start();
                        thread.join();
                    });
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public interface OnLocationPointsAvailableListener {
        void call(Queue<Location> result);
    }

    public interface OnImageRequestReceivedListener {
        void call(ImageReceivedListener listener);
    }

    public interface ImageReceivedListener {
        void received(ByteBuffer buffer) throws InterruptedException;
    }

    public interface OnConnectedListener {
        void call();
    }
}

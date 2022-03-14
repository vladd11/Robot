package com.vladd11.app.robot;

import android.location.Location;
import android.media.audiofx.DynamicsProcessing;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationResult;

import java.util.Queue;

public class PathFinder extends LocationCallback {
    private static final String TAG = "PathFinder";
    private final PathFinderListener listener;
    private int angle;
    private boolean shouldForward;
    private Location target;
    private Queue<Location> points;
    protected Location location;

    public PathFinder(PathFinderListener listener) {
        this.listener = listener;
    }

    public void onLocationResult(@NonNull LocationResult locationResult) {
        if (target != null) {
            location = locationResult.getLastLocation();

            if (location.distanceTo(target) > location.getAccuracy()) {
                shouldForward = true;
            } else nextPoint();

            int bearing = (int) location.bearingTo(target);
            if (bearing < 0) {
                angle = 360 + bearing;
            } else angle = bearing;
        } else shouldForward = false;
    }

    public void onLocationAvailability(@NonNull LocationAvailability var1) {

    }

    public void nextPoint() {
        target = points.poll();
        if (target == null) {
            Log.i(TAG, "nextPoint: End of path");
            listener.endOfPath();
        }
    }

    public void followPathOfPoints(Queue<Location> points) {
        this.points = points;
        nextPoint();
    }

    public boolean shouldForward() {
        return shouldForward;
    }

    public int getAngle() {
        return angle;
    }

    public interface PathFinderListener {
        void endOfPath();
    }
}

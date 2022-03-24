package com.vladd11.app.robot;

import android.location.Location;
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
    private int diff;
    private long timeOfPointSwitch;
    private boolean shouldForward;
    private Location target;
    private Queue<Location> points;
    protected Location location;
    private boolean first = true;

    public PathFinder(PathFinderListener listener) {
        this.listener = listener;
    }

    public void onLocationResult(@NonNull LocationResult locationResult) {
        location = locationResult.getLastLocation();
        if (location.getAccuracy() < 30 && first) {
            first = false;
            listener.whenLocationAccurate();
        }

        if (target != null) {

            if (location.distanceTo(target) > location.getAccuracy()) {
                shouldForward = true;
            } else nextPoint();

            int bearing = (int) location.bearingTo(target) + diff;
            if (bearing < 0) {
                angle = 360 + bearing;
            } else angle = bearing;
        } else shouldForward = false;
    }

    public void onLocationAvailability(@NonNull LocationAvailability var1) {

    }

    public void nextPoint() {
        diff = 0;
        timeOfPointSwitch = System.currentTimeMillis();
        target = points.poll();
        if (target == null) {
            Log.i(TAG, "nextPoint: End of path");
        }
    }

    public boolean isFrameValid(long sendTime) {
        return timeOfPointSwitch < sendTime;
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

    public void setAngleDiff(int angleDiff) {
        this.diff = angleDiff;
    }

    public interface PathFinderListener {
        void whenLocationAccurate();
    }
}

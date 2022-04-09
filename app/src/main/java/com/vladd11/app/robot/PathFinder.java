package com.vladd11.app.robot;

import android.location.Location;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationResult;

import java.util.Queue;

public class PathFinder extends LocationCallback implements Compass.CompassListener {
    private static final String TAG = "PathFinder";
    private final PathFinderListener listener;
    private final Handler handler = new Handler();
    private int angle;
    private int diff;
    private long timeOfPointSwitch;
    private boolean shouldForward;
    private Location target;
    private Queue<Location> points;
    protected Location location;
    private boolean isLocationAccurate = false;
    private int heading;

    public PathFinder(PathFinderListener listener) {
        this.listener = listener;
    }

    public static boolean almostEqual(double a, double b, double eps) {
        return Math.abs(a - b) < eps;
    }

    public void start() {
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (isLocationAccurate && target != null) {
                    float relativeBearing = location.bearingTo(target) - heading;

                    if (!(almostEqual(relativeBearing, 0, 14))) {
                        if (relativeBearing < 0) listener.whenActionChanged(Action.LEFT);
                        else listener.whenActionChanged(Action.RIGHT);
                    } else listener.whenActionChanged(Action.FORWARD);
                }
                handler.postDelayed(this, 250);
            }
        };

        handler.postDelayed(runnable, 250);
    }

    public void onLocationResult(@NonNull LocationResult locationResult) {
        location = locationResult.getLastLocation();
        if (location.getAccuracy() < 30 && !isLocationAccurate) {
            isLocationAccurate = true;
            start();
            listener.whenLocationAccurate();
        }

        if (target != null) {
            if (location.distanceTo(target) > Math.max(location.getAccuracy(), 5)) {
                shouldForward = true;
            } else nextPoint();

            int bearing = (int) location.bearingTo(target) + diff;
            if (bearing < 0) {
                angle = 360 + bearing;
            } else angle = bearing;

            listener.whenTargetAngleChanged(angle);
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
        //this.diff = angleDiff;
    }

    @Override
    public void onHeadingChanged(float heading) {
        this.heading = (int) heading;
    }

    public interface PathFinderListener {
        void whenLocationAccurate();

        void whenActionChanged(Action action);

        void whenTargetAngleChanged(float angle);
    }
}

package com.example.pavlo.aphone.event_listeners;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.util.Log;

/**
 * Created by pavlo on 24.06.16.
 */
public class ProximitySensorListener implements SensorEventListener {

    private static final String LOG_TAG = "Proximity sensor: ";

    private final Runnable onSensorStateListener;
    private final SensorManager sensorManager;

    private Sensor proximitySensor = null;

    private boolean lastStateReportIsNear = false;

    public static ProximitySensorListener create(Context context, Runnable sensorStateListener) {
        return new ProximitySensorListener(context, sensorStateListener);
    }

    private ProximitySensorListener(Context context, Runnable sensorStateListener) {
        onSensorStateListener = sensorStateListener;
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
    }

    public boolean start() {
        if (!initDefaultSensor()) {
            return false;
        }
        sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);

        return true;
    }

    public void stop() {
        if (proximitySensor == null) {
            return;
        }
        sensorManager.unregisterListener(this, proximitySensor);
    }

    public boolean sensorReportNearState() {
        return lastStateReportIsNear;
    }

    private boolean initDefaultSensor() {
        if (proximitySensor != null) {
            return true;
        }
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);

        if (proximitySensor == null) {
            return false;
        }
        logProximitySensorInfo();

        return true;
    }

    private void logProximitySensorInfo() {
        if (proximitySensor == null) {
            return;
        }
        StringBuilder info = new StringBuilder("Proximity sensor: ");
        info.append("name=" + proximitySensor.getName());
        info.append(", vendor: " + proximitySensor.getVendor());
        info.append(", power: " + proximitySensor.getPower());
        info.append(", resolution: " + proximitySensor.getResolution());
        info.append(", max range: " + proximitySensor.getMaximumRange());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            // Added in API level 9.
            info.append(", min delay: " + proximitySensor.getMinDelay());
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            // Added in API level 20.
            info.append(", type: " + proximitySensor.getStringType());
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Added in API level 21.
            info.append(", max delay: " + proximitySensor.getMaxDelay());
            info.append(", reporting mode: " + proximitySensor.getReportingMode());
            info.append(", isWakeUpSensor: " + proximitySensor.isWakeUpSensor());
        }
        Log.d(LOG_TAG, info.toString());
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float distanceInCentimeters = event.values[0];
        if (distanceInCentimeters < proximitySensor.getMaximumRange()) {
            Log.d(LOG_TAG, "Proximity sensor => NEAR state");
            lastStateReportIsNear = true;
        } else {
            Log.d(LOG_TAG, "Proximity sensor => FAR state");
            lastStateReportIsNear = false;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            Log.e(LOG_TAG, "The values returned by this sensor cannot be trusted");
        }
    }
}

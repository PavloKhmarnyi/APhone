package com.example.pavlo.aphone.permissions_manager;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;

import com.example.pavlo.aphone.util.Config;

import java.util.jar.Manifest;

/**
 * Created by pavlo on 27.06.16.
 */
public class PermissionsManager {

    public static void requestPermissions(Activity activity) {
        if (!isPermissions(activity)) {
            ActivityCompat.requestPermissions(activity, new String[] {android.Manifest.permission.RECORD_AUDIO},
                    Config.PERMISSION_REQUEST_CODE);
        }
    }

    private static boolean isPermissions(Context context) {
        return ActivityCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED;
    }
}

package com.example.navapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.example.navapp.car.NavStatePlugin;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Plugins must be registered before the bridge starts.
        registerPlugin(NavStatePlugin.class);
        super.onCreate(savedInstanceState);
        // Android 13 and later hides the navigation notification unless the app
        // may post notifications. Asked once; GPS still works if it is refused.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.POST_NOTIFICATIONS }, 1);
        }
    }
}

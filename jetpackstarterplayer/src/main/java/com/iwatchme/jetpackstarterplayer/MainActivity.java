package com.iwatchme.jetpackstarterplayer;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.io.File;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;


public class MainActivity extends AppCompatActivity {

    static {
        System.loadLibrary("native-lib");
    }

    private static final int REQUEST_CODE = 100;

    private String path = Environment.getExternalStorageDirectory() + File.separator + "resource/sintel.mp4";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Example of a call to a native method
        Button tv = findViewById(R.id.sample_text);
        tv.setText("Hello FFmpeg");

        tv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                methodRequiresTwoPermission();
                decodeWithPath(path);
            }
        });
    }


    @AfterPermissionGranted(REQUEST_CODE)
    private void methodRequiresTwoPermission() {
        String[] perms = {Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE};
        if (EasyPermissions.hasPermissions(this, perms)) {

        } else {
            // Do not have permissions, request them now
            EasyPermissions.requestPermissions(this, "request permission",
                    REQUEST_CODE, perms);
        }
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */

    public native void decodeWithPath(String path);
}

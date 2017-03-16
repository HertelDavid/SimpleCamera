package com.example.david.simplecamera;

/*
 * Description: A basic Android Camera intent that allows for a restriction on the size of the picture
 * the user can take.
 */

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.TextureView;

public class MainActivity extends AppCompatActivity {

    private final static int CAMERA_PERMISSION = 1;

    private String cameraId;
    private CameraDevice camera;
    private CameraManager cameraManager;
    private String[] cameraIds;

    private final String backgroundThreadName = "Camera Background Thread";
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    private TextureView cameraTextureView;

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice c) {

            //This gets called when the camera has been opened.
            cameraId = c.getId();
            camera = c;
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice c) {

            camera = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Find the Texture View to place what the camera is seeing.
        cameraTextureView = (TextureView) findViewById(R.id.camera_texture_view);
    }

    @Override
    protected void onResume() {
        super.onResume();

        //Get the CameraManager
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        //Get the Ids of all possible cameras connected to the device.
        getCameraIds();

        //Open the camera
        openCamera();
    }

    private void getCameraIds() {

        try {

            cameraIds = cameraManager.getCameraIdList();

        } catch (CameraAccessException e) {

            e.printStackTrace();
        }
    }

    private boolean openCamera() {

        if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){

            requestPermissions();
        }

        //Create the background thread and handler.
        createBackgroundThread();

        try{

            cameraManager.openCamera(cameraIds[0], stateCallback, backgroundHandler);

        } catch (CameraAccessException e) {

            e.printStackTrace();
        }

        return true;
    }

    private void createBackgroundThread(){

        backgroundThread = new HandlerThread(backgroundThreadName);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void requestPermissions(){

        if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){

            //If we don't have the permission we need to request it.
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults){

        //Check to see if the permission was granted.
        if(requestCode == CAMERA_PERMISSION){
            if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){

                //User granted us access
            }else{

                //User did not grant us access
            }
        }
    }
}

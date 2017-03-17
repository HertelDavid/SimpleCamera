package com.example.david.simplecamera;

/*
 * Description: A basic Android Camera intent that allows for a restriction on the size of the picture
 * the user can take.
 */

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Surface;
import android.view.TextureView;

import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    private final static int CAMERA_PERMISSION = 1;
    private final static int MAX_PREVIEW_WIDTH = 1920;
    private final static int MAX_PREVIEW_HEIGHT = 1080;

    private String cameraId;
    private CameraDevice camera;
    private CameraManager cameraManager;
    private CameraCharacteristics thisCameraCharacteristics;
    private StreamConfigurationMap cameraConfigurationMap;
    private String[] cameraIds;

    private final String backgroundThreadName = "Camera Background Thread";
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    private CaptureRequest.Builder captureRequestBuilder;
    private CaptureRequest captureRequest;
    private CameraCaptureSession cameraCaptureSession;
    private TextureView cameraTextureView;
    private SurfaceTexture cameraSurfaceTexture;
    private Surface surface;
    private int textureViewHeight;
    private int textureViewWidth;

    //Callback methods for accessing the CameraDevice.
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice c) {

            //This gets called when the camera has been opened.
            cameraId = c.getId();
            camera = c;
            getCameraCharacteristics();
            createCameraPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice c) {

            //This gets called when the camera has been disconnected.
            cameraId = null;
            camera = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {

            //This method gets called when the camera encounters an error.
            //Should probably disconnect the CameraDevice when this method gets called
            //and show an error.
            throw new RuntimeException("Camera Error: " + error);
        }
    };

    //Listener for TextureView state changes.
    private final TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {

            //Since the Surface Texture is ready now we can acquire the camera.
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    private CameraCaptureSession.CaptureCallback captureCallbackListener = new CameraCaptureSession.CaptureCallback() {

        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);

        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
            super.onCaptureProgressed(session, request, partialResult);

        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);

        }
    };

    private CameraCaptureSession.StateCallback captureSessionStateCallback = new CameraCaptureSession.StateCallback() {

        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {

            //This is called if everything was configured correctly.

            cameraCaptureSession = session;

            try{

                captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);

                captureRequest = captureRequestBuilder.build();

                cameraCaptureSession.setRepeatingRequest(captureRequest, captureCallbackListener, backgroundHandler);


            } catch (CameraAccessException e) {

                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {

            //If the configuration fails.
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

    }

    @Override
    protected void onResume() {
        super.onResume();

        //Find the Texture View to place what the camera is seeing.
        cameraTextureView = (TextureView) findViewById(R.id.camera_texture_view);
        cameraTextureView.setSurfaceTextureListener(surfaceTextureListener);

        //Get the CameraManager
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        //Get the Ids of all possible cameras connected to the device.
        getCameraIds();

    }

    private void getCameraIds() {

        try {

            cameraIds = cameraManager.getCameraIdList();

        } catch (CameraAccessException e) {

            e.printStackTrace();
        }
    }

    private void getCameraCharacteristics(){

        //Method for getting the particular Camera Characteristics.

        if(cameraManager != null && cameraId != null){

            try {

                thisCameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);

            } catch (CameraAccessException e) {

                e.printStackTrace();
            }

        }else{

            throw new NullPointerException();
        }
    }

    private void getStreamConfigurationMap(){

        if(thisCameraCharacteristics != null){

            cameraConfigurationMap = thisCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        }else{

            throw new NullPointerException("Error: " + thisCameraCharacteristics.getClass().toString() + " is null!");
        }
    }

    private boolean openCamera() {

        if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){

            requestPermissions();
            return false;
        }

        //Create the background thread and handler.
        createBackgroundThread();

        try{

            //Open the camera. Opening the camera will call the stateCallback onOpened method.
            cameraManager.openCamera(cameraIds[0], stateCallback, backgroundHandler);

        } catch (CameraAccessException e) {

            //Camera Access denied
            e.printStackTrace();
        }

        return true;
    }

    private void createCameraPreview(){

        SurfaceTexture cameraSurfaceTexture = cameraTextureView.getSurfaceTexture();

        if(cameraSurfaceTexture != null){

            try {

                //Set the buffer size of the SurfaceTexture.
                setDimensions();
                cameraSurfaceTexture.setDefaultBufferSize(textureViewWidth, textureViewHeight);

                //Create the Surface object from the SurfaceTexture.
                surface = new Surface(cameraSurfaceTexture);

                //Create a new Capture Request and also add the Surface as the target.
                captureRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                captureRequestBuilder.addTarget(surface);

                camera.createCaptureSession(Arrays.asList(surface), captureSessionStateCallback, null);

            } catch (CameraAccessException e) {

                e.printStackTrace();
            }

        }else{

            //The surface texture wasn't ready yet.
            throw new NullPointerException("Error: " + SurfaceTexture.class.toString() + " is null!");
        }
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
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);

            }else{

                //User did not grant us access
                //TODO show an error.
            }
        }
    }

    private void setDimensions(){

        if(cameraTextureView != null && cameraTextureView.getWidth() > 0 && cameraTextureView.getHeight() > 0){

            textureViewWidth = cameraTextureView.getWidth();
            textureViewHeight = cameraTextureView.getHeight();
        }else{

            throw new RuntimeException( );
        }
    }
}

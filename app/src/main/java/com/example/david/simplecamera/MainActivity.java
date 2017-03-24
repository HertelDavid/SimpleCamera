package com.example.david.simplecamera;

/*
 * Description: A basic Android Camera intent that allows for a restriction on the size of the picture
 * the user can take.
 */

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Camera;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Semaphore;

public class MainActivity extends AppCompatActivity {

    private final static int CAMERA_PERMISSION = 1;
    private final static int MAX_PREVIEW_WIDTH = 1920;
    private final static int MAX_PREVIEW_HEIGHT = 1080;

    private final static int STATE_PREVIEW = 0;
    private final static int STATE_WAITING_AUTO_FOCUS = 1;
    private final static int STATE_WAITING_LOCK = 2;
    private final static int STATE_WAITING_PRECAPTURE = 3;
    private final static int STATE_WAITING_NON_PRECAPTURE = 4;
    private final static int STATE_PICTURE_TAKEN = 5;

    private String cameraId;
    private CameraDevice camera;
    private CameraManager cameraManager;
    private CameraCharacteristics thisCameraCharacteristics;
    private int cameraCaptureRequestState = STATE_PREVIEW;
    private StreamConfigurationMap cameraConfigurationMap;
    private String[] cameraIds;

    private final String backgroundThreadName = "Camera Background Thread";
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private Semaphore cameraLock = new Semaphore(1);
    private Semaphore captureRequestLock = new Semaphore(1);
    private boolean capturing = false;

    private CaptureRequest.Builder captureRequestBuilder;
    private CaptureRequest captureRequest;
    private CameraCaptureSession cameraCaptureSession;
    private TextureView cameraTextureView;
    private SurfaceTexture cameraSurfaceTexture;
    private ImageReader cameraImageReader;
    private Surface surface;
    private int textureViewHeight;
    private int textureViewWidth;

    //Callback methods for accessing the CameraDevice.
    private final CameraDevice.StateCallback cameraDeviceStateCallback = new CameraDevice.StateCallback() {

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

            return true;
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

            processCapture(result);

        }

        private void processCapture(CaptureResult result){

            switch(cameraCaptureRequestState){
                case STATE_PREVIEW:
                    break;
                case STATE_WAITING_AUTO_FOCUS:
                    autoFocusSet(result);
                    break;
                case STATE_WAITING_LOCK:
                    autoFocusLocked(result);
                    break;
                case STATE_WAITING_PRECAPTURE:
                    waitForPreCapture(result);
                    break;
                default:
                    break;
            }
        }

        private void autoFocusSet(CaptureResult result){

            //Method used to make sure that the Auto Focus routine is set to Auto.
            //Once it's set the state will change so that it can begin focusing.

            Integer autoFocusMode = result.get(CaptureResult.CONTROL_AF_MODE);

            if(autoFocusMode == CaptureResult.CONTROL_AF_MODE_AUTO){

                lockFocus();
            }
        }

        private void autoFocusLocked(CaptureResult result){

            //Auto Focus is working and we should wait until it is done.

            Integer autoFocusState = result.get(CaptureResult.CONTROL_AF_STATE);

            if(autoFocusState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED){

                //The focus is ready.
                Integer autoExposureState = result.get(CaptureResult.CONTROL_AE_STATE);

                if(autoExposureState == null || autoExposureState == CaptureResult.CONTROL_AE_STATE_CONVERGED || autoExposureState == CaptureResult.CONTROL_AE_STATE_INACTIVE){

                    //The auto exposure has converged and indicates that a flash is not required.
                    cameraCaptureRequestState = STATE_PICTURE_TAKEN;
                    captureImage();
                }else{

                    //The auto exposure has determined that more processing needs to be done.
                    preCaptureImage();
                }

            }else if(autoFocusState == null){

                //Focus does not exist for this camera.
                captureImage();

            }
        }

        private void waitForPreCapture(CaptureResult result){

            Integer autoExposureState = result.get(CaptureResult.CONTROL_AE_STATE);

            if(autoExposureState == null || autoExposureState == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED || autoExposureState == CaptureResult.CONTROL_AE_STATE_LOCKED || autoExposureState == CaptureResult.CONTROL_AE_STATE_CONVERGED){

                cameraCaptureRequestState = STATE_PICTURE_TAKEN;
                captureImage();

            }else{

                //The Auto Exposure is still working here.
            }
        }
    };

    private CameraCaptureSession.StateCallback captureSessionStateCallback = new CameraCaptureSession.StateCallback() {

        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {

            //This is called if everything was configured correctly.

            cameraCaptureSession = session;

            try{

                captureRequest = captureRequestBuilder.build();

                cameraCaptureSession.setRepeatingRequest(captureRequest, captureCallbackListener, backgroundHandler);


            } catch (CameraAccessException e) {

                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {

            //If the configuration fails.
            //Throw an error here.
            //TODO different devices require different sizes. Example, some will not be able to support resolutions as high as 1920 x 1080

            throw new RuntimeException("Error: " + session.getClass().toString() + " was not configured properly!");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        hideDecorLayer();
    }

    @Override
    protected void onResume() {
        super.onResume();

        //Hide the decor layer
        hideDecorLayer();

        //Create the Image Reader for acquiring the images and their data.
        cameraImageReader = ImageReader.newInstance(MAX_PREVIEW_WIDTH, MAX_PREVIEW_HEIGHT, ImageFormat.JPEG, 3);

        //Get the CameraManager
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        //Get the Ids of all possible cameras connected to the device.
        getCameraIds();

        //Find the Texture View to place what the camera is seeing.
        cameraTextureView = (TextureView) findViewById(R.id.camera_texture_view);

        if(cameraTextureView.isAvailable()){

            openCamera();
        }else{

            cameraTextureView.setSurfaceTextureListener(surfaceTextureListener);
        }

    }

    @Override
    protected void onPause(){

        super.onPause();

        closeCamera();
        stopBackgroundThread();
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

        //Gets the CameraDevices Stream Configuration Map.
        //TODO this map will be used to more precisely decide how the camera is setup.

        if(thisCameraCharacteristics != null){

            cameraConfigurationMap = thisCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        }else{

            throw new NullPointerException("Error: " + thisCameraCharacteristics.getClass().toString() + " is null!");
        }
    }

    private boolean openCamera() {

        //Opens the camera if the user has granted this activity permission.

        if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){

            requestPermissions();
            return false;
        }

        //Create the background thread and handler.
        startBackgroundThread();

        try{

            //Open the camera. Opening the camera will call the stateCallback onOpened method.
            if(!cameraLock.tryAcquire()){
                System.out.println("This thread was unable to open the camera!");
            }else{
                if(camera == null) {

                    cameraManager.openCamera(cameraIds[0], cameraDeviceStateCallback, backgroundHandler);
                }

                cameraLock.release();
            }

        } catch (CameraAccessException e) {

            //Camera Access denied
            e.printStackTrace();
        }

        return true;
    }

    private void closeCamera(){

        cameraId = null;

        if(camera != null){
            camera.close();
            camera = null;
        }

        if(cameraCaptureSession != null){
            cameraCaptureSession.close();
            cameraCaptureSession = null;
        }

        if(cameraImageReader != null){
            cameraImageReader.close();
            cameraImageReader = null;
        }
    }

    private void createCameraPreview(){

        SurfaceTexture cameraSurfaceTexture = cameraTextureView.getSurfaceTexture();

        if(cameraSurfaceTexture != null){

            try {

                //Set the buffer size of the SurfaceTexture.
                setDimensions();
                cameraSurfaceTexture.setDefaultBufferSize(MAX_PREVIEW_WIDTH, MAX_PREVIEW_HEIGHT);

                //Create the Surface object from the SurfaceTexture.
                surface = new Surface(cameraSurfaceTexture);

                //Create a new Capture Request and also add the Surface as the target.
                captureRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                captureRequestBuilder.addTarget(surface);

                captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH);

                camera.createCaptureSession(Arrays.asList(surface, cameraImageReader.getSurface()), captureSessionStateCallback, null);

            } catch (CameraAccessException e) {

                e.printStackTrace();
            }

        }else{

            //The surface texture wasn't ready yet.
            throw new NullPointerException("Error: " + SurfaceTexture.class.toString() + " is null!");
        }
    }

    public void startImageCapture(View view){

        repeatingRequestForAuto();
    }

    private void repeatingRequestForAuto(){

        //Starts a repeating request with AUTO as the primary auto focus mode.

        try{
            cameraCaptureRequestState = STATE_WAITING_AUTO_FOCUS;
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH);

            cameraCaptureSession.stopRepeating();
            cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), captureCallbackListener, backgroundHandler);

        }catch (CameraAccessException e) {

            e.printStackTrace();
        }
    }

    private void lockFocus(){

        try{
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
            cameraCaptureRequestState = STATE_WAITING_LOCK;
            cameraCaptureSession.capture(captureRequestBuilder.build(), captureCallbackListener, backgroundHandler);

        } catch (CameraAccessException e) {

            e.printStackTrace();
        }
    }

    private void preCaptureImage(){

        /*try{

            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            cameraCaptureSession.capture(captureRequestBuilder.build(), captureCallbackListener, backgroundHandler);
            cameraCaptureRequestState = STATE_WAITING_PRECAPTURE;

        } catch (CameraAccessException e) {
            
            e.printStackTrace();
        }*/
    }

    private void captureImage(){

        try {

            //Create a new builder based off the still capture template.
            CaptureRequest.Builder imageCaptureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);

            //Setup the request.
            imageCaptureBuilder.addTarget(cameraImageReader.getSurface());
            imageCaptureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            imageCaptureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH);

            CameraCaptureSession.CaptureCallback imageCaptureCallback = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);

                    Image image = cameraImageReader.acquireNextImage();

                    System.out.println("Woohoo");

                    unlockFocus();


                }
            };

            cameraCaptureSession.stopRepeating();
            cameraCaptureSession.capture(imageCaptureBuilder.build(), imageCaptureCallback, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void turnOffFlash(){

        try{
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL);
            cameraCaptureSession.capture(captureRequestBuilder.build(), captureCallbackListener, backgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void unlockFocus(){

        try{

            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
            cameraCaptureSession.capture(captureRequestBuilder.build(), captureCallbackListener, backgroundHandler);

            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

            cameraCaptureSession.stopRepeating();
            cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), captureCallbackListener, backgroundHandler);

            cameraCaptureRequestState = STATE_PREVIEW;

        } catch (CameraAccessException e) {

            e.printStackTrace();
        }
    }

    private void startBackgroundThread(){

        if(backgroundThread == null && backgroundHandler == null){

            backgroundThread = new HandlerThread(backgroundThreadName);
            backgroundThread.start();
            backgroundHandler = new Handler(backgroundThread.getLooper());
        }
    }

    private void stopBackgroundThread(){

        backgroundThread.quitSafely();
        backgroundThread = null;
        backgroundHandler = null;
    }

    private void hideDecorLayer(){

        //Method for hiding the decorations on the decorView.
        int options = View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        View decorView = getWindow().getDecorView();

        decorView.setSystemUiVisibility(options);
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

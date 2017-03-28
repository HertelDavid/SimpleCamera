package com.example.david.simplecamera;

//Description: A runnable class that saves an image to the external storage.

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.media.Image;
import android.provider.MediaStore;

import java.nio.ByteBuffer;
import java.util.concurrent.Semaphore;

public class ImageSaver implements Runnable {

    private Image image;
    private ContentResolver contentResolver;
    private Semaphore imageSaveLock = new Semaphore(1);

    public ImageSaver(Image incomingImage, ContentResolver resolver){

        contentResolver = resolver;
        setImage(incomingImage);
    }

    @Override
    public void run() {

        if(imageSaveLock.tryAcquire()){

            saveImage();
            closeImage();
            imageSaveLock.release();

        }else{

            //A thread is trying to save an image on this object without having waited.
        }
    }

    private void saveImage(){

        if(checkForJpeg()){

            Bitmap bitmap = getBitmapFromImage();

            MediaStore.Images.Media.insertImage(contentResolver, bitmap, "img" + System.currentTimeMillis(), " ");
        }
    }

    private Bitmap getBitmapFromImage(){

        ByteBuffer imageBuffer = image.getPlanes()[0].getBuffer();

        byte[] imageBytes = new byte[imageBuffer.remaining()];

        imageBuffer.get(imageBytes);

        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    private void closeImage(){

        image.close();
    }

    private boolean checkForJpeg(){

        return (image.getFormat() == ImageFormat.JPEG);
    }

    public void setImage(Image incomingImage){

        if(incomingImage != null){

            image = incomingImage;

        }else{

            throw new NullPointerException();
        }
    }

    public Image getImage(){

       return image;
    }
}

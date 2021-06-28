package com.android.camera2.store;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.MediaStore;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Save the taken pictures.
 */
public class ImageSaver implements Runnable {
    private byte[] mbytes;
    private String mType;
    private Context mContext;
    private int mWidth;
    private int mHeight;
    private UriInfo mUriInfo;

    public ImageSaver(Context context, byte[] bytes, String type, int width, int height, UriInfo uriInfo) {
        mbytes = bytes;
        mType = type;
        mContext = context;
        mWidth = width;
        mHeight = height;
        mUriInfo = uriInfo;
    }

    public void run() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.MIME_TYPE, mType);
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_" + SystemClock.uptimeMillis());
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM);
        long current = System.currentTimeMillis() / 1000;
        values.put(MediaStore.Images.ImageColumns.DATE_ADDED, current);
        values.put(MediaStore.Images.ImageColumns.DATE_MODIFIED, current);
        values.put(MediaStore.Images.ImageColumns.DATE_TAKEN, current);
        values.put(MediaStore.Images.ImageColumns.WIDTH, mWidth);
        values.put(MediaStore.Images.ImageColumns.HEIGHT, mHeight);
        Uri uri = mContext.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (null != uri) {
            OutputStream outputStream;
            try {
                outputStream = mContext.getContentResolver().openOutputStream(uri);
                if (null != outputStream) {
                    outputStream.write(mbytes);
                    outputStream.close();
                    mUriInfo.UriInfo(uri);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
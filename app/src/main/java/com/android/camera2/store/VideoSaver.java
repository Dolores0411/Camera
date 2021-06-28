package com.android.camera2.store;

import android.content.ContentValues;
import android.content.Context;
import android.os.AsyncTask;
import android.provider.MediaStore;


public class VideoSaver extends AsyncTask {
    private Context mContext;
    private String mVideoPath;
    private String mType;
    private int mWidth;
    private int mHeight;

    public VideoSaver(Context context, String type, int width, int height) {
        mContext = context;
        mType = type;
        mWidth = width;
        mHeight = height;
    }

    @Override
    protected Object doInBackground(Object[] objects) {
        long dateTaken = System.currentTimeMillis();
        String title = "VIDEO_" + dateTaken;
        String filename = title + ".mp4";
        String mimeType = "video/mp4";
        String path = "/storage/emulated/0/DCIM/Camera" + '/' + filename;
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.Video.Media.TITLE, title);
        contentValues.put(MediaStore.Video.Media.MIME_TYPE, mimeType);//type是“video/3gp”
        contentValues.put(MediaStore.Video.Media.DISPLAY_NAME, dateTaken);
        contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Camera");
        long current = System.currentTimeMillis() / 1000;
        contentValues.put(MediaStore.Video.Media.DATE_ADDED, current);
        contentValues.put(MediaStore.Video.Media.DATE_MODIFIED, current);
        contentValues.put(MediaStore.Video.Media.DATE_TAKEN, current);
        contentValues.put(MediaStore.Video.Media.WIDTH, mWidth);
        contentValues.put(MediaStore.Video.Media.HEIGHT, mHeight);
        mVideoPath = path;
        return mVideoPath;
    }

    //作用：接收线程任务执行结果、将执行结果显示到UI组件
    @Override
    protected void onPostExecute(Object o) {
        super.onPostExecute(o);
    }
}

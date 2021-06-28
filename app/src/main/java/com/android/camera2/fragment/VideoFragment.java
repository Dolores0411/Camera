package com.android.camera2.fragment;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
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
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.android.camera2.functions.FlashLight;
import com.android.camera2.parameters.CameraParameter;
import com.android.camera2.ui.AutoFitTextureView;
import com.android.camera2.R;
import com.android.camera2.ui.CreatePopWin;
import com.bumptech.glide.Glide;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;


public class VideoFragment extends Fragment implements View.OnClickListener {

    private static final String TAG = "VideoTwo";

    private static final int MSG = 0x111;

    /**
     * Message for updating text view.
     */
    private static final int UPDATE_TEXTVIEW = 0;

    /**
     * We need to rotate the definite orientation to match the camera.
     */
    private static final int SENSOR_ORIENTATION_DEFAULT_DEGREES = 90;
    private static final int SENSOR_ORIENTATION_INVERSE_DEGREES = 270;
    private static final SparseIntArray DEFAULT_ORIENTATIONS = new SparseIntArray();
    private static final SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();

    static {
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_0, 90);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_90, 0);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_180, 270);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    static {
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0);
    }

    /**
     * Camera id for choosing front-facing camera or rear camera.
     */
    private int mCameraId = 0;

    /**
     * Current video aspect ratio.
     */
    private double mCurrentRatio = 720d / 480;

    /**
     * The sensor orientation.
     */
    private Integer mSensorOrientation;
    private AutoFitTextureView mTextureView;
    private CameraCaptureSession mCaptureSession;
    private CameraDevice mCameraDevice;
    private CaptureRequest.Builder mPreviewRequestBuilder;

    /**
     * Some parameters for time counting.
     */
    private static int mDelay = 0;
    private static int mPeriod = 1000;
    private static int mCount = 0;

    /**
     * The size of camera preview.
     */
    private Size mPreviewSize;

    /**
     * The video thumbnail.
     */
    private ImageView mPreviewImageView;

    /**
     * The size of video recording.
     */
    private Size mVideoSize;

    /**
     * The critical interface of recording
     */
    private MediaRecorder mMediaRecorder;
    private Handler mHandler;
    private HandlerThread mHandlerThread;

    /**
     * A {@link CameraManager} can build connection with camera.
     */
    private CameraManager mCameraManager;

    /**
     * The list of surface, we use it for previewing.
     */
    List<Surface> mSurfaces = new ArrayList<>();

    /**
     * The layout of different phenomena.
     */
    private FrameLayout mRecordLinearLayout;
    private FrameLayout mFunLayout;
    private FrameLayout mConLayout;
    private FrameLayout mTimeRecordLayout;

    /**
     * Control the status of recording.
     */
    private ImageView mRecord;
    private ImageView mVideoPause;

    /**
     * Mark the current ratio.
     */
    private static int mCurrentQuality = CamcorderProfile.QUALITY_480P;

    /**
     * Get {@link CamcorderProfile} to save some information about audio and video.
     */
    private CamcorderProfile mProfile;

    /**
     * The saving path of video.
     */
    private String mVideoAbsolutePath;

    /**
     * Configure parameters of video.
     */
    private ContentValues mCurrentVideoValues;

    /**
     * Process communication for file.
     */
    private ParcelFileDescriptor mVideoFDFromMedia = null;

    /**
     * Process communication with uri.
     */
    private Uri mVideoUri;
    private Uri mTempUri;

    /**
     * Configure some values of {@link PopupWindow}.
     */
    private CreatePopWin mCreatePopWin;
    private ImageView mFlashLight;
    private TextView mScale;
    private ImageView mSettings;
    private PopupWindow mFlashPop;
    private PopupWindow mScalePop;

    /**
     * Values to mark the status of flash.
     */
    private static int mFlashStatus = CameraParameter.FLASHOFF;
    private FlashLight mFlash;

    /**
     * Counting time for recording.
     */
    private TimerTask mTimerTask = null;
    private TextView mTimeText;
    private Timer mTimer = null;

    /**
     * Mark the status when the video pauses or works.
     */
    private boolean mPaused = false;


    /**
     * Mark the status when the video starts or stops.
     */
    private boolean mIsRecordingVideo = true;

    /**
     * Handle message for updating and showing thumbnail.
     */
    private Handler mUiHandler = new Handler(Looper.getMainLooper()) {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UPDATE_TEXTVIEW:
                    updateTextView();
                    break;
                case MSG:
                    Bitmap bitmap = (Bitmap) msg.obj;
                    Glide.with(Objects.requireNonNull(getActivity())).load(bitmap)
                            .error(R.drawable.ic_none).circleCrop().into(mPreviewImageView);
                    mPreviewImageView.setVisibility(View.VISIBLE);
                    break;
                default:
                    break;
            }
        }
    };


    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_video, container, false);
        mTextureView = (AutoFitTextureView) view.findViewById(R.id.texture);
        mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        mRecord = view.findViewById(R.id.record);
        mPreviewImageView = view.findViewById(R.id.video_save);
        ImageView cameraRotation = view.findViewById(R.id.camera_rotation);
        mRecordLinearLayout = view.findViewById(R.id.record_layout);
        mFunLayout = view.findViewById(R.id.functions_layout);
        mConLayout = view.findViewById(R.id.control);
        mTimeRecordLayout = view.findViewById(R.id.time_flag);
        mTimeText = view.findViewById(R.id.chr_time_record);
        mVideoPause = view.findViewById(R.id.video_pause);
        mFlashLight = view.findViewById(R.id.flash_light);
        mScale = view.findViewById(R.id.scale);
        mSettings = view.findViewById(R.id.settings);
        ImageView videoStop = view.findViewById(R.id.video_stop);
        ImageView videoCapture = view.findViewById(R.id.video_capture);
        mRecord.setOnClickListener(this);
        mPreviewImageView.setOnClickListener(this);
        cameraRotation.setOnClickListener(this);
        mFlashLight.setOnClickListener(this);
        mVideoPause.setOnClickListener(this);
        mScale.setOnClickListener(this);
        mSettings.setOnClickListener(this);
        videoStop.setOnClickListener(this);
        videoCapture.setOnClickListener(this);
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mProfile = getCamcorderProfile(mCameraId, CamcorderProfile.QUALITY_480P);
        Log.d(TAG, "The mProfile width is " + mProfile.videoFrameWidth + ", and the height is "
                + mProfile.videoFrameHeight);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onPause() {
        closeCamera();
        stopHandlerThread();
        mSurfaces.clear();
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        startHandlerThread();
        if (mTextureView.isAvailable()) {
            try {
                openCamera(mCurrentRatio);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    /**
     * Monitor the status of {@link TextureView}.
     */
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
            try {
                Log.d(TAG, "Surface is ready!");
                openCamera(mCurrentRatio);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

        }
    };

    /**
     * To get the optimal size of previewing size.
     *
     * @param choices The list of previewing size.
     * @return The optimal size.
     */
    private static Size chooseVideoSize(Size[] choices, double ratio) {
        for (Size size : choices) {
            if (size.getWidth() == size.getHeight() * ratio && size.getWidth() <= 1080) {
                return size;
            }
        }
        return choices[choices.length - 1];
    }

    /**
     * The capture handler.
     */
    private void startHandlerThread() {
        mHandlerThread = new HandlerThread("CameraHandler");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
    }

    /**
     * Stops the mHandler thread and its {@link Handler}.
     */
    private void stopHandlerThread() {
        mHandlerThread.quitSafely();
        try {
            mHandlerThread.join();
            mHandlerThread = null;
            mHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Open camera.
     *
     * @param ratio Aspect ratio.
     * @throws CameraAccessException
     */
    private void openCamera(double ratio) throws CameraAccessException {
        setUpCameraOutputs(ratio);
        forOpenCamera(mCameraId);
    }

    /**
     * Adjust preview size.
     *
     * @param ratio Aspect ratio.
     */
    private void setUpCameraOutputs(double ratio) {
        Activity activity = getActivity();
        assert activity != null;
        CameraManager cameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics cameraCharacteristics
                    = cameraManager.getCameraCharacteristics(String.valueOf(mCameraId));

            mSensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

            //Set preview and video sizes.
            StreamConfigurationMap streamConfigurationMap
                    = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (null == streamConfigurationMap) {
                throw new RuntimeException("Cannot get available size!");
            }
            mVideoSize = chooseVideoSize(streamConfigurationMap.getOutputSizes(MediaRecorder.class), ratio);
            mPreviewSize = chooseOptimalSize(streamConfigurationMap.getOutputSizes(SurfaceTexture.class),
                    ratio, mVideoSize);

            //To adjust width and height matching the real width and height.
            int orientation = getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                mTextureView.setAspectRatio(
                        mPreviewSize.getWidth(), mPreviewSize.getHeight());
            } else {
                mTextureView.setAspectRatio(
                        mPreviewSize.getHeight(), mPreviewSize.getWidth());
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Open camera.
     *
     * @param cameraid Represents the orientation of camera.
     * @throws CameraAccessException
     */
    private void forOpenCamera(int cameraid) throws CameraAccessException {
        Activity activity = getActivity();
        assert activity != null;
        mCameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    Activity#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for Activity#requestPermissions for more details.
                return;
            }

            mCameraManager.openCamera(String.valueOf(cameraid), mStateCallback, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * To get the optimal size of previewing size.
     *
     * @param choices     The list of previewing size.
     * @param ratio       Surface ratio.
     * @param aspectRatio Aspect ratio.
     * @return The optimal size.
     */
    private Size chooseOptimalSize(Size[] choices, double ratio, Size aspectRatio) {
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w &&
                    Math.abs((double) option.getWidth() / (double) option.getHeight()) - ratio <= 0.001) {
                return option;
            }
        }
        return null;
    }

    /**
     * The way is called when the status of camera is changed.
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Log.d(TAG, "Camera can open.");
            mCameraDevice = camera;
            initMediaRecorder();
            mHandlerThread = new HandlerThread("Camera2");
            mHandlerThread.start();
            mHandler = new Handler(mHandlerThread.getLooper());
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            Log.d(TAG, "Camera has disconnected.");
            mCameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.d(TAG, "There is some errors on camera.");
            mCameraDevice.close();
            mCameraDevice = null;
            Activity activity = getActivity();
            if (activity != null) {
                activity.finish();
            }
        }
    };

    /**
     * Create session.
     */
    private void createCameraPreviewSession() {
        try {
            closeSession();
            SurfaceTexture mSurfaceTexture = mTextureView.getSurfaceTexture();

            //Adjust the aspect ratio of texture view
            mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            // We configure the size of default buffer to be the size of camera preview we want.
            assert mSurfaceTexture != null;
            mSurfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            //set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder
                    = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            Surface surface = new Surface(mSurfaceTexture);
            mPreviewRequestBuilder.addTarget(surface);
            mSurfaces.add(surface);
            Surface recorderSurface = mMediaRecorder.getSurface();
            mPreviewRequestBuilder.addTarget(recorderSurface);
            mSurfaces.add(recorderSurface);

            //create the capture session
            mCameraDevice.createCaptureSession(mSurfaces, new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    if (mCameraDevice == null) {
                        return;
                    } else {
                        mCaptureSession = session;
                        try {
                            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                            mFlash = new FlashLight(mPreviewRequestBuilder);
                            mFlash.startFlashLight(mFlashStatus);
                            if (null != mCaptureSession) {
                                mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(),
                                        new CameraCaptureSession.CaptureCallback() {
                                            @Override
                                            public void onCaptureStarted(@NonNull CameraCaptureSession session,
                                                                         @NonNull CaptureRequest request, long timestamp, long frameNumber) {
                                                super.onCaptureStarted(session, request, timestamp, frameNumber);
                                            }

                                            @Override
                                            public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                                                            @NonNull CaptureRequest request,
                                                                            @NonNull CaptureResult partialResult) {
                                                super.onCaptureProgressed(session, request, partialResult);
                                            }

                                            @Override
                                            public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                                                           @NonNull CaptureRequest request,
                                                                           @NonNull TotalCaptureResult result) {
                                                super.onCaptureCompleted(session, request, result);
                                            }
                                        }, mHandler);
                            }
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    session.close();
                    mCaptureSession = null;
                    mCameraDevice.close();
                    mCameraDevice = null;
                    Activity activity = getActivity();
                    Toast.makeText(activity, "Configuration failed!", Toast.LENGTH_SHORT).show();

                }
            }, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    /**
     * Get the object of CamcorderProfile.
     *
     * @param cameraId Represents the orientation of camera.
     * @param quality  A video parameter.
     * @return CamcorderProfile.get(cameraId, quality).
     */
    protected CamcorderProfile getCamcorderProfile(int cameraId, int quality) {
        return CamcorderProfile.get(cameraId, quality);
    }

    /**
     * Initialize MediaRecorder and configure parameters.
     */
    private void initMediaRecorder() {
        mMediaRecorder = new MediaRecorder();
        mProfile = getCamcorderProfile(mCameraId, mCurrentQuality);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setOutputFormat(mProfile.fileFormat);
        //audio encoder format
        mMediaRecorder.setAudioEncoder(mProfile.audioCodec);
        //video encoder format
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setVideoEncodingBitRate(mProfile.videoBitRate);
        mMediaRecorder.setVideoFrameRate(mProfile.videoFrameRate);
        mMediaRecorder.setVideoSize(mProfile.videoFrameWidth, mProfile.videoFrameHeight);
        mMediaRecorder.setAudioEncodingBitRate(mProfile.audioBitRate);
        mMediaRecorder.setAudioChannels(mProfile.audioChannels);
        mMediaRecorder.setAudioSamplingRate(mProfile.audioSampleRate);
        Activity activity = getActivity();
        int rotation = activity.getDisplay().getRotation();
        switch (mSensorOrientation) {
            case SENSOR_ORIENTATION_DEFAULT_DEGREES:
                mMediaRecorder.setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation));
                break;
            case SENSOR_ORIENTATION_INVERSE_DEGREES:
                mMediaRecorder.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation));
                break;
        }
        generateVideo();
        setOutputUsingUri(mCurrentVideoValues);
        try {
            mMediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Get bitmap of the first frame of video.
     *
     * @param videoPath The saving path of video.
     * @return Bitmap.
     */
    public static Bitmap getVideoThumbnail(String videoPath) {
        Bitmap bitmap = null;
        bitmap = ThumbnailUtils.createVideoThumbnail(videoPath, MediaStore.Images.Thumbnails.MINI_KIND);
        if (bitmap != null) {
            bitmap = ThumbnailUtils.extractThumbnail(bitmap, 60, 60,
                    ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
        }
        return bitmap;
    }

    /**
     * Generate a path to save video.
     */
    private void generateVideo() {
        long dateTaken = System.currentTimeMillis();
        long currentTime = dateTaken / 1000;
        String title = "VID_" + dateTaken;
        String filename = getFileName(dateTaken);
        String mimeType = "video/mp4";
        String path = "/storage/emulated/0/DCIM/Camera" + "/" + filename;
        mCurrentVideoValues = new ContentValues();
        mCurrentVideoValues.put(MediaStore.Video.Media.TITLE, title);
        mCurrentVideoValues.put(MediaStore.Video.Media.DISPLAY_NAME, filename);
        mCurrentVideoValues.put(MediaStore.Video.Media.DATE_TAKEN, dateTaken);
        mCurrentVideoValues.put(MediaStore.MediaColumns.DATE_MODIFIED, currentTime);
        mCurrentVideoValues.put(MediaStore.Video.Media.DATE_TAKEN, currentTime);
        mCurrentVideoValues.put(MediaStore.Video.Media.MIME_TYPE, mimeType);
        mCurrentVideoValues.put(MediaStore.Video.Media.DATA, path);
        mCurrentVideoValues.put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Camera");

        //Other progresses cannot be accessible to this, because the data is being written.
        mCurrentVideoValues.put(MediaStore.MediaColumns.IS_PENDING, "1");
        mCurrentVideoValues.put(MediaStore.Video.Media.WIDTH, mProfile.videoFrameWidth);
        mCurrentVideoValues.put(MediaStore.Video.Media.HEIGHT, mProfile.videoFrameHeight);
    }

    /**
     * Use uri to mark the video.
     *
     * @param contentValues The configuration of video.
     */
    private void setOutputUsingUri(ContentValues contentValues) {
        Activity activity = getActivity();
        if (null != contentValues) {
            try {
                assert activity != null;
                mVideoUri = activity.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        contentValues);
                Log.d(TAG, "insert uri is " + mVideoUri);
                if (mVideoUri != null) {
                    mVideoFDFromMedia = activity.getContentResolver().openFileDescriptor(mVideoUri, "rw");
                    Log.d(TAG, "video ParcelFileDescriptor is " + mVideoFDFromMedia);
                    if (mVideoFDFromMedia != null) {
                        mMediaRecorder.setOutputFile(mVideoFDFromMedia.getFileDescriptor());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "Failed to set MediaRecorder output file with uri");
            }
        }
    }

    /**
     * Get the file name according to time.
     *
     * @param time Current time.
     * @return File name.
     */
    public String getFileName(long time) {
        @SuppressLint("SimpleDateFormat") SimpleDateFormat simpleDateFormat
                = new SimpleDateFormat("yyyyMMdd_HHmmss");
        Date date = new Date(time);
        String dateStr = simpleDateFormat.format(date);
        return "VID_" + dateStr + ".mp4";
    }

    /**
     * Save the video.
     */
    private Uri saveVideo() {
        final long curTime = System.currentTimeMillis();
        final String displayName = getFileName(curTime);

        //it's accessible to other progresses
        mCurrentVideoValues.put(MediaStore.MediaColumns.IS_PENDING, "0");
        final ContentValues currentVideoValues = mCurrentVideoValues;
        final Uri updateUri = mVideoUri;
        currentVideoValues.put(MediaStore.MediaColumns.DATE_MODIFIED, curTime / 1000);
        currentVideoValues.put(MediaStore.Video.Media.DISPLAY_NAME, displayName);
        try {
            Activity activity = getActivity();
            assert activity != null;
            activity.getContentResolver().update(updateUri, currentVideoValues, null, null);
        } catch (Exception e) {
            Log.e(TAG, "Fail to update display_name to stop video time" + e.getMessage());
        }
        mVideoAbsolutePath = "/storage/emulated/0/DCIM/Camera" + "/" + displayName;
        return updateUri;
    }

    /**
     * Show the thumbnail of video.
     */
    private void VideoThumbnail() {
        Bitmap bitmap = getVideoThumbnail(mVideoAbsolutePath);
        mUiHandler.obtainMessage(MSG, bitmap).sendToTarget();

        Activity activity = getActivity();
        if (null != activity) {
            Toast.makeText(activity, "Video saved: " + mVideoAbsolutePath, Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Video saved: " + mVideoAbsolutePath);
            mVideoAbsolutePath = null;
        }
    }

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.video_stop:
            case R.id.record:
                if (!mIsRecordingVideo) {
                    mTimeRecordLayout.setVisibility(View.GONE);
                    mRecordLinearLayout.setVisibility(View.GONE);
                    mFunLayout.setVisibility(View.VISIBLE);
                    mConLayout.setVisibility(View.VISIBLE);
                    stopRecording();
                    mTempUri = saveVideo();
                    VideoThumbnail();
                    initMediaRecorder();
                    createCameraPreviewSession();
                } else {
                    try {
                        mTimeRecordLayout.setVisibility(View.VISIBLE);
                        mRecordLinearLayout.setVisibility(View.VISIBLE);
                        mFunLayout.setVisibility(View.GONE);
                        mConLayout.setVisibility(View.GONE);
                        record();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                break;
            case R.id.camera_rotation:
                try {
                    stopRecord();
                    closeCamera();
                    mCameraId ^= 1;
                    forOpenCamera(mCameraId);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
                break;
            case R.id.video_save:
                Intent startGallery = new Intent(Intent.ACTION_VIEW);
                startGallery.setData(mTempUri);
                startGallery.putExtra("camera_album", true);
                startGallery.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(startGallery);
                break;
            case R.id.video_pause:
                if (!mPaused) {
                    pauseRecord();
                } else {
                    resumeRecord();
                }
                break;
            case R.id.video_capture:
                break;
            case R.id.flash_light:
                if (null != Objects.requireNonNull(getActivity()).getBaseContext()) {
                    mCreatePopWin = new CreatePopWin(getActivity().getBaseContext(), mFlashLight);
                    View flashLayout = mCreatePopWin.createPopupWindow(R.layout.flash_popupwindow_video);
                    mFlashPop = mCreatePopWin.getPopupWindow();
                    flashLayout.setOnClickListener(this);
                    ImageView flashOff = flashLayout.findViewById(R.id.flash_close);
                    ImageView flashHigh = flashLayout.findViewById(R.id.flash_highlight);
                    flashOff.setOnClickListener(this);
                    flashHigh.setOnClickListener(this);
                }
                break;
            case R.id.scale:
                mCreatePopWin = new CreatePopWin(Objects.requireNonNull(getActivity()).getBaseContext(), mScale);
                View scaleLayout = mCreatePopWin.createPopupWindow(R.layout.quality_popupwindow_video);
                mScalePop = mCreatePopWin.getPopupWindow();
                TextView tvQualitySmall = scaleLayout.findViewById(R.id.tv_quality_small);
                TextView tvQualityLarge = scaleLayout.findViewById(R.id.tv_quality_large);
                tvQualitySmall.setOnClickListener(this);
                tvQualityLarge.setOnClickListener(this);
                break;
            case R.id.settings:
                mCreatePopWin = new CreatePopWin(Objects.requireNonNull(getActivity()).getBaseContext(), mSettings);
                View settingsLayout = mCreatePopWin.createPopupWindow(R.layout.settings_popupwindow);
                ImageView ivLocation = settingsLayout.findViewById(R.id.iv_location);
                ImageView ivQRcode = settingsLayout.findViewById(R.id.iv_QR_code);
                ImageView ivDelayedThree = settingsLayout.findViewById(R.id.iv_delayed_three);
                ImageView ivDelayedSix = settingsLayout.findViewById(R.id.iv_delayed_six);
                ivLocation.setOnClickListener(this);
                ivQRcode.setOnClickListener(this);
                ivDelayedThree.setOnClickListener(this);
                ivDelayedSix.setOnClickListener(this);
                break;
            case R.id.flash_close:
                mFlashPop.dismiss();
                if (mFlashStatus != CameraParameter.FLASHOFF) {
                    mFlashStatus = CameraParameter.FLASHOFF;
                    mFlashLight.setImageResource(R.drawable.ic_flash_close);
                    closeSession();
                    createCameraPreviewSession();
                }
                break;
            case R.id.flash_highlight:
                mFlashPop.dismiss();
                if (mFlashStatus != CameraParameter.FLASHTORCH) {
                    mFlashStatus = CameraParameter.FLASHTORCH;
                    mFlashLight.setImageResource(R.drawable.ic_flash_highlight);
                    closeSession();
                    createCameraPreviewSession();
                }
                break;
            case R.id.tv_quality_small:
                mScalePop.dismiss();
                if (mCurrentQuality != CamcorderProfile.QUALITY_480P) {
                    mScale.setText("480p");
                    mCurrentQuality = CamcorderProfile.QUALITY_480P;
                    try {
                        adjustQuality(mCurrentQuality);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
                break;
            case R.id.tv_quality_large:
                mScalePop.dismiss();
                if (mCurrentQuality != CamcorderProfile.QUALITY_720P) {
                    mScale.setText("720p");
                    mCurrentQuality = CamcorderProfile.QUALITY_720P;
                    try {
                        adjustQuality(mCurrentQuality);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
                break;
        }
    }

    /**
     * Adjust ratio according to quality.
     *
     * @param quality The video quality we need.
     * @throws CameraAccessException
     */
    private void adjustQuality(int quality) throws CameraAccessException {
        closeSession();
        closeCamera();
        if (quality == CamcorderProfile.QUALITY_480P) {
            mProfile = getCamcorderProfile(mCameraId, CamcorderProfile.QUALITY_480P);
            mCurrentRatio = (double) mProfile.videoFrameWidth / mProfile.videoFrameHeight;
        } else if (quality == CamcorderProfile.QUALITY_720P) {
            mProfile = getCamcorderProfile(mCameraId, CamcorderProfile.QUALITY_720P);
            mCurrentRatio = (double) mProfile.videoFrameWidth / mProfile.videoFrameHeight;
        }
        openCamera(mCurrentRatio);
    }

    /**
     * Start recording.
     *
     * @throws IOException
     */
    private void record() throws IOException {
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED) ||
                null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            Activity activity = getActivity();
            Toast.makeText(activity, "Error!", Toast.LENGTH_SHORT).show();
            return;
        } else {
            mIsRecordingVideo = false;
            mMediaRecorder.start();
            startTimer();
        }
    }

    public static VideoFragment newInstance() {
        return new VideoFragment();
    }

    /**
     * Pause recording.
     */
    private void pauseRecord() {
        mPaused = true;
        mVideoPause.setImageResource(R.drawable.ic_video_play);
        mMediaRecorder.resume();
    }

    /**
     * Resume recording.
     */
    private void resumeRecord() {
        mPaused = false;
        mVideoPause.setImageResource(R.drawable.ic_video_pause);
        mMediaRecorder.pause();
    }

    /**
     * Counting thread.
     */
    private void startTimer() {
        if (null == mTimer) {
            mTimer = new Timer();
        }
        if (null == mTimerTask) {
            mTimerTask = new TimerTask() {
                @Override
                public void run() {
                    sendMessage(UPDATE_TEXTVIEW);
                    do {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                        }
                    } while (mPaused);
                    mCount++;

                }
            };
        }
        if (null != mTimer) {
            mTimer.schedule(mTimerTask, mDelay, mPeriod);
        }
    }

    /**
     * Send message to update time counting text.
     *
     * @param id Message for informing updating text view.
     */
    public void sendMessage(int id) {
        if (mUiHandler != null) {
            Message message = Message.obtain(mUiHandler, id);
            mUiHandler.sendMessage(message);
        }
    }

    /**
     * Update the text of time counting.
     */
    private void updateTextView() {
        mTimeText.setText(getStringTime(mCount));
        if (null != getContext()) {
            mTimeText.setTextColor(getContext().getResources().getColor(R.color.colorWhite));
        }
    }

    /**
     * Set the format of the recording time.
     *
     * @param count Actual time.
     * @return The format of time.
     */
    private String getStringTime(int count) {
        int min = count % 3600 / 60;
        int second = count % 60;
        return String.format(Locale.CHINA, "%02d:%02d", min, second);
    }

    /**
     * Cancel Timer for next counting.
     */
    private void stopRecording() {
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }
        if (mTimerTask != null) {
            mTimerTask.cancel();
            mTimerTask = null;
        }
        mCount = 0;
        mIsRecordingVideo = true;
        mMediaRecorder.stop();
        mMediaRecorder.reset();
        stopRecord();
    }

    /**
     * Clear surface and stop recording.
     */
    public void stopRecord() {
        if (null != mMediaRecorder) {
            mMediaRecorder.release();
            mMediaRecorder = null;
            mSurfaces.clear();
        }
    }

    /**
     * Close camera.
     */
    public void closeCamera() {
        if (null != mCaptureSession) {
            mCaptureSession.close();
            mCaptureSession = null;
        }
        if (null != mCameraDevice) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }

    /**
     * Close {@link CameraCaptureSession} for stopping previewing.
     */
    private void closeSession() {
        if (null != mCaptureSession) {
            try {
                mCaptureSession.abortCaptures();
                mCaptureSession.stopRepeating();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            mSurfaces.clear();
            mCaptureSession.close();
            mCaptureSession = null;
        }
    }
}

package com.android.camera2.fragment;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
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
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.android.camera2.R;
import com.android.camera2.functions.FlashLight;
import com.android.camera2.parameters.CameraParameter;
import com.android.camera2.store.ImageSaver;
import com.android.camera2.store.UriInfo;
import com.android.camera2.ui.AutoFitTextureView;
import com.android.camera2.ui.CreatePopWin;
import com.bumptech.glide.Glide;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class CameraFragment extends Fragment implements View.OnClickListener {

    private static final String TAG = "CameraTwo";

    private static final int MSG = 0x123;

    /**
     * Camera state: Showing camera preview.
     */
    private static final int STATE_PREVIEW = 0;

    /**
     * Camera state: Waiting for the focus to be locked.
     */
    private static final int STATE_WAITING_LOCK = 1;

    /**
     * Camera state: Waiting for the exposure to be precapture state.
     */
    private static final int STATE_WAITING_PRECAPTURE = 2;

    /**
     * Camera state: Waiting for the exposure state to be something other than precapture.
     */
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;

    /**
     * Camera state: Picture was taken.
     */
    private static final int STATE_PICTURE_TAKEN = 4;

    /**
     * We need to rotate the definite orientation to match the camera.
     */
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    /**
     * The actual camera orientation.
     */
    private int mSensorOrientation;
    private AutoFitTextureView mTextureView;
    private CameraDevice mCameraDevice;

    /**
     * Mark the size of previewing.
     */
    private Size mPreviewSize, mCaptureSize;
    private ImageView mPreviewImageView;

    /**
     * The {@link CaptureRequest} for controlling.
     */
    private CaptureRequest mPreviewRequest;

    /**
     * Values to operate camera.
     */
    private int mCameraId = 0;
    private CameraManager mCameraManager;
    private CameraCaptureSession mCaptureSession;
    private CaptureRequest.Builder mPreviewRequestBuilder;

    /**
     * Values to mark the status of flash.
     */
    private static int mFlashStatus = CameraParameter.FLASHON;
    private FlashLight mFlash;

    /**
     * Mark the current ratio.
     */
    private static double mCurrentRatio = CameraParameter.FOURTOTHREE;

    /**
     * Mark the uri of the target.
     */
    private Uri mImageUri;
    private ImageReader mImageReader;

    /**
     * An interface to get Uri.
     */
    private UriInfo mUriCallBack = new UriInfo() {
        @Override
        public void UriInfo(Uri uri) {
            mImageUri = uri;
        }
    };

    /**
     * A thread for showing thumbnail.
     */
    private Handler mUiHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case MSG:
                    Bitmap bitmap = (Bitmap) msg.obj;
                    Log.d(TAG, "The bitmap can be shown.");
                    Glide.with(getActivity()).load(bitmap).error(R.drawable.ic_none).circleCrop().into(mPreviewImageView);
                    mPreviewImageView.setVisibility(View.VISIBLE);
                    break;
            }
        }
    };

    /**
     * Some parameters of {@link PopupWindow}.
     */
    private CreatePopWin mCreatePopWin;
    private ImageView mFlashlight;
    private TextView mScale;
    private ImageView mSettings;
    private PopupWindow mFlashPop;
    private PopupWindow mScalePop;

    /**
     * A thread for previewing.
     */
    private Handler mHandler;
    private HandlerThread mHandlerThread;

    /**
     * The current state of camera state for taking pictures.
     */
    private int mCurrentState = STATE_PREVIEW;

    /**
     * A thread for saving pictures.
     */
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundHandlerThread;

    /**
     * Scale this picture to thumbnail.
     */
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireNextImage();
            if (image != null) {
                ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();//获取该图像的像素平面数组
                final byte[] data = new byte[byteBuffer.remaining()];
                byteBuffer.get(data);
                Activity activity = getActivity();
                mBackgroundHandler.post(new ImageSaver(activity, data, "image/jpeg", image.getWidth(), image.getHeight(), mUriCallBack));
                Bitmap picture = BitmapFactory.decodeByteArray(data, 0, data.length);
                int width = picture.getWidth();
                int height = picture.getHeight();
                Matrix matrix = new Matrix();
                float scaleWidth = ((float) width / image.getWidth());
                float scaleHeight = ((float) height / image.getHeight());
                matrix.postScale(scaleWidth, scaleHeight);
                if (mCameraId == 0) {
                    matrix.postRotate(90);
                } else {
                    matrix.postRotate(270);
                }
                Bitmap bitmap = Bitmap.createBitmap(picture, 0, 0, width, height, matrix, true);
                mUiHandler.obtainMessage(MSG, bitmap).sendToTarget();
                image.close();
            }
        }
    };

    /**
     * Monitor the status of {@link TextureView}.
     */
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
            try {
                openCamera(mCurrentRatio);
                Log.d(TAG, "Camera surface is ready");
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
            Log.d(TAG, "The size of surface has changed");
        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

        }
    };

    /**
     * Receive the status of the camera.
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Log.d(TAG, "Camera can open.");
            mCameraDevice = camera;
            mHandlerThread = new HandlerThread("Camera2");
            mHandlerThread.start();
            mHandler = new Handler(mHandlerThread.getLooper());
            mBackgroundHandlerThread = new HandlerThread("Capture");
            mBackgroundHandlerThread.start();
            mBackgroundHandler = new Handler(mBackgroundHandlerThread.getLooper());
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            Log.d(TAG, "Camera has disconnected.");
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.d(TAG, "There is some errors on camera.");
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            Activity activity = getActivity();
            if (activity != null) {
                activity.finish();
            }
        }
    };


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_camera, container, false);
        mTextureView = (AutoFitTextureView) view.findViewById(R.id.texture);
        mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        ImageView imageView = view.findViewById(R.id.capture);
        mFlashlight = view.findViewById(R.id.flash_light);
        mScale = view.findViewById(R.id.scale);
        mSettings = view.findViewById(R.id.settings);
        mPreviewImageView = view.findViewById(R.id.image_save);
        ImageView cameraRotation = view.findViewById(R.id.camera_rotation);
        cameraRotation.setOnClickListener(this);
        imageView.setOnClickListener(this);
        mPreviewImageView.setOnClickListener(this);
        mFlashlight.setOnClickListener(this);
        mScale.setOnClickListener(this);
        mSettings.setOnClickListener(this);
        return view;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onPause() {
        closeCamera();
        stopHandlerThread();
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        try {
            startHandlerThread();
            if (mTextureView.isAvailable()) {
                openCamera(mCurrentRatio);
            } else {
                mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
            }
        } catch (CameraAccessException e) {
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
     * The callback of {@link CameraCaptureSession}.
     *
     */
    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {
        private void process(CaptureResult result) {
            switch (mCurrentState) {
                case STATE_PREVIEW: {
                    // We have nothing to do when the camera preview is working normally.
                    break;
                }
                case STATE_WAITING_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState == null) {
                        Log.d(TAG, "TATE_WAITING_LOCK");
                        captureStillPicture();
                    } else if (CaptureResult.CONTROL_AF_STATE_INACTIVE == afState ||
                            CaptureRequest.CONTROL_AF_STATE_PASSIVE_SCAN == afState ||
                            CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        // CONTROL_AE_STATE can be null on some devices
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (aeState == null ||
                                aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            mCurrentState = STATE_PICTURE_TAKEN;
                            captureStillPicture();
                        } else {
                            runPrecaptureSequence();
                        }
                    }
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mCurrentState = STATE_WAITING_NON_PRECAPTURE;
                    }
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mCurrentState = STATE_PICTURE_TAKEN;
                        captureStillPicture();
                    }
                    break;
                }
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            process(result);
        }
    };

    /**
     * Run the precapture sequence for capturing a still image. This method should be called when
     * we get a response in {@link #mCaptureCallback} from {@link #lockFocus()}.
     */
    private void runPrecaptureSequence() {
        try {
            // This is how to tell the camera to trigger.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the precapture sequence to be set.
            mCurrentState = STATE_WAITING_PRECAPTURE;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Create preview {@link CameraCaptureSession}.
     */
    private void createCameraPreviewSession() {
        try {
            SurfaceTexture mSurfaceTexture = mTextureView.getSurfaceTexture();

            //adjust the aspect ratio of texture view
            mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            mSurfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            //set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder
                    = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            Surface surface = new Surface(mSurfaceTexture);
            mPreviewRequestBuilder.addTarget(surface);

            //create the capture session
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            if (mCameraDevice == null) {
                                return;
                            } else {
                                mCaptureSession = session;
                                try {
                                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                                    //configure the AE mode
                                    mFlash = new FlashLight(mPreviewRequestBuilder);
                                    mFlash.startFlashLight(mFlashStatus);
                                    mPreviewRequest = mPreviewRequestBuilder.build();
                                    if (mCaptureSession != null)
                                        mCaptureSession.setRepeatingRequest(mPreviewRequest,
                                                mCaptureCallback, mHandler);
                                } catch (CameraAccessException e) {
                                    e.printStackTrace();
                                }
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Activity activity = getActivity();
                            Toast.makeText(activity, "Configuration failed!", Toast.LENGTH_SHORT).show();

                        }
                    }, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Adjust preview size.
     *
     * @param ratio Aspect ratio.
     */
    private void setUpCameraOutputs(double ratio) {
        Activity activity = getActivity();
        assert activity != null;
        mCameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics cameraCharacteristics
                    = mCameraManager.getCameraCharacteristics(String.valueOf(mCameraId));

            //We can't get the list of sizes from CameraCharacteristics directly. We should get StreamConfigurationMap first from SCALER_STREAM_CONFIGURATION_MAP,
            // Then we acquire the list of sizes through StreamConfigurationMap.getOutputSizes(),and it requires the type of parameter is Class.
            // And you can get the list from the parameter.If it doesn't support it, it will give you a value of null, you can estimate it through StreamConfigurationMap.isOutputSupportedFor()
            StreamConfigurationMap streamConfigurationMap
                    = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] sizes = streamConfigurationMap.getOutputSizes(ImageFormat.YUV_420_888);

            //to find out if we should adjust the orientation to matching the actual orientation
            mSensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

            // the actual width of window
            DisplayMetrics displayMetrics = getResources().getDisplayMetrics();

            int widthPixels = displayMetrics.widthPixels;
            mPreviewSize = chooseOptimalSize(sizes, ratio, widthPixels);

            //to adjust width and height matching the real width and height
            int orientation = getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                mTextureView.setAspectRatio(
                        mPreviewSize.getWidth(), mPreviewSize.getHeight());
            } else {
                mTextureView.setAspectRatio(
                        mPreviewSize.getHeight(), mPreviewSize.getWidth());
            }
            Size[] pictureSizes = streamConfigurationMap.getOutputSizes(ImageFormat.JPEG);
            mCaptureSize = chooseOptimalSize(pictureSizes, ratio, widthPixels);

            //We get the data of pictures through ImageReader
            mImageReader = ImageReader.newInstance(mCaptureSize.getWidth(), mCaptureSize.getHeight(),
                    ImageFormat.JPEG, 2);
            mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * To get the optimal size of previewing size.
     *
     * @param choices The list of previewing size.
     * @param ratio   Aspect ratio.
     * @return The optimal size.
     */
    private Size chooseOptimalSize(Size[] choices, double ratio, int windowWidth) {
        List<Size> optimalSize = new ArrayList<>();
        for (int i = 0; i < choices.length; i++) {
            double r = (double) choices[i].getWidth() / (double) choices[i].getHeight();
            if (Math.abs(r - ratio) <= 0.001) {
                optimalSize.add(choices[i]);
            }
        }
        if (optimalSize.size() != 0) {
            return minSize(optimalSize, windowWidth);
        } else {
            return null;
        }
    }

    /**
     * Choose the minimum size and the width must be close to the width of window.
     *
     * @param sizes    These sizes correspond with the aspects ratio.
     * @param winWidth Window width.
     * @return Minimum size.
     */
    private Size minSize(List<Size> sizes, int winWidth) {
        int minWidth = sizes.get(0).getWidth();
        Size minSize = sizes.get(0);
        for (Size size : sizes) {
            if (size.getWidth() < minWidth && size.getWidth() >= winWidth) {
                minWidth = size.getWidth();
                minSize = size;
            }
        }
        return minSize;
    }

    /**
     * We need to adjust the aspect ratio when we select the definite ratio.
     *
     * @param ratio The aspect ratio.
     * @throws CameraAccessException
     */
    public void adjustAspectRatio(double ratio) throws CameraAccessException {
        closeSession();
        closeCamera();
        openCamera(ratio);
    }

    /**
     * Open camera.
     *
     * @param cameraid Represents the orientation of camera.
     * @throws CameraAccessException
     */
    private void forOpenCamera(int cameraid) throws CameraAccessException {
        Activity activity = getActivity();
        mCameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        //mCameraId = Integer.parseInt(mCameraManager.getCameraIdList()[cameraid]);
        try {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
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


    public static CameraFragment newInstance() {
        return new CameraFragment();
    }


    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.capture:
                takePicture();
                break;
            case R.id.image_save:
                Intent startGallery = new Intent(Intent.ACTION_VIEW);
                startGallery.setData(mImageUri);
                startGallery.putExtra("camera_album", true);
                startGallery.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(startGallery);
                break;
            case R.id.camera_rotation:
                try {
                    closeSession();
                    closeCamera();
                    mCameraId ^= 1;
                    forOpenCamera(mCameraId);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
                break;
            case R.id.flash_light:
                if (null != getActivity().getBaseContext()) {
                    mCreatePopWin = new CreatePopWin(getActivity().getBaseContext(), mFlashlight);
                    View flashLayout = mCreatePopWin.createPopupWindow(R.layout.flash_popupwindow);
                    mFlashPop = mCreatePopWin.getPopupWindow();
                    flashLayout.setOnClickListener(this);
                    ImageView flashOn = flashLayout.findViewById(R.id.flash_light_select);
                    ImageView flashAuto = flashLayout.findViewById(R.id.flash_auto);
                    ImageView flashOff = flashLayout.findViewById(R.id.flash_close);
                    ImageView flashHigh = flashLayout.findViewById(R.id.flash_highlight);
                    flashOn.setOnClickListener(this);
                    flashAuto.setOnClickListener(this);
                    flashOff.setOnClickListener(this);
                    flashHigh.setOnClickListener(this);
                }
                break;
            case R.id.scale:
                mCreatePopWin = new CreatePopWin(getActivity().getBaseContext(), mScale);
                View scaleLayout = mCreatePopWin.createPopupWindow(R.layout.scale_popupwindow);
                mScalePop = mCreatePopWin.getPopupWindow();
                TextView tvFourThree = scaleLayout.findViewById(R.id.tv_scale_four_three);
                TextView tvOneOne = scaleLayout.findViewById(R.id.tv_scale_one_one);
                tvFourThree.setOnClickListener(this);
                tvOneOne.setOnClickListener(this);
                break;
            case R.id.settings:
                mCreatePopWin = new CreatePopWin(getActivity().getBaseContext(), mSettings);
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
            case R.id.flash_light_select:
                mFlashPop.dismiss();
                if (mFlashStatus != CameraParameter.FLASHON) {
                    mFlashStatus = CameraParameter.FLASHON;
                    mFlashlight.setImageResource(R.drawable.ic_flash_light);
                    closeSession();
                    createCameraPreviewSession();
                }
                break;
            case R.id.flash_auto:
                mFlashPop.dismiss();
                if (mFlashStatus != CameraParameter.FLASHAUTO) {
                    mFlashStatus = CameraParameter.FLASHAUTO;
                    mFlashlight.setImageResource(R.drawable.ic_flash_auto);
                    closeSession();
                    createCameraPreviewSession();
                }
                break;
            case R.id.flash_close:
                mFlashPop.dismiss();
                if (mFlashStatus != CameraParameter.FLASHOFF) {
                    mFlashStatus = CameraParameter.FLASHOFF;
                    mFlashlight.setImageResource(R.drawable.ic_flash_close);
                    closeSession();
                    createCameraPreviewSession();
                }
                break;
            case R.id.flash_highlight:
                mFlashPop.dismiss();
                if (mFlashStatus != CameraParameter.FLASHTORCH) {
                    mFlashStatus = CameraParameter.FLASHTORCH;
                    mFlashlight.setImageResource(R.drawable.ic_flash_highlight);
                    closeSession();
                    createCameraPreviewSession();
                }
                break;
            case R.id.tv_scale_four_three:
                mScalePop.dismiss();
                if (mCurrentRatio != CameraParameter.FOURTOTHREE) {
                    mScale.setText("4:3");
                    mCurrentRatio = CameraParameter.FOURTOTHREE;
                    try {
                        adjustAspectRatio(mCurrentRatio);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
                break;
            case R.id.tv_scale_one_one:
                mScalePop.dismiss();
                if (mCurrentRatio != CameraParameter.ONETOONE) {
                    mScale.setText("1:1");
                    mCurrentRatio = CameraParameter.ONETOONE;
                    try {
                        adjustAspectRatio(CameraParameter.ONETOONE);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
                break;
            case R.id.iv_location:
                break;
            case R.id.iv_QR_code:
                break;
            case R.id.iv_delayed_three:
                break;
            case R.id.iv_delayed_six:
                break;

        }
    }

    /**
     * Lock the focus before taking a picture.
     */
    private void takePicture() {
        lockFocus();
    }

    /**
     * Configure some parameters before taking photos.
     */
    private void captureStillPicture() {
        try {
            final Activity activity = getActivity();
            if (null == activity || null == mCameraDevice) {
                return;
            }
            final CaptureRequest.Builder captureRequestBuilder
                    = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureRequestBuilder.addTarget(mImageReader.getSurface());
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
//            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            mFlash = new FlashLight(captureRequestBuilder);
            mFlash.startFlashLight(mFlashStatus);
            int rotation = activity.getDisplay().getRotation();
            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));
//            captureRequestBuilder.set(CaptureRequest.JPEG_GPS_LOCATION,)
            mCaptureSession.stopRepeating();
            mCaptureSession.abortCaptures();
            mCaptureSession.capture(captureRequestBuilder.build(),
                    new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    Log.d(TAG, "The picture has been saved!");
                    unlockFocus();
                }
            }, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * Get the appropriate orientation.
     *
     * @param rotation The screen needs to be rotated definite angles.
     * @return Rotation
     */
    private int getOrientation(int rotation) {
        if (mCameraId == 0) {
            return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;
        } else {
            return (ORIENTATIONS.get(rotation) + mSensorOrientation + 90) % 360;
        }
    }

    /**
     * Lock the focus as the first step for a still image capture.
     */
    private void lockFocus() {
        try {
            // This is how to tell the camera to lock focus.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the lock.
            mCurrentState = STATE_WAITING_LOCK;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     *
     *Unlock the focus. This method should be called when still image capture sequence is
     *finished.
     */
    private void unlockFocus() {
        try {
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            mFlash = new FlashLight(mPreviewRequestBuilder);
            mFlash.startFlashLight(mFlashStatus);
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mHandler);
            mCurrentState = STATE_PREVIEW;
            mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback,
                    mHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Start the preview handler.
     *
     * @throws CameraAccessException
     */
    private void startHandlerThread() throws CameraAccessException {
        mHandlerThread = new HandlerThread("CameraTwo");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

    }

    /**
     * Stop the preview handler.
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
     * Close session.
     */
    private void closeSession() {
        if (null != mCaptureSession) {
            try {
                mCaptureSession.abortCaptures();
                mCaptureSession.stopRepeating();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            mCaptureSession.close();
            mCaptureSession = null;
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
}
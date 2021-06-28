package com.android.camera2;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.camera2.fragment.CameraFragment;
import com.android.camera2.fragment.VideoFragment;
import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.interfaces.OnCancelListener;
import com.lxj.xpopup.interfaces.OnConfirmListener;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;


public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_PERMISSION = 1;
    private static final int CAMERA_FRAGMENT = 0;
    private static final int VIDEO_FRAGMENT = 1;
    private int mCurrentFragment = CAMERA_FRAGMENT;
    @BindView(R.id.container)
    FrameLayout container;
    @BindView(R.id.for_camera)
    TextView takePicture;
    @BindView(R.id.for_record)
    TextView recording;
    @BindView(R.id.for_beauty)
    TextView forBeauty;
    @BindView(R.id.scroll_view)
    HorizontalScrollView scrollView;

    private static final String[] PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
    };

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        if (hasPermission(PERMISSIONS)) {
            if (savedInstanceState == null) {
                setFragment();
            }
        } else {
            requestPermission();
        }
    }

    /**
     * To judge if these permissions have opened.
     *
     * @param requestCode Application specific request code to match with a result.
     * @param permissions
     * @param grantResults
     */
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (requestCode == REQUEST_PERMISSION) {
            int i = 0;
            for (; i < grantResults.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    break;
                }
            }
            if (i == grantResults.length) {
                setFragment();
            } else {
                requestPermission();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    /**
     * To check whether the camera is permitted to be open.
     *
     * @return The value of permission.
     */
    private boolean hasPermission(String[] permissions) {
        for (String permission : permissions) {
            if (checkSelfPermission(permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Request the permissions.
     */
    private void requestPermission() {
        if (shouldShowRequestPermissionRationale(PERMISSIONS[0])
                || shouldShowRequestPermissionRationale(PERMISSIONS[1])
                || shouldShowRequestPermissionRationale(PERMISSIONS[2])) {
            showAlertDialog();
        } else {
            requestPermissions(PERMISSIONS, REQUEST_PERMISSION);
        }
    }

    /**
     * There will be a dialog for reminding us when the phone doesn't have the permission.
     */
    private void showAlertDialog() {
        new XPopup.Builder(this).asConfirm("Permission Required!", "This app needs permission for camera and audio recording.", new OnConfirmListener() {
            @Override
            public void onConfirm() {
                MainActivity.this.requestPermissions(PERMISSIONS, REQUEST_PERMISSION);
                MainActivity.this.requestPermissions(PERMISSIONS, REQUEST_PERMISSION);
                getSupportFragmentManager().beginTransaction().replace(R.id.container,
                        CameraFragment.newInstance()).commitAllowingStateLoss();
            }

        }, new OnCancelListener() {
            @Override
            public void onCancel() {
                MainActivity.this.finish();
            }
        }).show();
    }

    /**
     * Choose to start the function of taking picture or video.
     */
    private void setFragment() {
        getSupportFragmentManager().beginTransaction().replace(R.id.container,
                CameraFragment.newInstance()).commitAllowingStateLoss();
    }

    @OnClick({R.id.for_camera, R.id.for_record, R.id.for_beauty})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.for_camera:
                if (mCurrentFragment != CAMERA_FRAGMENT) {
                    mCurrentFragment = CAMERA_FRAGMENT;
                    getSupportFragmentManager().beginTransaction().replace(R.id.container,
                            CameraFragment.newInstance()).commitAllowingStateLoss();
                }
                break;
            case R.id.for_record:
                if (mCurrentFragment != VIDEO_FRAGMENT) {
                    mCurrentFragment = VIDEO_FRAGMENT;
                    getSupportFragmentManager().beginTransaction().replace(R.id.container,
                            VideoFragment.newInstance()).commitAllowingStateLoss();
                }
                break;
            case R.id.for_beauty:
                //TODO optimize pictures
                Toast.makeText(this, "Has not implement!", Toast.LENGTH_SHORT).show();
                break;
        }
    }
}


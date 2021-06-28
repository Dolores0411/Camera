package com.android.camera2.functions;

import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;

import com.android.camera2.parameters.CameraParameter;

/**
 * switch the status of AE mode according to what we want.
 */
public class FlashLight implements CameraParameter {
    private CaptureRequest.Builder mCaptureBuilder;

    public FlashLight(CaptureRequest.Builder captureBuilder) {
        mCaptureBuilder = captureBuilder;
    }

    public CaptureRequest.Builder startFlashLight(int mFlashLight) {
        if (mFlashLight == CameraParameter.FLASHON) {
            mCaptureBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CameraMetadata.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
        } else if (mFlashLight == CameraParameter.FLASHAUTO) {
            mCaptureBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH);
        } else if (mFlashLight == CameraParameter.FLASHOFF) {
            mCaptureBuilder.set(CaptureRequest.FLASH_MODE,
                    CameraMetadata.FLASH_MODE_OFF);
        } else if (mFlashLight == CameraParameter.FLASHTORCH) {
            mCaptureBuilder.set(CaptureRequest.FLASH_MODE,
                    CameraMetadata.FLASH_MODE_TORCH);
        }
        return mCaptureBuilder;
    }
}

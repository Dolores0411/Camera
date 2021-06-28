package com.android.camera2.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;

import com.android.camera2.R;

/**
 * Generate {@link PopupWindow}.
 */
public class CreatePopWin {
    private Context mContext;
    private PopupWindow mPopupWindow;
    private View mContentView;

    public CreatePopWin(Context context, View view) {
        this.mContext = context;
        this.mContentView = view;
    }

    public View createPopupWindow(int layoutId) {
        View contentView = LayoutInflater.from(mContext).inflate(layoutId, null);
        mPopupWindow = new PopupWindow(contentView,
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        mPopupWindow = new PopupWindow(mContext);
        mPopupWindow.setContentView(contentView);
        mPopupWindow.setOutsideTouchable(true);
        mPopupWindow.setFocusable(true);
        mPopupWindow.setBackgroundDrawable(mContext.getResources().getDrawable(R.color.colorTransparent));
        mPopupWindow.showAsDropDown(mContentView);
        return contentView;
    }

    public PopupWindow getPopupWindow() {
        return mPopupWindow;
    }
}

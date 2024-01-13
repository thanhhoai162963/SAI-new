package com.aefyr.sai.ui.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.aefyr.sai.R;
import com.aefyr.sai.utils.SharedPreferencesManager;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class SettingDialog extends BottomSheetDialogFragment implements View.OnClickListener {
    private ImageView mImageClose;
    private TextView mTxtVI;
    private TextView mTxtUS;
    private Callback mListener;
    private SharedPreferences mSharedRef;

    public interface Callback<T> {
        void onSuccess(T result);

        void onFailed(String error);
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        return inflater.inflate(R.layout.dialog_lang, container, false);

    }

    @Override
    public int getTheme() {
        return R.style.BaseBottomSheetDialog;
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mImageClose = view.findViewById(R.id.image_close);
        mTxtVI = view.findViewById(R.id.text_vn);
        mTxtUS = view.findViewById(R.id.text_us);
        mImageClose.setOnClickListener(this);
        mTxtVI.setOnClickListener(this);
        mTxtUS.setOnClickListener(this);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return dialog;
    }


    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.image_close:
                mListener.onFailed("no");
                break;
            case R.id.text_vn:
                mListener.onSuccess("vi");
                SharedPreferencesManager.getInstance(getContext()).clear();

                SharedPreferencesManager.getInstance(getContext()).setLang("vi");
                break;
            case R.id.text_us:
                mListener.onSuccess("en");
                SharedPreferencesManager.getInstance(getContext()).clear();

                SharedPreferencesManager.getInstance(getContext()).setLang("en");
                break;
            default:
                break;
        }
        dismiss();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        try {
            if (getParentFragment() != null)
                mListener = (Callback) getParentFragment();
            else
                mListener = (Callback) getActivity();
        } catch (Exception e) {
            throw new IllegalStateException("Activity/Fragment that uses FilePickerDialogFragment must implement FilePickerDialogFragment.OnFilesSelectedListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }
}


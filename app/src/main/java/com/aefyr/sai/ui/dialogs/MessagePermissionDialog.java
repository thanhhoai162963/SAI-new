package com.aefyr.sai.ui.dialogs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.aefyr.sai.R;
import com.aefyr.sai.ui.activities.MainActivity;
import com.google.android.material.button.MaterialButton;

public class MessagePermissionDialog extends DialogFragment {
    private MaterialButton mMaterialButton;
    private OnDismissListener mListener;


    public static MessagePermissionDialog newInstance() {
        MessagePermissionDialog dialog = new MessagePermissionDialog();
        return dialog;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_message_permission, container);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mMaterialButton = (MaterialButton) view.findViewById(R.id.button_access);
        mMaterialButton.setOnClickListener(view1 -> resetApp());
    }

    @SuppressLint("ObsoleteSdkInt")
    private void resetApp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            getActivity().finishAffinity();
            Intent intent = new Intent(getActivity().getApplicationContext(), MainActivity.class);
            startActivity(intent);
        }
    }
    public interface OnDismissListener {
        void onDialogDismissed();
    }
}

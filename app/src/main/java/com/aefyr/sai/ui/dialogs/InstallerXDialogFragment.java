package com.aefyr.sai.ui.dialogs;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aefyr.sai.R;
import com.aefyr.sai.adapters.SplitApkSourceMetaAdapter;
import com.aefyr.sai.installerx.resolver.urimess.UriHostFactory;
import com.aefyr.sai.ui.dialogs.base.BaseBottomSheetDialogFragment;
import com.aefyr.sai.utils.AlertsUtils;
import com.aefyr.sai.utils.PermissionsUtils;
import com.aefyr.sai.utils.PreferencesHelper;
import com.aefyr.sai.utils.Utils;
import com.aefyr.sai.view.ViewSwitcherLayout;
import com.aefyr.sai.viewmodels.InstallerXDialogViewModel;
import com.aefyr.sai.viewmodels.factory.InstallerXDialogViewModelFactory;
import com.github.angads25.filepicker.model.DialogConfigs;
import com.github.angads25.filepicker.model.DialogProperties;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

@SuppressWarnings("ALL")
public class InstallerXDialogFragment extends BaseBottomSheetDialogFragment implements FilePickerDialogFragment.OnFilesSelectedListener, SimpleAlertDialogFragment.OnDismissListener {
    private static final int REQUEST_CODE_GET_FILES = 337;
    public static final int REQUEST_CODE_GET_FILES_OBB = 100;

    private static final String ARG_APK_SOURCE_URI = "apk_source_uri";
    private static final String ARG_URI_HOST_FACTORY = "uri_host_factory";

    private InstallerXDialogViewModel mViewModel;

    private PreferencesHelper mHelper;

    private int mActionAfterGettingStoragePermissions;
    private static final int PICK_WITH_INTERNAL_FILEPICKER = 0;
    private static final int PICK_WITH_SAF = 1;

    private static final String DIALOG_TAG_Q_SAF_WARNING = "q_saf_warning";

    private LinearLayout mLayoutProgress;
    private ViewSwitcherLayout mLayoutInstall;

    private String mPathObb = "";
    private Disposable mDisposable;

    /**
     * Create an instance of InstallerXDialogFragment with given apk source uri and UriHostFactory class.
     * If {@code apkSourceUri} is null, dialog will let user pick apk source file.
     * If {@code uriHostFactoryClass} is null, {@link com.aefyr.sai.installerx.resolver.urimess.impl.AndroidUriHost} will be used.
     */
    public static InstallerXDialogFragment newInstance(@Nullable Uri apkSourceUri, @Nullable Class<? extends UriHostFactory> uriHostFactoryClass) {
        Bundle args = new Bundle();
        if (apkSourceUri != null)
            args.putParcelable(ARG_APK_SOURCE_URI, apkSourceUri);

        if (uriHostFactoryClass != null)
            args.putString(ARG_URI_HOST_FACTORY, uriHostFactoryClass.getCanonicalName());

        InstallerXDialogFragment fragment = new InstallerXDialogFragment();
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();

        UriHostFactory uriHostFactory = null;
        if (args != null) {
            String uriHostFactoryClass = args.getString(ARG_URI_HOST_FACTORY);
            if (uriHostFactoryClass != null) {
                try {
                    uriHostFactory = (UriHostFactory) Class.forName(uriHostFactoryClass).getConstructor().newInstance();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        mHelper = PreferencesHelper.getInstance(requireContext());
        mViewModel = new ViewModelProvider(this, new InstallerXDialogViewModelFactory(requireContext(), uriHostFactory)).get(InstallerXDialogViewModel.class);

        if (args == null)
            return;

        Uri apkSourceUri = args.getParcelable(ARG_APK_SOURCE_URI);
        if (apkSourceUri != null)
            mViewModel.setApkSourceUris(Collections.singletonList(apkSourceUri));
    }

    @Nullable
    @Override
    protected View onCreateContentView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_installerx, container, false);
        mLayoutProgress = view.findViewById(R.id.layout_progress);
        mLayoutInstall = view.findViewById(R.id.container_dialog_installerx);
        return view;
    }

    @Override
    protected void onContentViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onContentViewCreated(view, savedInstanceState);

        setTitle(R.string.installerx_dialog_title);
        getPositiveButton().setText(R.string.installerx_dialog_install);

        ViewSwitcherLayout viewSwitcher = view.findViewById(R.id.container_dialog_installerx);

        RecyclerView recycler = view.findViewById(R.id.rv_dialog_installerx_content);
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        recycler.getRecycledViewPool().setMaxRecycledViews(SplitApkSourceMetaAdapter.VH_TYPE_SPLIT_PART, 16);

        SplitApkSourceMetaAdapter adapter = new SplitApkSourceMetaAdapter(mViewModel.getPartsSelection(), this, requireContext());
        recycler.setAdapter(adapter);

        getNegativeButton().setOnClickListener(v -> dismiss());
        getPositiveButton().setOnClickListener(v -> {
            mViewModel.enqueueInstallation();
            dismiss();
        });

        view.findViewById(R.id.button_installerx_fp_internal).setOnClickListener(v -> checkPermissionsAndPickFiles());
        view.findViewById(R.id.button_installerx_fp_saf).setOnClickListener(v -> pickFilesWithSaf(false));

        TextView warningTv = view.findViewById(R.id.tv_installerx_warning);
        mViewModel.getState().observe(this, state -> {
            switch (state) {
                case NO_DATA:
                    viewSwitcher.setShownView(R.id.container_installerx_no_data);
                    getPositiveButton().setVisibility(View.GONE);
                    break;
                case LOADING:
                    viewSwitcher.setShownView(R.id.container_installerx_loading);
                    getPositiveButton().setVisibility(View.GONE);
                    break;
                case LOADED:
                    viewSwitcher.setShownView(R.id.rv_dialog_installerx_content);
                    getPositiveButton().setVisibility(View.VISIBLE);
                    break;
                case WARNING:
                    viewSwitcher.setShownView(R.id.container_installerx_warning);
                    warningTv.setText(mViewModel.getWarning().message());
                    getPositiveButton().setVisibility(mViewModel.getWarning().canInstallAnyway() ? View.VISIBLE : View.GONE);
                    break;
                case ERROR:
                    viewSwitcher.setShownView(R.id.container_installerx_error);
                    getPositiveButton().setVisibility(View.VISIBLE);
                    break;
            }
            revealBottomSheet();
        });

        mViewModel.getMeta().observe(this, meta -> {
            adapter.setMeta(meta);
            revealBottomSheet();
        });

        view.requestFocus(); //TV fix
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);

        if (mViewModel.getState().getValue() == InstallerXDialogViewModel.State.LOADING)
            mViewModel.cancelParsing();
    }

    private void checkPermissionsAndPickFiles() {
        mActionAfterGettingStoragePermissions = PICK_WITH_INTERNAL_FILEPICKER;

        DialogProperties properties = new DialogProperties();
        properties.selection_mode = DialogConfigs.MULTI_MODE;
        properties.selection_type = DialogConfigs.FILE_SELECT;
        properties.root = Environment.getExternalStorageDirectory();
        properties.offset = new File(mHelper.getHomeDirectory());
        properties.extensions = new String[]{"zip", "apks", "xapk", "apk", "apkm"};
        properties.sortBy = mHelper.getFilePickerSortBy();
        properties.sortOrder = mHelper.getFilePickerSortOrder();

        FilePickerDialogFragment.newInstance(null, getString(R.string.installer_pick_apks), properties).show(getChildFragmentManager(), "dialog_files_picker");
    }

    private void pickFilesWithSaf(boolean ignorePermissions) {
        if (Utils.apiIsAtLeast(30) && !ignorePermissions) {
            if (requireContext().checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                SimpleAlertDialogFragment.newInstance(requireContext(), R.string.warning, R.string.installerx_thank_you_scoped_storage_very_cool).show(getChildFragmentManager(), DIALOG_TAG_Q_SAF_WARNING);
                return;
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (requireContext().checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                SimpleAlertDialogFragment.newInstance(requireContext(), R.string.warning, R.string.installerx_thank_you_scoped_storage_very_cool).show(getChildFragmentManager(), DIALOG_TAG_Q_SAF_WARNING);
                return;
            }
        }

        Intent getContentIntent = new Intent(Intent.ACTION_GET_CONTENT);
        getContentIntent.setType("*/*");
        getContentIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        getContentIntent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        getContentIntent.addCategory(Intent.CATEGORY_OPENABLE);

        startActivityForResult(Intent.createChooser(getContentIntent, getString(R.string.installer_pick_apks)), REQUEST_CODE_GET_FILES);

    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PermissionsUtils.REQUEST_CODE_STORAGE_PERMISSIONS) {
            boolean permissionsGranted = !(grantResults.length == 0 || grantResults[0] == PackageManager.PERMISSION_DENIED);
            switch (mActionAfterGettingStoragePermissions) {
                case PICK_WITH_INTERNAL_FILEPICKER:
                    if (!permissionsGranted)
                        AlertsUtils.showAlert(this, R.string.error, R.string.permissions_required_storage);
                    else
                        checkPermissionsAndPickFiles();
                    break;
                case PICK_WITH_SAF:
                    pickFilesWithSaf(true);
                    break;
            }
        }

    }

    @SuppressLint("WrongConstant")
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_GET_FILES) {
            if (resultCode != Activity.RESULT_OK || data == null)
                return;
            if (data.getData() != null) {
                backgroundTask(data.getData());
                mViewModel.setApkSourceUris(Collections.singletonList(data.getData()));
                return;
            }

            if (data.getClipData() != null) {
                ClipData clipData = data.getClipData();
                List<Uri> apkUris = new ArrayList<>(clipData.getItemCount());
                data.getData().getPath();

                for (int i = 0; i < clipData.getItemCount(); i++)
                    apkUris.add(clipData.getItemAt(i).getUri());

                mViewModel.setApkSourceUris(apkUris);
            }
        }
        if (requestCode == REQUEST_CODE_GET_FILES_OBB) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                int takeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                getContext().getApplicationContext().getContentResolver().takePersistableUriPermission(data.getData(), takeFlags);
            }
        }
    }

    private boolean copyFileObb(Uri data) {
        String pathSrc = getPath(data);
        Path uriDir = unpackZip(pathSrc);
        String pathObb = uriDir.fileName.replace(mPathObb, "");
        copyFileOrDirectory(uriDir.pathName, Environment.getExternalStorageDirectory().getPath() + "/Android/obb/" + pathObb);
        return true;
    }

    private void backgroundTask(Uri data) {
        setShowHideProgress(true);
        Observable
                .just(copyFileObb(data))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<Boolean>() {
                    @Override
                    public void onSubscribe(@io.reactivex.rxjava3.annotations.NonNull Disposable d) {
                        mDisposable = d;
                    }

                    @Override
                    public void onNext(@io.reactivex.rxjava3.annotations.NonNull Boolean aBoolean) {

                    }

                    @Override
                    public void onError(@io.reactivex.rxjava3.annotations.NonNull Throwable e) {
                        setShowHideProgress(false);
                    }

                    @Override
                    public void onComplete() {
                        setShowHideProgress(false);
                        mViewModel.setApkSourceUris(Collections.singletonList(data));
                    }
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mDisposable == null) return;
        mDisposable.dispose();
    }

    private void setShowHideProgress(Boolean isShow) {
        if (!isShow) {
            mLayoutProgress.setVisibility(View.GONE);
            mLayoutInstall.setVisibility(View.VISIBLE);
        } else {
            mLayoutProgress.setVisibility(View.VISIBLE);
            mLayoutInstall.setVisibility(View.GONE);
        }
    }

    public void openFileObb() {
        StorageManager sm = (StorageManager) getContext().getApplicationContext().getSystemService(Context.STORAGE_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent intent = sm.getPrimaryStorageVolume().createOpenDocumentTreeIntent();
            String startDir = "Android/obb";

            Uri uri = intent.getParcelableExtra("android.provider.extra.INITIAL_URI");

            String scheme = uri.toString();

            scheme = scheme.replace("/root/", "/document/");

            startDir = startDir.replace("/", "%2F");

            scheme += "%3A" + startDir;

            uri = Uri.parse(scheme);
            intent.setAction(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
            intent.putExtra("android.provider.extra.INITIAL_URI", uri);
            startActivityForResult(intent, REQUEST_CODE_GET_FILES_OBB);
        }
    }

    public static void copyFileOrDirectory(String srcDir, String dstDir) {
        try {
            File src = new File(srcDir);
            File dst = new File(dstDir);
            if (src.isDirectory()) {
                String[] files = src.list();
                for (String file : files) {
                    String src1 = (new File(src, file).getPath());
                    String dst1 = dst.getPath();
                    copyFileOrDirectory(src1, dst1);
                }
            } else {
                copyFile(src, dst);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void copyFile(File sourceFile, File destFile) throws IOException {
        if (!destFile.getParentFile().exists())
            destFile.getParentFile().mkdirs();

        if (!destFile.exists()) {
            destFile.createNewFile();
        }

        FileChannel source = null;
        FileChannel destination = null;
        try {
            source = new FileInputStream(sourceFile).getChannel();
            destination = new FileOutputStream(destFile).getChannel();
            destination.transferFrom(source, 0, source.size());
        } finally {
            if (source != null) {
                source.close();
            }
            if (destination != null) {
                destination.close();
            }
        }
    }

    @Override
    public void onFilesSelected(String tag, List<File> files) {
        mViewModel.setApkSourceFiles(files);
    }

    @Override
    public void onDialogDismissed(@NonNull String dialogTag) {
        switch (dialogTag) {
            case DIALOG_TAG_Q_SAF_WARNING:
                mActionAfterGettingStoragePermissions = PICK_WITH_SAF;
                if (PermissionsUtils.checkAndRequestStoragePermissions(this)) {
                    pickFilesWithSaf(false);
                }
                break;
        }
    }

    class Path {
        String fileName = null;
        String pathName = null;
    }

    private Path unpackZip(String path) {
        String pathName = "";
        String fileName1 = "";
        InputStream is;
        ZipInputStream zis;
        try {
            String filename;
            is = new FileInputStream(path);
            zis = new ZipInputStream(new BufferedInputStream(is));
            ZipEntry ze;
            byte[] buffer = new byte[1024];
            int count;
            int countFileObb = 0;

            while ((ze = zis.getNextEntry()) != null) {
                filename = ze.getName();
                fileName1 = filename;
                pathName = path + filename;
                if (ze.getName().contains("/obb/")) {
                    if (countFileObb == 0) {
                        mPathObb = ze.getName();
                    }
                    countFileObb++;
                }
                if (ze.isDirectory()) {
                    File fmd = new File(pathName);
                    fmd.mkdirs();
                    continue;
                }
                FileOutputStream fout = new FileOutputStream(pathName);
                if (ze.getName().contains(".obb")) {
                    while ((count = zis.read(buffer)) != -1) {
                        fout.write(buffer, 0, count);
                    }
                }
                fout.close();
                zis.closeEntry();
            }

            zis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        Path path1 = new Path();
        path1.fileName = fileName1;
        path1.pathName = pathName;
        return path1;

    }

    public static String getPathObb(final Context context, final Uri uri) {

        final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }

                // TODO handle non-primary volumes
            }
            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {

                final String id = DocumentsContract.getDocumentId(uri);
                final Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

                return getDataColumn(context, contentUri, null, null);
            }
            // MediaProvider
            else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[]{
                        split[1]
                };

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {


            return getDataColumn(context, uri, null, null);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        return null;
    }


    public String getPath(final Uri uri) {
        // check here to KITKAT or new version
        final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
        String selection = null;
        String[] selectionArgs = null;
        // DocumentProvider
        if (isKitKat) {
            // ExternalStorageProvider

            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                String fullPath = getPathFromExtSD(split);
                if (fullPath != "") {
                    return fullPath;
                } else {
                    return null;
                }
            }
            // DownloadsProvider

            if (isDownloadsDocument(uri)) {

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    final String id;
                    Cursor cursor = null;
                    try {
                        cursor = getActivity().getApplicationContext().getContentResolver().query(uri, new String[]{MediaStore.MediaColumns.DISPLAY_NAME}, null, null, null);
                        if (cursor != null && cursor.moveToFirst()) {
                            String fileName = cursor.getString(0);
                            String pathDownload = Environment.getExternalStorageDirectory().toString() + "/Download/" + fileName;
                            File fileDownload = new File(pathDownload);
                            if (fileDownload.exists()) {
                                if (!TextUtils.isEmpty(pathDownload)) {
                                    return pathDownload;
                                }
                            } else {
                                Toast.makeText(getContext().getApplicationContext(), "not path", Toast.LENGTH_LONG).show();
                                String pathDownloadBrowser = Environment.getExternalStorageDirectory().toString() + "/Download/Browser/" + fileName;
                                return pathDownloadBrowser;
                            }

                        }
                    } finally {
                        if (cursor != null)
                            cursor.close();
                    }
                    id = DocumentsContract.getDocumentId(uri);
                    if (!TextUtils.isEmpty(id)) {
                        if (id.startsWith("raw:")) {
                            return id.replaceFirst("raw:", "");
                        }
                        String[] contentUriPrefixesToTry = new String[]{
                                "content://downloads/public_downloads",
                                "content://downloads/my_downloads"
                        };
                        for (String contentUriPrefix : contentUriPrefixesToTry) {
                            try {
                                final Uri contentUri = ContentUris.withAppendedId(Uri.parse(contentUriPrefix), Long.valueOf(id));


                                return getDataColumn(getActivity().getApplicationContext(), contentUri, null, null);
                            } catch (NumberFormatException e) {
                                //In Android 8 and Android P the id is not a number
                                return uri.getPath().replaceFirst("^/document/raw:", "").replaceFirst("^raw:", "");
                            }
                        }


                    }
                } else {
                    final String id = DocumentsContract.getDocumentId(uri);
                    Uri contentUri = null;

                    if (id.startsWith("raw:")) {
                        return id.replaceFirst("raw:", "");
                    }
                    try {
                        contentUri = ContentUris.withAppendedId(
                                Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                    }
                    if (contentUri != null) {

                        return getDataColumn(getActivity().getApplicationContext(), contentUri, null, null);
                    }
                }
            }


            // MediaProvider
            if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;

                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }
                selection = "_id=?";
                selectionArgs = new String[]{split[1]};


                return getDataColumn(getActivity().getApplicationContext(), contentUri, selection,
                        selectionArgs);
            }


            if ("content".equalsIgnoreCase(uri.getScheme())) {

                if (isGooglePhotosUri(uri)) {
                    return uri.getLastPathSegment();
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

                    // return getFilePathFromURI(context,uri);
                    return copyFileToInternalStorage(uri, "userfiles");
                    // return getRealPathFromURI(context,uri);
                } else {
                    return getDataColumn(getActivity().getApplicationContext(), uri, null, null);
                }

            }
            if ("file".equalsIgnoreCase(uri.getScheme())) {
                return uri.getPath();
            }
        } else {


            if ("content".equalsIgnoreCase(uri.getScheme())) {
                String[] projection = {
                        MediaStore.Images.Media.DATA
                };
                Cursor cursor = null;
                try {
                    cursor = getActivity().getApplicationContext().getContentResolver()
                            .query(uri, projection, selection, selectionArgs, null);
                    int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                    if (cursor.moveToFirst()) {
                        return cursor.getString(column_index);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }


        return null;
    }

    private boolean fileExists(String filePath) {
        File file = new File(filePath);

        return file.exists();
    }

    private String getPathFromExtSD(String[] pathData) {
        final String type = pathData[0];
        final String relativePath = "/" + pathData[1];
        String fullPath = "";

        // on my Sony devices (4.4.4 & 5.1.1), `type` is a dynamic string
        // something like "71F8-2C0A", some kind of unique id per storage
        // don't know any API that can get the root path of that storage based on its id.
        //
        // so no "primary" type, but let the check here for other devices
        if ("primary".equalsIgnoreCase(type)) {
            fullPath = Environment.getExternalStorageDirectory() + relativePath;
            if (fileExists(fullPath)) {
                return fullPath;
            }
        }

        // Environment.isExternalStorageRemovable() is `true` for external and internal storage
        // so we cannot relay on it.
        //
        // instead, for each possible path, check if file exists
        // we'll start with secondary storage as this could be our (physically) removable sd card
        fullPath = System.getenv("SECONDARY_STORAGE") + relativePath;
        if (fileExists(fullPath)) {
            return fullPath;
        }

        fullPath = System.getenv("EXTERNAL_STORAGE") + relativePath;
        if (fileExists(fullPath)) {
            return fullPath;
        }

        return fullPath;
    }

    /***
     * Used for Android Q+
     * @param uri
     * @param newDirName if you want to create a directory, you can set this variable
     * @return
     */
    private String copyFileToInternalStorage(Uri uri, String newDirName) {
        Uri returnUri = uri;

        Cursor returnCursor = getActivity().getApplicationContext().getContentResolver().query(returnUri, new String[]{
                OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE
        }, null, null, null);


        /*
         * Get the column indexes of the data in the Cursor,
         *     * move to the first row in the Cursor, get the data,
         *     * and display it.
         * */
        int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        int sizeIndex = returnCursor.getColumnIndex(OpenableColumns.SIZE);
        returnCursor.moveToFirst();
        String name = (returnCursor.getString(nameIndex));
        String size = (Long.toString(returnCursor.getLong(sizeIndex)));

        File output;
        if (!newDirName.equals("")) {
            File dir = new File(getActivity().getApplicationContext().getFilesDir() + "/" + newDirName);
            if (!dir.exists()) {
                dir.mkdir();
            }
            output = new File(getActivity().getApplicationContext().getFilesDir() + "/" + newDirName + "/" + name);
        } else {
            output = new File(getActivity().getApplicationContext().getFilesDir() + "/" + name);
        }
        try {
            InputStream inputStream = getActivity().getApplicationContext().getContentResolver().openInputStream(uri);
            FileOutputStream outputStream = new FileOutputStream(output);
            int read = 0;
            int bufferSize = 1024;
            final byte[] buffers = new byte[bufferSize];
            while ((read = inputStream.read(buffers)) != -1) {
                outputStream.write(buffers, 0, read);
            }

            inputStream.close();
            outputStream.close();

        } catch (Exception e) {

        }

        return output.getPath();
    }


    private static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {column};

        try {
            cursor = context.getContentResolver().query(uri, projection,
                    selection, selectionArgs, null);

            if (cursor != null && cursor.moveToFirst()) {
                final int index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }

        return null;
    }

    private static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    private static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    private static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    private boolean isGooglePhotosUri(Uri uri) {
        return "com.google.android.apps.photos.content".equals(uri.getAuthority());
    }
}

package com.aefyr.sai.ui.activities;


import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.MenuItem;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.aefyr.sai.R;
import com.aefyr.sai.backup2.impl.DefaultBackupManager;
import com.aefyr.sai.billing.BillingManager;
import com.aefyr.sai.billing.DefaultBillingManager;
import com.aefyr.sai.ui.fragments.BackupFragment;
import com.aefyr.sai.ui.fragments.Installer2Fragment;
import com.aefyr.sai.ui.fragments.InstallerFragment;
import com.aefyr.sai.ui.fragments.LegacyInstallerFragment;
import com.aefyr.sai.ui.fragments.PreferencesFragment;
import com.aefyr.sai.utils.FragmentNavigator;
import com.aefyr.sai.utils.MiuiUtils;
import com.aefyr.sai.utils.PreferencesHelper;
import com.aefyr.sai.utils.PreferencesKeys;
import com.aefyr.sai.utils.SharedPreferencesManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.Locale;

public class MainActivity extends ThemedActivity implements BottomNavigationView.OnNavigationItemSelectedListener, FragmentNavigator.FragmentFactory {

    private BottomNavigationView mBottomNavigationView;

    private FragmentNavigator mFragmentNavigator;

    private InstallerFragment mInstallerFragment;

    private boolean mIsNavigationEnabled = true;

    private BillingManager mBillingManager;
    private RelativeLayout view;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mBillingManager = DefaultBillingManager.getInstance(this);

        //TODO is this ok?
        DefaultBackupManager.getInstance(this);

        showMiuiWarning();


        mBottomNavigationView = findViewById(R.id.bottomnav_main);
        mBottomNavigationView.setOnNavigationItemSelectedListener(this);

        mFragmentNavigator = new FragmentNavigator(savedInstanceState, getSupportFragmentManager(), R.id.container_main, this);
        mInstallerFragment = mFragmentNavigator.findFragmentByTag("installer");
        if (savedInstanceState == null)
            mFragmentNavigator.switchTo("installer");

        Intent intent = getIntent();
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            deliverActionViewUri(intent.getData());
            getIntent().setData(null);
        }

        restartLanguageApp();
    }

    private void restartLanguageApp() {
        if (Locale.getDefault().getLanguage() != "en" && SharedPreferencesManager.getInstance(this).getLang() != "vi") {
            Locale locale = new Locale("en");
            Locale.setDefault(locale);
            Configuration config = getResources().getConfiguration();
            config.locale = locale;
            getResources().updateConfiguration(config, getResources().getDisplayMetrics());
        }
    }

    public void restartApp() {
        Intent intent = getBaseContext().getPackageManager().getLaunchIntentForPackage(getBaseContext().getPackageName());
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            deliverActionViewUri(intent.getData());
        }
    }

    private void deliverActionViewUri(Uri uri) {
        if (!mIsNavigationEnabled) {
            Toast.makeText(this, R.string.main_navigation_disabled, Toast.LENGTH_SHORT).show();
            return;
        }
        mBottomNavigationView.getMenu().getItem(0).setChecked(true);
        mFragmentNavigator.switchTo("installer");
        getInstallerFragment().handleActionView(uri);
    }

    private void showMiuiWarning() {
        if (MiuiUtils.isMiui() && !PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PreferencesKeys.MIUI_WARNING_SHOWN, false)) {
            startActivity(new Intent(this, MiActivity.class));
            finish();
        }
    }

    public void setNavigationEnabled(boolean enabled) {
        mIsNavigationEnabled = enabled;

        for (int i = 0; i < mBottomNavigationView.getMenu().size(); i++) {
            mBottomNavigationView.getMenu().getItem(i).setEnabled(enabled);
        }
        mBottomNavigationView.animate()
                .alpha(enabled ? 1f : 0.4f)
                .setDuration(300)
                .start();
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_installer:
                mFragmentNavigator.switchTo("installer");
                break;
//            case R.id.menu_backup:
//                mFragmentNavigator.switchTo("backup");
//                break;
//            case R.id.menu_settings:
//                mFragmentNavigator.switchTo("settings");
//                break;
        }

        return true;
    }

    @Override
    public Fragment createFragment(String tag) {
        switch (tag) {
            case "installer":
                return getInstallerFragment();
            case "backup":
                return new BackupFragment();
            case "settings":
                return new PreferencesFragment();
        }

        throw new IllegalArgumentException("Unknown fragment tag: " + tag);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mFragmentNavigator.writeStateToBundle(outState);
    }

    private InstallerFragment getInstallerFragment() {
        if (mInstallerFragment == null)
            mInstallerFragment = PreferencesHelper.getInstance(this).useOldInstaller() ? new LegacyInstallerFragment() : new Installer2Fragment();
        return mInstallerFragment;
    }

    @Override
    protected void onResume() {
        super.onResume();
        mBillingManager.refresh();
    }
}

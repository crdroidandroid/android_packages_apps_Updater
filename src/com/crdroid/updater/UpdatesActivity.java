/*
 * Copyright (C) 2017-2020 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.crdroid.updater;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.icu.text.DateFormat;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemProperties;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONException;
import com.crdroid.updater.controller.UpdaterController;
import com.crdroid.updater.controller.UpdaterService;
import com.crdroid.updater.download.DownloadClient;
import com.crdroid.updater.misc.BuildInfoUtils;
import com.crdroid.updater.misc.Constants;
import com.crdroid.updater.misc.FileUtils;
import com.crdroid.updater.misc.PermissionsUtils;
import com.crdroid.updater.misc.StringGenerator;
import com.crdroid.updater.misc.Utils;
import com.crdroid.updater.model.Update;
import com.crdroid.updater.model.UpdateInfo;
import com.crdroid.updater.model.UpdateStatus;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class UpdatesActivity extends UpdatesListActivity {

    private static final String TAG = "UpdatesActivity";
    private UpdaterService mUpdaterService;
    private BroadcastReceiver mBroadcastReceiver;

    private static UpdatesListAdapter mAdapter;

    private View mRefreshIconView;
    private RotateAnimation mRefreshAnimation;

    private static final int READ_REQUEST_CODE = 42;
    private static final int PERMISSION_REQUEST_CODE = 200;

    private static Map<String, String> sf_mirrors;

    // Storage Permissions
    private static final int STORAGE_PERMISSIONS_REQUEST_CODE = 0;
    private static final String[] REQUIRED_STORAGE_PERMISSIONS = new String[]{
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_updates);

        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        mAdapter = new UpdatesListAdapter(this, this);
        recyclerView.setAdapter(mAdapter);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        RecyclerView.ItemAnimator animator = recyclerView.getItemAnimator();
        if (animator instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) animator).setSupportsChangeAnimations(false);
        }

        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (UpdaterController.ACTION_UPDATE_STATUS.equals(intent.getAction())) {
                    String downloadId = intent.getStringExtra(UpdaterController.EXTRA_DOWNLOAD_ID);
                    handleDownloadStatusChange(downloadId);
                    mAdapter.notifyDataSetChanged();
                } else if (UpdaterController.ACTION_DOWNLOAD_PROGRESS.equals(intent.getAction()) ||
                        UpdaterController.ACTION_INSTALL_PROGRESS.equals(intent.getAction())) {
                    String downloadId = intent.getStringExtra(UpdaterController.EXTRA_DOWNLOAD_ID);
                    mAdapter.notifyItemChanged(downloadId);
                } else if (UpdaterController.ACTION_UPDATE_REMOVED.equals(intent.getAction())) {
                    String downloadId = intent.getStringExtra(UpdaterController.EXTRA_DOWNLOAD_ID);
                    mAdapter.removeItem(downloadId);
                    downloadUpdatesList(false);
                }
            }
        };

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        TextView headerTitle = (TextView) findViewById(R.id.header_title);
        headerTitle.setText(getString(R.string.header_title_text,
                BuildInfoUtils.getBuildVersion()) + "\n(" 
                + SystemProperties.get(Constants.PROP_DEVICE) + ")");

        updateLastCheckedString();

        // Switch between header title and appbar title minimizing overlaps
        final CollapsingToolbarLayout collapsingToolbar =
                (CollapsingToolbarLayout) findViewById(R.id.collapsing_toolbar);
        final AppBarLayout appBar = (AppBarLayout) findViewById(R.id.app_bar);
        appBar.addOnOffsetChangedListener(new AppBarLayout.OnOffsetChangedListener() {
            boolean mIsShown = false;

            @Override
            public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
                int scrollRange = appBarLayout.getTotalScrollRange();
                if (!mIsShown && scrollRange + verticalOffset < 10) {
                    collapsingToolbar.setTitle(getString(R.string.display_name));
                    mIsShown = true;
                } else if (mIsShown && scrollRange + verticalOffset > 100) {
                    collapsingToolbar.setTitle(null);
                    mIsShown = false;
                }
            }
        });

        if (!Utils.hasTouchscreen(this)) {
            // This can't be collapsed without a touchscreen
            appBar.setExpanded(false);
        }

        mRefreshAnimation = new RotateAnimation(0, 360, Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);
        mRefreshAnimation.setInterpolator(new LinearInterpolator());
        mRefreshAnimation.setDuration(1000);
    }

    @Override
    public void onStart() {
        super.onStart();
        Intent intent = new Intent(this, UpdaterService.class);
        startService(intent);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UpdaterController.ACTION_UPDATE_STATUS);
        intentFilter.addAction(UpdaterController.ACTION_DOWNLOAD_PROGRESS);
        intentFilter.addAction(UpdaterController.ACTION_INSTALL_PROGRESS);
        intentFilter.addAction(UpdaterController.ACTION_UPDATE_REMOVED);
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, intentFilter);
    }

    @Override
    public void onStop() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
        if (mUpdaterService != null) {
            unbindService(mConnection);
        }
        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_toolbar, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_refresh: {
                downloadUpdatesList(true);
                return true;
            }
            case R.id.menu_preferences: {
                showPreferencesDialog();
                return true;
            }
            case R.id.menu_sf_mirrors: {
                showSfMirrorPreferencesDialog();
                return true;
            }
            case R.id.menu_local_update: {
                boolean hasPermission = PermissionsUtils.checkAndRequestPermissions(
                      this, REQUIRED_STORAGE_PERMISSIONS,
                      STORAGE_PERMISSIONS_REQUEST_CODE);
                if (hasPermission) {
                  performFileSearch();
                }
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                IBinder service) {
            UpdaterService.LocalBinder binder = (UpdaterService.LocalBinder) service;
            mUpdaterService = binder.getService();
            mAdapter.setUpdaterController(mUpdaterService.getUpdaterController());
            getUpdatesList();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mAdapter.setUpdaterController(null);
            mUpdaterService = null;
            mAdapter.notifyDataSetChanged();
        }
    };

    private void loadUpdatesList(File jsonFile, boolean manualRefresh)
            throws IOException, JSONException {
        Log.d(TAG, "Adding remote updates");
        UpdaterController controller = mUpdaterService.getUpdaterController();
        boolean newUpdates = false;

        List<UpdateInfo> updates = Utils.parseJson(jsonFile, true);
        List<String> updatesOnline = new ArrayList<>();
        for (UpdateInfo update : updates) {
            newUpdates |= controller.addUpdate(update);
            updatesOnline.add(update.getDownloadId());
        }
        controller.setUpdatesAvailableOnline(updatesOnline, true);

        if (manualRefresh) {
            showSnackbar(
                    newUpdates ? R.string.snack_updates_found : R.string.snack_no_updates_found,
                    Snackbar.LENGTH_SHORT);
        }

        List<String> updateIds = new ArrayList<>();
        List<UpdateInfo> sortedUpdates = controller.getUpdates();
        if (sortedUpdates.isEmpty()) {
            findViewById(R.id.no_new_updates_view).setVisibility(View.VISIBLE);
            findViewById(R.id.recycler_view).setVisibility(View.GONE);
        } else {
            findViewById(R.id.no_new_updates_view).setVisibility(View.GONE);
            findViewById(R.id.recycler_view).setVisibility(View.VISIBLE);
            sortedUpdates.sort((u1, u2) -> Long.compare(u2.getTimestamp(), u1.getTimestamp()));
            for (UpdateInfo update : sortedUpdates) {
                updateIds.add(update.getDownloadId());
            }
            mAdapter.setData(updateIds);
            mAdapter.notifyDataSetChanged();
        }
    }

    private void getUpdatesList() {
        File jsonFile = Utils.getCachedUpdateList(this);
        if (jsonFile.exists()) {
            try {
                loadUpdatesList(jsonFile, false);
                Log.d(TAG, "Cached list parsed");
            } catch (IOException | JSONException e) {
                Log.e(TAG, "Error while parsing json list", e);
            }
        } else {
            downloadUpdatesList(false);
        }
    }

    private void processNewJson(File json, File jsonNew, boolean manualRefresh) {
        try {
            loadUpdatesList(jsonNew, manualRefresh);
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
            long millis = System.currentTimeMillis();
            preferences.edit().putLong(Constants.PREF_LAST_UPDATE_CHECK, millis).apply();
            updateLastCheckedString();
            if (json.exists() && Utils.isUpdateCheckEnabled(this) &&
                    Utils.checkForNewUpdates(json, jsonNew)) {
                UpdatesCheckReceiver.updateRepeatingUpdatesCheck(this);
            }
            // In case we set a one-shot check because of a previous failure
            UpdatesCheckReceiver.cancelUpdatesCheck(this);
            jsonNew.renameTo(json);
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Could not read json", e);
            showSnackbar(R.string.snack_updates_check_failed, Snackbar.LENGTH_LONG);
        }
    }

    private void downloadUpdatesList(final boolean manualRefresh) {
        final File jsonFile = Utils.getCachedUpdateList(this);
        final File jsonFileTmp = new File(jsonFile.getAbsolutePath() + UUID.randomUUID());
        String url = Utils.getServerURL(this);
        Log.d(TAG, "Checking " + url);

        DownloadClient.DownloadCallback callback = new DownloadClient.DownloadCallback() {
            @Override
            public void onFailure(final boolean cancelled) {
                Log.e(TAG, "Could not download updates list");
                runOnUiThread(() -> {
                    if (!cancelled) {
                        showSnackbar(R.string.snack_updates_check_failed, Snackbar.LENGTH_LONG);
                    }
                    refreshAnimationStop();
                });
            }

            @Override
            public void onResponse(int statusCode, String url,
                    DownloadClient.Headers headers) {
            }

            @Override
            public void onSuccess(File destination) {
                runOnUiThread(() -> {
                    Log.d(TAG, "List downloaded");
                    processNewJson(jsonFile, jsonFileTmp, manualRefresh);
                    refreshAnimationStop();
                });
            }
        };

        final DownloadClient downloadClient;
        try {
            downloadClient = new DownloadClient.Builder()
                    .setUrl(url)
                    .setDestination(jsonFileTmp)
                    .setDownloadCallback(callback)
                    .build();
        } catch (IOException exception) {
            Log.e(TAG, "Could not build download client");
            showSnackbar(R.string.snack_updates_check_failed, Snackbar.LENGTH_LONG);
            return;
        }

        refreshAnimationStart();
        downloadClient.start();
    }

    private void updateLastCheckedString() {
        final SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(this);
        long lastCheck = preferences.getLong(Constants.PREF_LAST_UPDATE_CHECK, -1) / 1000;
        String lastCheckString = getString(R.string.header_last_updates_check,
                StringGenerator.getDateLocalized(this, DateFormat.LONG, lastCheck),
                StringGenerator.getTimeLocalized(this, lastCheck));
        TextView headerLastCheck = (TextView) findViewById(R.id.header_last_check);
        headerLastCheck.setText(lastCheckString);

        TextView headerBuildVersion = (TextView) findViewById(R.id.header_build_version);
        headerBuildVersion.setText(
                getString(R.string.header_android_version, Build.VERSION.RELEASE));

        TextView headerBuildDate = (TextView) findViewById(R.id.header_build_date);
        headerBuildDate.setText("Current build date: " + StringGenerator.getDateLocalizedUTC(this,
                DateFormat.LONG, BuildInfoUtils.getBuildDateTimestamp()));

        TextView headerBuildType = (TextView) findViewById(R.id.header_build_type);
        String buildType = Utils.getBuildType();
        if (buildType == null || buildType.isEmpty()) {
                headerBuildType.setText("Unofficial or missing OTA info");
                LinearLayout supportLayout=(LinearLayout)this.findViewById(R.id.support_icons);
                supportLayout.setVisibility(LinearLayout.GONE);
        } else {
                headerBuildType.setText("Current build type: " + buildType);
        }

        TextView MaintainerName = (TextView) findViewById(R.id.maintainer_name);
        String maintainer = Utils.getMaintainer();
        if (maintainer == null || maintainer.isEmpty()) {
                MaintainerName.setVisibility(View.GONE);
        } else {
                MaintainerName.setText(
                        getString(R.string.maintainer_name, maintainer));
            MaintainerName.setVisibility(View.VISIBLE);
        }

        ImageView forumImage = (ImageView)findViewById(R.id.support_forum);
        String forum = Utils.getForum();
        forumImage.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_VIEW);
                intent.addCategory(Intent.CATEGORY_BROWSABLE);
                intent.setData(Uri.parse(forum));
                startActivity(intent);
                }
            });

        ImageView telegramImage = (ImageView)findViewById(R.id.support_telegram);
        String telegram = Utils.getTelegram();
        if (telegram == null || telegram.isEmpty()) {
            telegramImage.setVisibility(View.GONE);
        } else {
            telegramImage.setVisibility(View.VISIBLE);
            telegramImage.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Intent intent = new Intent();
                    intent.setAction(Intent.ACTION_VIEW);
                    intent.addCategory(Intent.CATEGORY_BROWSABLE);
                    intent.setData(Uri.parse(telegram));
                    startActivity(intent);
                    }
            });
        }

        ImageView recoveryImage = (ImageView)findViewById(R.id.support_recovery);
        String recovery = Utils.getRecovery();
        if (recovery == null || recovery.isEmpty()) {
            recoveryImage.setVisibility(View.GONE);
        } else {
            recoveryImage.setVisibility(View.VISIBLE);
            recoveryImage.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Intent intent = new Intent();
                    intent.setAction(Intent.ACTION_VIEW);
                    intent.addCategory(Intent.CATEGORY_BROWSABLE);
                    intent.setData(Uri.parse(recovery));
                    startActivity(intent);
                    }
            });
        }

        ImageView paypalImage = (ImageView)findViewById(R.id.support_paypal);
        String paypal = Utils.getPaypal();
        if (paypal == null || recovery.isEmpty()) {
            paypalImage.setVisibility(View.GONE);
        } else {
            paypalImage.setVisibility(View.VISIBLE);
            paypalImage.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Intent intent = new Intent();
                    intent.setAction(Intent.ACTION_VIEW);
                    intent.addCategory(Intent.CATEGORY_BROWSABLE);
                    intent.setData(Uri.parse(paypal));
                    startActivity(intent);
                    }
            });
        }

        ImageView gappsImage = (ImageView)findViewById(R.id.support_gapps);
        String gapps = Utils.getGapps();
        if (gapps == null || gapps.isEmpty()) {
            gappsImage.setVisibility(View.GONE);
        } else {
            gappsImage.setVisibility(View.VISIBLE);
            gappsImage.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Intent intent = new Intent();
                    intent.setAction(Intent.ACTION_VIEW);
                    intent.addCategory(Intent.CATEGORY_BROWSABLE);
                    intent.setData(Uri.parse(gapps));
                    startActivity(intent);
                    }
            });
        }

        ImageView firmwareImage = (ImageView)findViewById(R.id.support_firmware);
        String firmware = Utils.getFirmware();
        if (firmware == null || firmware.isEmpty()) {
            firmwareImage.setVisibility(View.GONE);
        } else {
            firmwareImage.setVisibility(View.VISIBLE);
            firmwareImage.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Intent intent = new Intent();
                    intent.setAction(Intent.ACTION_VIEW);
                    intent.addCategory(Intent.CATEGORY_BROWSABLE);
                    intent.setData(Uri.parse(firmware));
                    startActivity(intent);
                    }
            });
        }

        ImageView modemImage = (ImageView)findViewById(R.id.support_modem);
        String modem = Utils.getModem();
        if (modem == null || modem.isEmpty()) {
            modemImage.setVisibility(View.GONE);
        } else {
            modemImage.setVisibility(View.VISIBLE);
            modemImage.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Intent intent = new Intent();
                    intent.setAction(Intent.ACTION_VIEW);
                    intent.addCategory(Intent.CATEGORY_BROWSABLE);
                    intent.setData(Uri.parse(modem));
                    startActivity(intent);
                    }
            });
        }

        ImageView bootloaderImage = (ImageView)findViewById(R.id.support_bootloader);
        String bootloader = Utils.getBootloader();
        if (bootloader == null || bootloader.isEmpty()) {
            bootloaderImage.setVisibility(View.GONE);
        } else {
            bootloaderImage.setVisibility(View.VISIBLE);
            bootloaderImage.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Intent intent = new Intent();
                    intent.setAction(Intent.ACTION_VIEW);
                    intent.addCategory(Intent.CATEGORY_BROWSABLE);
                    intent.setData(Uri.parse(bootloader));
                    startActivity(intent);
                    }
            });
        }
    }

    private void handleDownloadStatusChange(String downloadId) {
        UpdateInfo update = mUpdaterService.getUpdaterController().getUpdate(downloadId);
        switch (update.getStatus()) {
            case PAUSED_ERROR:
                showSnackbar(R.string.snack_download_failed, Snackbar.LENGTH_LONG);
                break;
            case VERIFICATION_FAILED:
                showSnackbar(R.string.snack_download_verification_failed, Snackbar.LENGTH_LONG);
                break;
            case VERIFIED:
                showSnackbar(R.string.snack_download_verified, Snackbar.LENGTH_LONG);
                break;
        }
    }

    @Override
    public void showSnackbar(int stringId, int duration) {
        Snackbar.make(findViewById(R.id.main_container), stringId, duration).show();
    }

    public void showSnackbarString(String string, int duration) {
        Snackbar.make(findViewById(R.id.main_container), string, duration).show();
    }

    private void refreshAnimationStart() {
        if (mRefreshIconView == null) {
            mRefreshIconView = findViewById(R.id.menu_refresh);
        }
        if (mRefreshIconView != null) {
            mRefreshAnimation.setRepeatCount(Animation.INFINITE);
            mRefreshIconView.startAnimation(mRefreshAnimation);
            mRefreshIconView.setEnabled(false);
        }
    }

    private void refreshAnimationStop() {
        if (mRefreshIconView != null) {
            mRefreshAnimation.setRepeatCount(0);
            mRefreshIconView.setEnabled(true);
        }
    }

    private void showPreferencesDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.preferences_dialog, null);
        Spinner autoCheckInterval =
                view.findViewById(R.id.preferences_auto_updates_check_interval);
        Switch autoDelete = view.findViewById(R.id.preferences_auto_delete_updates);
        Switch dataWarning = view.findViewById(R.id.preferences_mobile_data_warning);
        Switch abPerfMode = view.findViewById(R.id.preferences_ab_perf_mode);
        Switch updateRecovery = view.findViewById(R.id.preferences_update_recovery);

        if (!Utils.isABDevice()) {
            abPerfMode.setVisibility(View.GONE);
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        autoCheckInterval.setSelection(Utils.getUpdateCheckSetting(this));
        autoDelete.setChecked(prefs.getBoolean(Constants.PREF_AUTO_DELETE_UPDATES, false));
        dataWarning.setChecked(prefs.getBoolean(Constants.PREF_MOBILE_DATA_WARNING, true));
        abPerfMode.setChecked(prefs.getBoolean(Constants.PREF_AB_PERF_MODE, true));

        if (getResources().getBoolean(R.bool.config_hideRecoveryUpdate)) {
            // Hide the update feature if explicitely requested.
            // Might be the case of A-only devices using prebuilt vendor images.
            updateRecovery.setVisibility(View.GONE);
        } else if (Utils.isRecoveryUpdateExecPresent()) {
            updateRecovery.setChecked(
                    SystemProperties.getBoolean(Constants.UPDATE_RECOVERY_PROPERTY, false));
        } else {
            // There is no recovery updater script in the device, so the feature is considered
            // forcefully enabled, just to avoid users to be confused and complain that
            // recovery gets overwritten. That's the case of A/B and recovery-in-boot devices.
            updateRecovery.setChecked(true);
            updateRecovery.setOnTouchListener(new View.OnTouchListener() {
                private Toast forcedUpdateToast = null;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (forcedUpdateToast != null) {
                        forcedUpdateToast.cancel();
                    }
                    forcedUpdateToast = Toast.makeText(getApplicationContext(),
                            getString(R.string.toast_forced_update_recovery), Toast.LENGTH_SHORT);
                    forcedUpdateToast.show();
                    return true;
                }
            });
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_preferences)
                .setView(view)
                .setOnDismissListener(dialogInterface -> {
                    prefs.edit()
                            .putInt(Constants.PREF_AUTO_UPDATES_CHECK_INTERVAL,
                                    autoCheckInterval.getSelectedItemPosition())
                            .putBoolean(Constants.PREF_AUTO_DELETE_UPDATES,
                                    autoDelete.isChecked())
                            .putBoolean(Constants.PREF_MOBILE_DATA_WARNING,
                                    dataWarning.isChecked())
                            .putBoolean(Constants.PREF_AB_PERF_MODE,
                                    abPerfMode.isChecked())
                            .apply();

                    if (Utils.isUpdateCheckEnabled(this)) {
                        UpdatesCheckReceiver.scheduleRepeatingUpdatesCheck(this);
                    } else {
                        UpdatesCheckReceiver.cancelRepeatingUpdatesCheck(this);
                        UpdatesCheckReceiver.cancelUpdatesCheck(this);
                    }

                    if (Utils.isABDevice()) {
                        boolean enableABPerfMode = abPerfMode.isChecked();
                        mUpdaterService.getUpdaterController().setPerformanceMode(enableABPerfMode);
                    }
                    if (Utils.isRecoveryUpdateExecPresent()) {
                        boolean enableRecoveryUpdate = updateRecovery.isChecked();
                        SystemProperties.set(Constants.UPDATE_RECOVERY_PROPERTY,
                                String.valueOf(enableRecoveryUpdate));
                    }
                })
                .show();
    }

    public static void prepareSfMirrorsData (UpdateInfo updateInfo, UpdatesActivity mUpdatesActivity) {

        new AsyncTask<UpdateInfo, Void, Map<String, String>>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();

                if (Utils.getSfRankSortSetting(mUpdatesActivity))
                    mUpdatesActivity.showSnackbar(R.string.snack_ranking_sf_mirrors, Snackbar.LENGTH_INDEFINITE);
                else
                    mUpdatesActivity.showSnackbar(R.string.snack_fetching_sf_mirrors, Snackbar.LENGTH_INDEFINITE);
            }

            @Override
            protected Map<String, String> doInBackground(UpdateInfo... update) {

                Boolean sfRankSort = Utils.getSfRankSortSetting(mUpdatesActivity);
                sf_mirrors = new LinkedHashMap<>();

                try {
                    Thread mirrorsData = new Thread(() -> sf_mirrors = UpdaterController.sourceforgeMirrors(update[0], sfRankSort));
                    mirrorsData.start();
                    mirrorsData.join();
                } catch (InterruptedException e) {
                    Log.d(TAG, "Mirrors data thread interrupted");
                }

                return sf_mirrors;
            }

            @Override
            protected void onPostExecute(Map<String, String> sf_mirrors) {
                super.onPostExecute(sf_mirrors);

                if (!sf_mirrors.isEmpty()) {
                    mUpdatesActivity.showSnackbar(R.string.snack_fetched_sf_mirrors, Snackbar.LENGTH_SHORT);
                    showSfMirrorsDialog(sf_mirrors, mUpdatesActivity, updateInfo);
                } else {
                    mUpdatesActivity.showSnackbar(R.string.snack_failed_sf_mirrors, Snackbar.LENGTH_LONG);
                }
            }
        }.execute(updateInfo);
    }

    private static void showSfMirrorsDialog(Map<String, String> sf_mirrors, UpdatesActivity mUpdatesActivity, UpdateInfo updateInfo) {
        final MirrorsDbHelper mirrorsDbHelper = MirrorsDbHelper.getInstance(mUpdatesActivity);
        String[] mirrors = sf_mirrors.keySet().toArray(new String[0]);
        String[] mirrors_pings = new String[mirrors.length];
        String downloadId = updateInfo.getDownloadId();
        String prevMirrorName = mirrorsDbHelper.getMirrorName(updateInfo.getDownloadId());
        Boolean isRankSort = Utils.getSfRankSortSetting(mUpdatesActivity);
        int setMirrorPos = 0;

        for (int i=0; i<mirrors.length; i++) {
            if (mirrors[i].equals(prevMirrorName)) {
                setMirrorPos = i;
                break;
            }
        }

        // If failed to find the previous mirror force set to the first available one
        if (setMirrorPos == 0) {
            mirrorsDbHelper.setMirrorName(mirrors[0], downloadId);
            UpdaterController.setSfMirror(updateInfo, mUpdatesActivity, mirrors[0]);
            mAdapter.notifyItemChanged(downloadId);
        }

        if (isRankSort) {
            int mirrorName =0;
            for (Map.Entry<Double, String> pingVals : UpdaterController.sorted_ranked_mirrors.entrySet()) {
                mirrors_pings[mirrorName] = mirrors[mirrorName] + "  (" + pingVals.getKey() + ")";
                mirrorName++;
            }
        }

        new AlertDialog.Builder(mUpdatesActivity)
                .setTitle(R.string.sf_dialog_title)
                .setSingleChoiceItems((isRankSort) ? mirrors_pings : mirrors, setMirrorPos, (dialogInterface, i) -> {

                    mirrorsDbHelper.setMirrorName(mirrors[i], downloadId);
                    UpdaterController.setSfMirror(updateInfo, mUpdatesActivity, mirrors[i]);
                    mAdapter.notifyItemChanged(downloadId);

                    Log.d(TAG, "Selected Mirror!" + mirrors[i]);

                }).create()
                .show();
    }

    private void showSfMirrorPreferencesDialog () {
        View view = LayoutInflater.from(this).inflate(R.layout.sf_mirror_preferences, null);
        Switch sf_rank_sort = view.findViewById(R.id.rank_and_sort_mirrors);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        sf_rank_sort.setChecked(prefs.getBoolean(Constants.PREF_SF_RANK_SORT, false));

        new AlertDialog.Builder(this)
                .setTitle(R.string.sf_mirror_preferences)
                .setView(view)
                .setOnDismissListener(dialogInterface -> prefs.edit()
                        .putBoolean(Constants.PREF_SF_RANK_SORT, sf_rank_sort.isChecked())
                        .apply())
                .show();
    }

    private void performFileSearch() {
        if (checkStoragePermissions()) {
            return;
        }
        Intent chooseFile;
        Intent intent;
        chooseFile = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        chooseFile.setType("application/zip");
        intent = Intent.createChooser(chooseFile, "Choose a file");
        startActivityForResult(intent, READ_REQUEST_CODE);
    }

    private boolean hasStoragePermission() {
        int result = checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    private boolean checkStoragePermissions() {
        if (hasStoragePermission()) {
            return false;
        }

        final String[] perms = {Manifest.permission.READ_EXTERNAL_STORAGE};
        requestPermissions(perms, PERMISSION_REQUEST_CODE);
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                } else {
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                                != PackageManager.PERMISSION_GRANTED) {
                            showMessageOKCancel(getString(R.string.dialog_permissions_title),
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                checkStoragePermissions();
                                            }
                                        }
                                    });
                        }
                }
                break;
        }
    }

    private void showMessageOKCancel(String message, DialogInterface.OnClickListener okListener) {
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton(getString(R.string.dialog_permissions_ask), okListener)
                .setNegativeButton(getString(R.string.dialog_permissions_dismiss), null)
                .create()
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            Uri uri = null;
            if (resultData != null) {
                uri = resultData.getData();
                addLocalUpdateInfo(uri);
            }
        }
        super.onActivityResult(requestCode, resultCode, resultData);
    }

    private void addLocalUpdateInfo(Uri uri) {
        String path = FileUtils.getRealPath(this, uri);
        Update localUpdate = new Update();
        File file = new File(path);
        localUpdate.setFile(file);
        localUpdate.setName(file.getName());
        localUpdate.setFileSize(file.length());
        localUpdate.setTimestamp(new Date().getTime()/1000L);
        localUpdate.setDownloadId(String.valueOf(new Date().getTime()/1000L));
        localUpdate.setVersion("");
        localUpdate.setPersistentStatus(UpdateStatus.Persistent.LOCAL);
        localUpdate.setStatus(UpdateStatus.DOWNLOADED);

        Log.d(TAG, "Adding local updates");
        UpdaterController controller = mUpdaterService.getUpdaterController();
        boolean newUpdates = false;

        List<UpdateInfo> updates = new ArrayList<>();
        updates.add(localUpdate);
        List<String> updatesOnline = new ArrayList<>();
        for (UpdateInfo update : updates) {
            newUpdates |= controller.addUpdate(update);
            updatesOnline.add(0, update.getDownloadId());
        }

        controller.setUpdatesAvailableOnline(updatesOnline, false);

        controller.verifyUpdateAsync(localUpdate.getDownloadId());
        controller.notifyUpdateChange(localUpdate.getDownloadId());

        List<String> updateIds = new ArrayList<>();
        List<UpdateInfo> sortedUpdates = controller.getUpdates();
        if (sortedUpdates.isEmpty()) {
            findViewById(R.id.no_new_updates_view).setVisibility(View.VISIBLE);
            findViewById(R.id.recycler_view).setVisibility(View.GONE);
        } else {
            findViewById(R.id.no_new_updates_view).setVisibility(View.GONE);
            findViewById(R.id.recycler_view).setVisibility(View.VISIBLE);
            sortedUpdates.sort((u1, u2) -> Long.compare(u2.getTimestamp(), u1.getTimestamp()));
            for (UpdateInfo update : sortedUpdates) {
                updateIds.add(update.getDownloadId());
            }
            mAdapter.setData(updateIds);
            mAdapter.notifyDataSetChanged();
        }
    }
}

/*
 * SPDX-FileCopyrightText: 2017 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.crdroid.updater;

import androidx.appcompat.app.AppCompatActivity;

import com.crdroid.updater.model.UpdateInfo;

public abstract class UpdatesListActivity extends AppCompatActivity {
    public abstract void exportUpdate(UpdateInfo update);
    public abstract void showSnackbar(int stringId, int duration);
}

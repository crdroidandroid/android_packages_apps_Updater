/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.deviceinfo

import android.os.Build
import android.os.SystemProperties
import com.android.settingslib.DeviceInfoUtils as SettingsLibDeviceInfoUtils

object DeviceInfoUtils : SettingsLibDeviceInfoUtils() {

    private const val PROP_AB_DEVICE = "ro.build.ab_update"
    private const val PROP_ALLOW_MAJOR_UPGRADES = "crdroid.updater.allow_major_upgrades"
    private const val PROP_BUILD_DATE = "ro.build.date.utc"
    private const val PROP_BUILD_VERSION = "ro.crdroid.build.version"
    private const val PROP_DEVICE = "ro.crdroid.device"
    private const val PROP_NEXT_DEVICE = "ro.updater.next_device"
    private const val PROP_UPDATER_ALLOW_DOWNGRADING = "crdroid.updater.allow_downgrading"
    private const val PROP_UPDATE_RECOVERY = "persist.vendor.recovery_update"

    // Read-only
    val androidVersion: String = Build.VERSION.RELEASE

    val buildSecurityPatch: String = Build.VERSION.SECURITY_PATCH

    val sdkLevel: Int = Build.VERSION.SDK_INT

    @JvmStatic
    val buildDateTimestamp: Long = SystemProperties.getLong(PROP_BUILD_DATE, 0)

    @JvmStatic
    val buildVersion: String = SystemProperties.get(PROP_BUILD_VERSION, "")

    @JvmStatic
    val device: String = SystemProperties.get(PROP_NEXT_DEVICE, SystemProperties.get(PROP_DEVICE))

    @JvmStatic
    val isABDevice: Boolean = SystemProperties.getBoolean(PROP_AB_DEVICE, false)

    // Mutable at runtime
    @JvmStatic
    val isDowngradingAllowed: Boolean
        get() = SystemProperties.getBoolean(PROP_UPDATER_ALLOW_DOWNGRADING, false)

    @JvmStatic
    val isMajorUpdateAllowed: Boolean
        get() = SystemProperties.getBoolean(PROP_ALLOW_MAJOR_UPGRADES, false)

    @JvmStatic
    var isRecoveryUpdateEnabled: Boolean
        get() = SystemProperties.getBoolean(PROP_UPDATE_RECOVERY, false)
        set(value) = SystemProperties.set(PROP_UPDATE_RECOVERY, value.toString())
}

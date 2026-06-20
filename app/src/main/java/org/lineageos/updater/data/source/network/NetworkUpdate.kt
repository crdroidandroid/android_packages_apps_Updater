/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(ExperimentalSerializationApi::class)

package org.lineageos.updater.data.source.network

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import org.lineageos.updater.data.Update

@Suppress("PROVIDED_RUNTIME_TOO_LOW")
@Serializable
@JsonIgnoreUnknownKeys
data class NetworkUpdateResponse(
    @SerialName("response") val response: List<NetworkUpdate> = emptyList(),
)

@Suppress("PROVIDED_RUNTIME_TOO_LOW")
@Serializable
@JsonIgnoreUnknownKeys
data class NetworkUpdate(
    @SerialName("filename") val filename: String,
    @SerialName("download") val download: String,
    @SerialName("timestamp") val timestamp: Long,
    @SerialName("sha256") val sha256: String,
    @SerialName("size") val size: Long,
    @SerialName("version") val version: String,
    @SerialName("md5") val md5: String? = null,
    @SerialName("buildtype") val buildType: String? = null,
    // crDroid additional metadata
    @SerialName("maintainer") val maintainer: String? = null,
    @SerialName("oem") val oem: String? = null,
    @SerialName("device") val device: String? = null,
    @SerialName("forum") val forum: String? = null,
    @SerialName("gapps") val gapps: String? = null,
    @SerialName("firmware") val firmware: String? = null,
    @SerialName("modem") val modem: String? = null,
    @SerialName("bootloader") val bootloader: String? = null,
    @SerialName("recovery") val recovery: String? = null,
    @SerialName("paypal") val paypal: String? = null,
    @SerialName("telegram") val telegram: String? = null,
    @SerialName("os_patch_level") val osPatchLevel: String? = null,
    @SerialName("os_sdk_level") val osSdkLevel: Int? = null,
    // Not used
    @SerialName("ota_property_files") val otaPropertyFiles: String? = null,
)

fun NetworkUpdate.toUpdate(): Update = Update(
    downloadId = sha256,
    name = filename,
    timestamp = timestamp,
    type = buildType,
    fileSize = size,
    downloadUrl = download,
    version = version,
    osPatchLevel = osPatchLevel,
    osSdkLevel = osSdkLevel ?: 0,
    isAvailableOnline = true,
)

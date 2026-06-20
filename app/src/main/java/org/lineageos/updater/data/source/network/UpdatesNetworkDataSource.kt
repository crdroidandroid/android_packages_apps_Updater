/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.updater.data.source.network

import android.content.Context
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.lineageos.updater.R
import org.lineageos.updater.deviceinfo.DeviceInfoUtils
import java.io.IOException
import java.util.concurrent.TimeUnit

class UpdatesNetworkDataSource(private val context: Context) {
    private val serverUrl: String
        get() {
            val base = context.getString(R.string.updater_server_url)
            require(base.startsWith("https://")) {
                "Update server URL must use HTTPS: $base"
            }
            return base
                .replace("{device}", DeviceInfoUtils.device)
        }

    private val client = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    fun fetchUpdates(): List<NetworkUpdate> {
        val request = Request.Builder()
            .url(serverUrl)
            .build()

        val responseBody = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected HTTP status: ${response.code}")
            }

            response.body?.string() ?: throw IOException("Empty response body")
        }

        return json.decodeFromString<NetworkUpdateResponse>(responseBody).response
    }
}

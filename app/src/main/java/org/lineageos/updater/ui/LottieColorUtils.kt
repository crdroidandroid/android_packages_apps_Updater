/*
 * SPDX-FileCopyrightText: The Android Open Source Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.updater.ui

import android.graphics.ColorFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.colorResource
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieDynamicProperty
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import org.lineageos.updater.R

internal object LottieColorUtils {
    @Composable
    private fun createOnSurfaceFilter(): LottieDynamicProperty<ColorFilter> {
        val color = MaterialTheme.colorScheme.onSurface
        return rememberLottieDynamicProperty(
            property = LottieProperty.COLOR_FILTER,
            keyPath = arrayOf("**", ".onSurface", "**"),
        ) {
            PorterDuffColorFilter(color.toArgb(), PorterDuff.Mode.SRC_ATOP)
        }
    }

    @Composable
    private fun createBrandFilter(): LottieDynamicProperty<ColorFilter> {
        val color = colorResource(R.color.brand_primary)
        return rememberLottieDynamicProperty(
            property = LottieProperty.COLOR_FILTER,
            keyPath = arrayOf("**", "fill", "**"),
        ) {
            PorterDuffColorFilter(color.toArgb(), PorterDuff.Mode.SRC_ATOP)
        }
    }

    @Composable
    fun getDefaultDynamicProperties() =
        rememberLottieDynamicProperties(
            createOnSurfaceFilter(),
            createBrandFilter(),
        )
}

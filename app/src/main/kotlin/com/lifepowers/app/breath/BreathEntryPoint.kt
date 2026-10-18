package com.lifepowers.app.breath

import android.content.Context
import com.lifepowers.app.domain.BreathSessionController
import com.lifepowers.app.domain.HeartLink
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** Lets the foreground service reach the singletons without being a Hilt entry point itself. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface BreathEntryPoint {
    fun controller(): BreathSessionController
    fun heartLink(): HeartLink
}

internal fun breathEntryPoint(context: Context): BreathEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, BreathEntryPoint::class.java)

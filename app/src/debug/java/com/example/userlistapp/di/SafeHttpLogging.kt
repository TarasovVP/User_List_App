package com.example.userlistapp.di

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

internal fun OkHttpClient.Builder.addSafeDebugLogging(): OkHttpClient.Builder = apply {
    addInterceptor(
        HttpLoggingInterceptor().apply {
            redactHeader("Authorization")
            redactHeader("Cookie")
            redactHeader("Set-Cookie")
            level = HttpLoggingInterceptor.Level.BASIC
        }
    )
}

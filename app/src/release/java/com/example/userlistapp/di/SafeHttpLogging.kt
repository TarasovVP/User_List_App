package com.example.userlistapp.di

import okhttp3.OkHttpClient

internal fun OkHttpClient.Builder.addSafeDebugLogging(): OkHttpClient.Builder = this

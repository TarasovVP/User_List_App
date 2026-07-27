package com.example.userlistapp.core.common

fun interface TimeProvider {
    fun currentTimeMillis(): Long
}

object SystemTimeProvider : TimeProvider {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}

package com.example.userlistapp.data.local

internal fun nextSnapshotBatchId(candidateBatchId: Long, latestBatchId: Long?): Long = when {
    latestBatchId == null || latestBatchId < candidateBatchId -> candidateBatchId
    latestBatchId < Long.MAX_VALUE -> latestBatchId + 1
    else -> Long.MAX_VALUE
}

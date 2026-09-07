package com.example.aicleanphonestorage.feature.networktraffic.data

interface NetworkTrafficRepository {
    suspend fun hasUsageAccess(): Boolean
    suspend fun load(period: TrafficPeriod, onProgress: (TrafficProgress) -> Unit): TrafficSnapshot
}

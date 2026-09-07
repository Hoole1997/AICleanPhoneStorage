package com.example.aicleanphonestorage.feature.networktraffic.data

import com.example.aicleanphonestorage.core.data.OneShotTransfer

/** 具体功能只定义数据类型；容量、有效期与一次性消费由通用交接容器负责。 */
typealias TrafficSnapshotTransfer = OneShotTransfer<TrafficSnapshot>

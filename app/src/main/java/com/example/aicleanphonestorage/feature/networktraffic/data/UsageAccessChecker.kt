package com.example.aicleanphonestorage.feature.networktraffic.data

import android.content.Context
import com.example.aicleanphonestorage.core.permissions.PermissionChecks

class UsageAccessChecker(context:Context){
    private val app=context.applicationContext
    fun isGranted()=PermissionChecks.usage(app)
}

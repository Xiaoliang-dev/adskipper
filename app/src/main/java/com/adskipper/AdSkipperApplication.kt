package com.adskipper

import android.app.Application
import com.adskipper.data.RuleDatabase
import com.adskipper.data.RuleManager

class AdSkipperApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Initialize RuleManager eagerly
        RuleManager.getInstance(this)
    }
}
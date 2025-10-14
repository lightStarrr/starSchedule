package com.star.schedule.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

class LiveNotification : Service() {
    override fun onCreate() {
        super.onCreate()
        Log.d("LiveNotification", "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("LiveNotification", "Service started")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.d("LiveNotification", "Service bound")
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("LiveNotification", "Service destroyed")
    }

}
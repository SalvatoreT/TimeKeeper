package dev.sal.timekeeper

import android.app.Application

class TimeKeeperApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CalendarSyncWorker.schedule(this)
    }
}

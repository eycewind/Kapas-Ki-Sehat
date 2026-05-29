package com.example

import android.app.Application
import androidx.room.Room
import com.example.database.AppDatabase

class CottonAceApplication : Application() {
    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "cotton_ace.db").build()
    }
}

package com.example

import android.app.Application
import androidx.room.Room
import com.example.database.AppDatabase
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

class CottonAceApplication : Application() {

    val database: AppDatabase by lazy {
        Room.databaseBuilder(
            context = this,
            klass = AppDatabase::class.java,
            name = "cotton_ace.db"
        ).build()
    }

    // Correct top-level property initialization assignment rule:
    val supabaseClient = createSupabaseClient(
        supabaseUrl = "https://wmfqxrzoploggezfmnjn.supabase.co",
        supabaseKey = "sb_publishable_flQOih4VRvMCs67leUY3Zg_GFeGcNf-"
    ) {
        install(Postgrest)
    }
}

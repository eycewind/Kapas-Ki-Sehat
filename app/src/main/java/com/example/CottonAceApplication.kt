package com.example

import android.app.Application
import androidx.room.Room
import com.example.database.AppDatabase
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

class CottonAceApplication : Application() {

    val database: AppDatabase by lazy {
        Room.databaseBuilder(
            context = this,
            klass = AppDatabase::class.java,
            name = "cotton_ace.db"
        )
        // Local scan history is not precious during development; wipe and rebuild
        // rather than write migrations for every schema change.
        .fallbackToDestructiveMigration()
        .build()
    }

    // Keys come from .env (gitignored) via the Secrets Gradle Plugin → BuildConfig.
    // Must be JWT-format keys (eyJh...) — sb_publishable_ is rejected by Storage.
    // See CONTRACTS.md §8 and .env.example for the required key names.
    val supabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY
    ) {
        install(Postgrest)
        install(Storage)
    }
}

package com.wearaware.app.data.repository

import android.content.SharedPreferences
import com.google.gson.Gson
import com.wearaware.app.domain.model.KnownTargetSignature
import com.wearaware.app.domain.repository.KnownTargetRepository
import javax.inject.Inject
import javax.inject.Singleton

internal const val KEY_KNOWN_TARGET_SIGNATURE = "known_target_signature"

@Singleton
class KnownTargetRepositoryImpl @Inject constructor(
    private val prefs: SharedPreferences,
    private val gson: Gson
) : KnownTargetRepository {

    override fun load(): KnownTargetSignature? {
        val json = prefs.getString(KEY_KNOWN_TARGET_SIGNATURE, null) ?: return null
        return runCatching { gson.fromJson(json, KnownTargetSignature::class.java) }.getOrNull()
    }

    override fun save(signature: KnownTargetSignature) {
        // commit() instead of apply() — user-initiated save must survive an immediate process kill
        prefs.edit().putString(KEY_KNOWN_TARGET_SIGNATURE, gson.toJson(signature)).commit()
    }

    override fun clear() {
        prefs.edit().remove(KEY_KNOWN_TARGET_SIGNATURE).apply()
    }
}

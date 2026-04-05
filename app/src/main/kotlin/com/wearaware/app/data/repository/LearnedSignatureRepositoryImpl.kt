package com.wearaware.app.data.repository

import android.content.SharedPreferences
import com.google.gson.Gson
import com.wearaware.app.domain.model.LearnedDeviceSignature
import com.wearaware.app.domain.repository.LearnedSignatureRepository
import javax.inject.Inject
import javax.inject.Singleton

internal const val KEY_LEARNED_SIGNATURE = "learned_device_signature"

@Singleton
class LearnedSignatureRepositoryImpl @Inject constructor(
    private val prefs: SharedPreferences,
    private val gson: Gson
) : LearnedSignatureRepository {

    override fun load(): LearnedDeviceSignature? {
        val json = prefs.getString(KEY_LEARNED_SIGNATURE, null) ?: return null
        return runCatching { gson.fromJson(json, LearnedDeviceSignature::class.java) }.getOrNull()
    }

    override fun save(signature: LearnedDeviceSignature) {
        prefs.edit().putString(KEY_LEARNED_SIGNATURE, gson.toJson(signature)).apply()
    }

    override fun clear() {
        prefs.edit().remove(KEY_LEARNED_SIGNATURE).apply()
    }
}

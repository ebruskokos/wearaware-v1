package com.wearaware.app.data.repository

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.wearaware.app.domain.model.KnownTargetSignature
import com.wearaware.app.domain.model.SignatureSnapshot
import com.wearaware.app.domain.repository.KnownTargetRepository
import javax.inject.Inject
import javax.inject.Singleton

internal const val KEY_KNOWN_TARGET_SIGNATURE = "known_target_signature"
private const val KEY_SIGNATURE_HISTORY = "known_target_signature_history"

@Singleton
class KnownTargetRepositoryImpl @Inject constructor(
    private val prefs: SharedPreferences,
    private val gson: Gson
) : KnownTargetRepository {

    companion object {
        /** Maximum number of historical snapshots retained. */
        const val MAX_HISTORY = 8
    }

    override fun load(): KnownTargetSignature? {
        val json = prefs.getString(KEY_KNOWN_TARGET_SIGNATURE, null) ?: return null
        return runCatching { gson.fromJson(json, KnownTargetSignature::class.java) }.getOrNull()
    }

    override fun save(signature: KnownTargetSignature) {
        // commit() instead of apply() — user-initiated save must survive an immediate process kill
        prefs.edit().putString(KEY_KNOWN_TARGET_SIGNATURE, gson.toJson(signature)).commit()
    }

    override fun saveWithHistory(signature: KnownTargetSignature, deltaDescription: String) {
        runCatching {
            val snapshot = SignatureSnapshot(
                version = signature.version,
                savedAt = System.currentTimeMillis(),
                deltaDescription = deltaDescription,
                signature = signature
            )
            val history = loadHistory().toMutableList()
            history.add(0, snapshot)
            if (history.size > MAX_HISTORY) {
                history.subList(MAX_HISTORY, history.size).clear()
            }
            prefs.edit()
                .putString(KEY_KNOWN_TARGET_SIGNATURE, gson.toJson(signature))
                .putString(KEY_SIGNATURE_HISTORY, gson.toJson(history))
                .commit()
        }.onFailure {
            // History write failed — fall back to plain save so signature is never lost
            runCatching { save(signature) }
        }
    }

    override fun loadHistory(): List<SignatureSnapshot> {
        val json = prefs.getString(KEY_SIGNATURE_HISTORY, null) ?: return emptyList()
        val type = object : TypeToken<List<SignatureSnapshot>>() {}.type
        return runCatching { gson.fromJson<List<SignatureSnapshot>>(json, type) }.getOrNull()
            ?: emptyList()
    }

    override fun rollbackTo(version: Int): KnownTargetSignature? {
        val snapshot = loadHistory().firstOrNull { it.version == version } ?: return null
        save(snapshot.signature)
        return snapshot.signature
    }

    override fun clear() {
        prefs.edit()
            .remove(KEY_KNOWN_TARGET_SIGNATURE)
            .remove(KEY_SIGNATURE_HISTORY)
            .apply()
    }
}

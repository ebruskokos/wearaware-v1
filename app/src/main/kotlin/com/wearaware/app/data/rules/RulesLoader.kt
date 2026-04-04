package com.wearaware.app.data.rules

import android.content.Context
import com.google.gson.Gson
import com.wearaware.app.data.rules.dto.RulesDataDto
import com.wearaware.app.domain.repository.RulesData
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PURPOSE: Reads and parses fingerprint_rules.json from the app's assets directory.
 * LIMITATIONS: Rules are bundled — cannot be updated without an app release.
 *   This is intentional for v1.0 audit integrity.
 * NOTES: Results are NOT cached here. Caching is the responsibility of RulesRepositoryImpl.
 */
@Singleton
class RulesLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()

    /**
     * Reads fingerprint_rules.json from assets and returns the parsed domain model.
     * Throws if the file is missing or malformed — this is a programming error, not
     * a recoverable runtime condition.
     */
    fun load(): RulesData {
        val json = context.assets
            .open("fingerprint_rules.json")
            .bufferedReader()
            .use { it.readText() }
        val dto = gson.fromJson(json, RulesDataDto::class.java)
        return dto.toDomain()
    }
}

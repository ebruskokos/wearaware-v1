package com.wearaware.app.data.repository

import com.wearaware.app.data.rules.RulesLoader
import com.wearaware.app.domain.repository.RulesData
import com.wearaware.app.domain.repository.RulesRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PURPOSE: Implements RulesRepository by loading from RulesLoader and caching the result.
 *   The rules file never changes at runtime, so one load per app lifecycle is correct.
 * NOTES: lazy { } ensures load() is called at most once even if getRules() is called
 *   concurrently. Kotlin lazy delegation is thread-safe by default.
 */
@Singleton
class RulesRepositoryImpl @Inject constructor(
    private val rulesLoader: RulesLoader
) : RulesRepository {

    private val cachedRules: RulesData by lazy { rulesLoader.load() }

    /** Returns the parsed and cached rules. Loads from asset on first call. */
    override fun getRules(): RulesData = cachedRules
}

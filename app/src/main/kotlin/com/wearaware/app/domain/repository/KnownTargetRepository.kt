package com.wearaware.app.domain.repository

import com.wearaware.app.domain.model.KnownTargetSignature

interface KnownTargetRepository {
    fun load(): KnownTargetSignature?
    fun save(signature: KnownTargetSignature)
    fun clear()
}

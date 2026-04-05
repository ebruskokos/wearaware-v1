package com.wearaware.app.domain.repository

import com.wearaware.app.domain.model.LearnedDeviceSignature

interface LearnedSignatureRepository {
    fun load(): LearnedDeviceSignature?
    fun save(signature: LearnedDeviceSignature)
    fun clear()
}

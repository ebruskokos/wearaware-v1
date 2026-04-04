package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.ClassificationResult
import com.wearaware.app.domain.model.RawScanResult
import com.wearaware.app.domain.rules.FingerprintClassifier
import javax.inject.Inject

/**
 * PURPOSE: Thin wrapper around FingerprintClassifier for on-demand classification.
 *   Classification in normal scanning flow happens inside BleRepositoryImpl;
 *   this use case is useful for testing and future one-off classification needs.
 */
class ClassifyDeviceUseCase @Inject constructor(
    private val classifier: FingerprintClassifier
) {
    operator fun invoke(scan: RawScanResult): ClassificationResult = classifier.classify(scan)
}

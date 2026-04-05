package com.wearaware.app.domain.model

enum class CompareConfidence {
    HIGH,    // score >= 9
    MEDIUM,  // score >= 6
    LOW,     // score >= 3 — shown in list but not highlighted as top candidate
    NONE     // score < 3 — excluded from results
}

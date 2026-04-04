package com.wearaware.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wearaware.app.domain.model.ProximityLabel
import com.wearaware.app.ui.theme.SignalVeryClose
import com.wearaware.app.ui.theme.SignalStrong
import com.wearaware.app.ui.theme.SignalNearby
import com.wearaware.app.ui.theme.SignalWeak
import com.wearaware.app.ui.theme.SignalUnknown

private fun barCountFor(label: ProximityLabel): Int = when (label) {
    ProximityLabel.VERY_CLOSE -> 5
    ProximityLabel.STRONG     -> 4
    ProximityLabel.NEARBY     -> 3
    ProximityLabel.WEAK       -> 2
    ProximityLabel.UNKNOWN    -> 1
}

private fun barColorFor(label: ProximityLabel): Color = when (label) {
    ProximityLabel.VERY_CLOSE -> SignalVeryClose
    ProximityLabel.STRONG     -> SignalStrong
    ProximityLabel.NEARBY     -> SignalNearby
    ProximityLabel.WEAK       -> SignalWeak
    ProximityLabel.UNKNOWN    -> SignalUnknown
}

private fun labelDescriptionFor(label: ProximityLabel): String = when (label) {
    ProximityLabel.VERY_CLOSE -> "Signal strength: very close (5 bars)"
    ProximityLabel.STRONG     -> "Signal strength: strong (4 bars)"
    ProximityLabel.NEARBY     -> "Signal strength: nearby (3 bars)"
    ProximityLabel.WEAK       -> "Signal strength: weak (2 bars)"
    ProximityLabel.UNKNOWN    -> "Signal strength: unknown (1 bar)"
}

@Composable
fun SignalBars(
    proximity: ProximityLabel,
    modifier: Modifier = Modifier
) {
    val filledBars = barCountFor(proximity)
    val color = barColorFor(proximity)
    val description = labelDescriptionFor(proximity)

    Row(
        modifier = modifier
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        for (i in 1..5) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height((4 + i * 4).dp)
                    .background(
                        color = if (i <= filledBars) color else SignalUnknown.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)
                    )
            )
        }
    }
}

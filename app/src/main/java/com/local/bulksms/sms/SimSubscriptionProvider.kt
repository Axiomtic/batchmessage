package com.local.bulksms.sms

import android.content.Context
import android.telephony.SubscriptionManager

data class SubscriptionSnapshot(
    val subscriptionId: Int,
    val displayName: String,
    val carrierName: String,
    val slotIndex: Int,
)

data class SimOption(
    val subscriptionId: Int,
    val displayLabel: String,
    val slotIndex: Int,
)

fun interface SubscriptionSource {
    fun active(): List<SubscriptionSnapshot>
}

class SimSubscriptionProvider(private val source: SubscriptionSource) {
    constructor(context: Context) : this(
        SubscriptionSource {
            val manager = context.getSystemService(SubscriptionManager::class.java)
            manager.activeSubscriptionInfoList.orEmpty().map { info ->
                SubscriptionSnapshot(
                    subscriptionId = info.subscriptionId,
                    displayName = info.displayName?.toString().orEmpty(),
                    carrierName = info.carrierName?.toString().orEmpty(),
                    slotIndex = info.simSlotIndex,
                )
            }
        },
    )

    fun active(): List<SimOption> = source.active()
        .asSequence()
        .filter { it.subscriptionId >= 0 }
        .sortedWith(compareBy<SubscriptionSnapshot> { it.slotIndex }.thenBy { it.subscriptionId })
        .map { snapshot ->
            SimOption(
                subscriptionId = snapshot.subscriptionId,
                displayLabel = snapshot.displayName.trim()
                    .ifBlank { snapshot.carrierName.trim() }
                    .ifBlank {
                        if (snapshot.slotIndex >= 0) "SIM ${snapshot.slotIndex + 1}" else "SIM"
                    },
                slotIndex = snapshot.slotIndex,
            )
        }
        .toList()
}

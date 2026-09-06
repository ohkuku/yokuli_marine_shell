package com.yokuli.marine.data.phone

import com.yokuli.marine.data.model.DataKey
import com.yokuli.marine.data.source.MarineSourceSnapshot
import com.yokuli.marine.data.source.SourceDecisionStatus

/**
 * Product demand for Phone location. It is derived from selected OS data, never a free-standing
 * on/off preference in the new Data app.
 */
data class PhoneLocationDemand(
    val requiredKeys: Set<DataKey>,
) {
    val required: Boolean get() = requiredKeys.isNotEmpty()

    companion object {
        val NONE = PhoneLocationDemand(emptySet())
    }
}

object PhoneLocationDemandPolicy {
    fun resolve(snapshot: MarineSourceSnapshot): PhoneLocationDemand {
        val required = snapshot.decisions.asSequence()
            .filter { decision ->
                decision.selectedSource == PHONE_SYSTEM_LOCATION_SOURCE &&
                    decision.status != SourceDecisionStatus.DISABLED
            }
            .map { it.key }
            .toSet()
        return PhoneLocationDemand(required)
    }
}

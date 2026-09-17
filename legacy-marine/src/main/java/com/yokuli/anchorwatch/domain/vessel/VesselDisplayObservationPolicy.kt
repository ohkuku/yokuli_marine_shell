package com.yokuli.anchorwatch.domain.vessel

import com.yokuli.anchorwatch.domain.vessel.source.MetricSourceEligibility

/** 显示可以保留上次读数；采纳状态、测量时间、来源代次始终不变。 */
object VesselDisplayObservationPolicy {
    fun retainDerived(previous:VesselObservation<Double>?,next:VesselObservation<Double>,currentSourceIds:Set<String>):VesselObservation<Double> {
        if(next.value!=null||previous==null)return next
        val sources=(previous.provenanceDetail as? VesselProvenance.Derived)?.inputs?:listOfNotNull(previous.sourceIdentity)
        if(sources.isEmpty()||sources.any{it.id !in currentSourceIds})return next
        return previous.copy(freshness=VesselDataFreshness.STALE)
    }
    fun <T> resolve(selection:VesselSourceSelection<T>,previous:VesselObservation<T>?,now:Long):VesselObservation<T>? {
        val candidate=selection.selected ?: selection.candidates.firstOrNull {
            it.source.id==previous?.sourceIdentity?.id && it.validity !in setOf(CandidateValidity.INVALID,CandidateValidity.DISABLED)
        } ?: return null
        val age=now-candidate.receivedElapsedRealtime
        val freshness=when {
            selection.selected==null || age<0 -> VesselDataFreshness.STALE
            candidate.sourceHeartbeatElapsedRealtime>candidate.receivedElapsedRealtime -> VesselDataFreshness.HELD
            age>MetricSourceEligibility.measurementLeaseMillis(candidate.metric) -> VesselDataFreshness.STALE
            else -> VesselDataFreshness.FRESH
        }
        return VesselObservation(candidate.value,candidate.sourceClass.toLegacySource(),candidate.observedAtUtcMillis,
            candidate.receivedElapsedRealtime,candidate.quality,freshness,candidate.source.displayName,
            candidate.source,candidate.sourceClass,candidate.reference,candidate.provenance,
            selection.conflict.takeIf{it.active},candidate.sourceHeartbeatElapsedRealtime,selection.reason)
    }

    /** 姿态 GUI 的兼容聚合；输出仍逐字段校验，缺少的角速率不能伪造为零。 */
    fun attitude(heel:VesselObservation<Double>,pitch:VesselObservation<Double>,roll:VesselObservation<Double>,pitchRate:VesselObservation<Double>,yaw:VesselObservation<Double>):VesselObservation<VesselAttitude> {
        val h=heel.value?:return VesselObservation();val p=pitch.value?:return VesselObservation()
        val inputs=listOf(heel,pitch)
        val same=heel.sourceIdentity?.id==pitch.sourceIdentity?.id
        return VesselObservation(VesselAttitude(h,p,roll.value?:Double.NaN,pitchRate.value?:Double.NaN,yaw.value?:Double.NaN),
            source=if(same)heel.source else VesselDataSource.DERIVED,
            receivedElapsedRealtime=inputs.mapNotNull{it.receivedElapsedRealtime}.minOrNull(),
            quality=if(inputs.all{it.quality==VesselDataQuality.GOOD})VesselDataQuality.GOOD else VesselDataQuality.DEGRADED,
            freshness=when{inputs.any{it.freshness==VesselDataFreshness.STALE}->VesselDataFreshness.STALE;inputs.any{it.freshness!=VesselDataFreshness.FRESH}->VesselDataFreshness.HELD;else->VesselDataFreshness.FRESH},
            provenance=inputs.mapNotNull{it.provenance}.distinct().joinToString(" · "),sourceIdentity=if(same)heel.sourceIdentity else null,
            provenanceDetail=if(same)heel.provenanceDetail else VesselProvenance.Derived("selected heel and pitch",inputs.mapNotNull{it.sourceIdentity}))
    }
}

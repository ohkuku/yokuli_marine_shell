package com.yokuli.anchorwatch.domain.vessel

import com.yokuli.anchorwatch.domain.model.NavigationFix

/** 船位仍来自完整性检查；速度、COG、船首向来自各自选源结果，绝不借船位时间续命。 */
object VesselFixProjection {
    fun compose(position:NavigationFix,snapshot:VesselDataSnapshot):NavigationFix = position.copy(
        sogKnots=snapshot.sogKnots.value,
        sogReceivedElapsedRealtime=snapshot.sogKnots.receivedElapsedRealtime,
        cogTrueDegrees=snapshot.cogTrueDegrees.value,
        cogReceivedElapsedRealtime=snapshot.cogTrueDegrees.receivedElapsedRealtime,
        headingTrueDegrees=snapshot.headingTrueDegrees.value,
        headingReceivedElapsedRealtime=snapshot.headingTrueDegrees.receivedElapsedRealtime,
    )
}

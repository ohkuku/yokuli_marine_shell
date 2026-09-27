package com.yokuli.marine.shell.rebuild.data

import com.yokuli.anchorwatch.domain.vessel.VesselDataSnapshot
import com.yokuli.runtime.contract.navigation.NavigationState
import com.yokuli.anchorwatch.api.withNavigation as coreNavigationProjection

/** UI与后台历史调用相同纯投影，不维护第二套导航指标。 */
fun VesselDataSnapshot.withNavigation(state:NavigationState)=coreNavigationProjection(state)

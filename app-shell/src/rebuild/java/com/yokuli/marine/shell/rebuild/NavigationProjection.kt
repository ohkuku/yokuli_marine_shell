package com.yokuli.marine.shell.rebuild

import com.yokuli.runtime.contract.navigation.*
import java.security.MessageDigest

/** 收藏版本由内容决定；执行会话冻结此值，之后修改收藏不会静默重规划。 */
fun Route.navigationSnapshot(): NavigationRouteSnapshot {
    val bytes = MessageDigest.getInstance("SHA-256").digest(json().toString().toByteArray(Charsets.UTF_8))
    val revision = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    return NavigationRouteSnapshot(id, revision, name, points.mapIndexed { index, p ->
        NavigationWaypoint("$id:$index", targetIndices.indexOf(index).takeIf{it>=0}?.plus(1)?.toString().orEmpty(), NavigationPoint(p.lat, p.lon))
    }, navigationTargetIndices)
}
fun NavigationRouteSnapshot.asRoute() = Route(id, name, waypoints.map { GeoPoint(it.point.lat, it.point.lon) },navigationTargetIndices)

package com.yokuli.runtime.marine.planning

/** Bounded exponential lookahead followed by exact-tested refinement. Visibility is not
 * assumed monotone: every accepted shortcut is tested, and failed samples never imply
 * that an untested segment is clear. The adjacent original edge is already verified. */
internal inline fun passageShortcutIndex(anchor:Int,last:Int,clear:(Int)->Boolean):Int {
    require(anchor in 0 until last)
    if(last==anchor+1||clear(last))return last
    var best=anchor+1
    val failed=ArrayList<Int>(20)
    failed+=last
    var step=2
    while(step<last-anchor) {
        val candidate=anchor+step
        if(clear(candidate))best=candidate else failed+=candidate
        if(step>Int.MAX_VALUE/2)break
        step*=2
    }
    var upper=failed.filter{it>best}.minOrNull()?:last
    repeat(6) {
        if(upper-best<=1)return best
        val candidate=best+(upper-best)/2
        if(clear(candidate))best=candidate else upper=candidate
    }
    return best
}

package com.yokuli.runtime.marine.planning

/** Accumulates active search time across suspensions; producer work never resets spent time. */
internal class PassageSearchBudget(private val limitMillis:Long, private val nanoTime:()->Long=System::nanoTime) {
    init { require(limitMillis>0) }
    private var activeSince:Long?=null
    private var spentNanos=0L
    val elapsedMillis:Long get()=(spentNanos+(activeSince?.let { (nanoTime()-it).coerceAtLeast(0) }?:0))/1_000_000
    fun resume(){check(activeSince==null);activeSince=nanoTime()}
    fun pause(){activeSince?.let { spentNanos+=(nanoTime()-it).coerceAtLeast(0) };activeSince=null}
    fun check(){if(elapsedMillis>=limitMillis)throw PassageSearchBudgetExceeded()}
}
internal class PassageSearchBudgetExceeded:IllegalStateException("NAVIGATION_SEARCH_BUDGET: 寻路达到计算预算；输入与已准备资料保留，尚未得出无路结论 / Search limit reached; inputs retained, no-route is not established")

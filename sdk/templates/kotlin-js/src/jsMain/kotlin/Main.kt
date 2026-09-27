fun main() {
    val root = js("document.getElementById('app')")
    Yokuli.system.info().then { info ->
        val en = info.language != "zh-CN"
        root.append(Yokuli.ui.node("h1", "", if (en) "My boat" else "我的船况"))
        val content = Yokuli.ui.node("section", "yk-section")
        val errors = Yokuli.ui.status("", true)
        errors.hidden = true
        root.append(content, errors)
        Yokuli.marine.watch({ snapshot ->
            errors.hidden = true
            val speed = snapshot.readings.sog
            content.replaceChildren(Yokuli.ui.metric(if (en) "Speed over ground" else "对地速度", speed))
        }, { error ->
            errors.hidden = false
            errors.textContent = Yokuli.ui.errorMessage(error)
        })
        Unit
    }.catch { error ->
        root.append(Yokuli.ui.status(Yokuli.ui.errorMessage(error), true))
    }
}

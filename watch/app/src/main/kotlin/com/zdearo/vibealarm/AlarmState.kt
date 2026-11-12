package com.zdearo.vibealarm

/** Estado do alarme em andamento, compartilhado entre o serviço e a tela (mesmo processo, sempre na thread principal). */
object AlarmState {
    var active = false
        private set

    /** Meta atingida: a tela anima até 100% e só então fecha. */
    var done = false
        private set

    /** Segundos de caminhada já medidos e a meta. */
    var progress = 0
        private set
    var goal = 0
        private set
    var timeLabel = ""
        private set

    /** Meta de caminhada atingida, mas a trava da conexão com o celular ainda não abriu. */
    var waiting = false
        private set

    var listener: (() -> Unit)? = null

    fun set(active: Boolean, progress: Int = 0, goal: Int = 0, timeLabel: String = "", waiting: Boolean = false) {
        this.active = active
        this.waiting = waiting
        this.done = false
        this.progress = progress
        this.goal = goal
        this.timeLabel = timeLabel
        listener?.invoke()
    }

    fun complete() {
        active = false
        done = true
        progress = goal
        listener?.invoke()
    }
}

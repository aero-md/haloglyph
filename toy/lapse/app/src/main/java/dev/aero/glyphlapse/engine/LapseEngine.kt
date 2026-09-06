package dev.aero.glyphlapse.engine

import java.time.LocalDate
import java.time.ZoneId

/**
 * État du compteur : configuration (référence, format, mode secondes),
 * transitions de format (slide), détection du passage par zéro (arrivée).
 * Temps injecté (epoch millis + secondes monotones) → testable en JVM pure.
 */
class LapseEngine(private val zone: ZoneId = ZoneId.systemDefault()) {

    enum class Format {
        DETAIL2, COMPACT, CYCLE, DAYS;

        fun next(): Format = entries[(ordinal + 1) % entries.size]
    }

    enum class SecondsMode { RING, HOURGLASS }

    sealed interface Event {
        /** Compte à rebours atteint : bascule jusqu'à → depuis. */
        data object Arrived : Event

        /** Format changé (appui long ou app) — pour le retour haptique. */
        data object FormatChanged : Event
    }

    data class Snapshot(
        val diff: TimeBreakdown.Diff,
        val format: Format,
        val prevFormat: Format?,
        /** Temps écoulé depuis le changement de format, < [SLIDE] pendant la transition. */
        val slideT: Double?,
        val secondsMode: SecondsMode,
        /** Temps écoulé depuis l'arrivée, < [ARRIVAL_DUR] pendant l'animation. */
        val arrivalT: Double?,
        /** Temps monotone (s) pour les animations continues (cycle, filet du sablier). */
        val t: Double,
    ) {
        /** Le service passe à 30 fps quand c'est vrai, sinon 1 tick/s. */
        val animating: Boolean
            get() = slideT != null || arrivalT != null || format == Format.CYCLE ||
                secondsMode == SecondsMode.HOURGLASS
    }

    var refMillis: Long = defaultRef(zone)
        private set
    var format: Format = Format.DETAIL2
        private set
    var secondsMode: SecondsMode = SecondsMode.RING

    private var lastDirection: TimeBreakdown.Direction? = null
    private var arrivalStart: Double? = null
    private var slideStart: Double? = null
    private var prevFormat: Format? = null
    private val events = mutableListOf<Event>()

    fun setRef(millis: Long) {
        refMillis = millis
        lastDirection = null
        arrivalStart = null
    }

    fun setFormat(f: Format, now: Double) {
        if (f == format) return
        prevFormat = format
        format = f
        slideStart = now
        events += Event.FormatChanged
    }

    fun cycleFormat(now: Double) = setFormat(format.next(), now)

    /** Changement sans transition ni événement (chargement de la config). */
    fun setFormatQuiet(f: Format) {
        format = f
        prevFormat = null
        slideStart = null
    }

    fun update(nowMillis: Long, now: Double): Snapshot {
        val diff = TimeBreakdown.breakdown(refMillis, nowMillis, zone)

        if (lastDirection == TimeBreakdown.Direction.UNTIL &&
            diff.direction == TimeBreakdown.Direction.SINCE &&
            arrivalStart == null
        ) {
            arrivalStart = now
            events += Event.Arrived
        }
        lastDirection = diff.direction

        arrivalStart = arrivalStart?.takeIf { now - it < ARRIVAL_DUR }
        slideStart = slideStart?.takeIf { now - it < SLIDE }
        if (slideStart == null) prevFormat = null

        return Snapshot(
            diff = diff,
            format = format,
            prevFormat = prevFormat,
            slideT = slideStart?.let { now - it },
            secondsMode = secondsMode,
            arrivalT = arrivalStart?.let { now - it },
            t = now,
        )
    }

    fun drainEvents(): List<Event> {
        val out = events.toList()
        events.clear()
        return out
    }

    companion object {
        /** Durée du slide de changement de format (s). */
        const val SLIDE = 0.3

        /** Durée de l'animation d'arrivée (s). */
        const val ARRIVAL_DUR = 4.0

        /** Défaut : 1ᵉʳ janvier de l'année courante, 00:00 locale (mode depuis). */
        fun defaultRef(zone: ZoneId): Long =
            LocalDate.now(zone).withDayOfYear(1).atStartOfDay(zone).toInstant().toEpochMilli()

        /** 31 décembre de l'année courante, 23:59 locale (mode jusqu'à). */
        fun endOfYearRef(zone: ZoneId): Long =
            LocalDate.now(zone).withMonth(12).withDayOfMonth(31)
                .atTime(23, 59).atZone(zone).toInstant().toEpochMilli()
    }
}

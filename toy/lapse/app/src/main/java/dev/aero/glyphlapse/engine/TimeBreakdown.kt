package dev.aero.glyphlapse.engine

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Décomposition calendaire entre une date de référence et maintenant.
 * Années/mois exacts via java.time (fins de mois clampées, bissextiles),
 * puis jours/heures/minutes/secondes en temps réel écoulé.
 * Direction déduite : référence passée → SINCE, future → UNTIL.
 */
object TimeBreakdown {

    enum class Direction { SINCE, UNTIL }

    data class Diff(
        val direction: Direction,
        val years: Int,
        val months: Int,
        val days: Int,
        val hours: Int,
        val minutes: Int,
        val seconds: Int,
        val totalDays: Long,
    )

    fun breakdown(refMillis: Long, nowMillis: Long, zone: ZoneId): Diff {
        val direction =
            if (nowMillis >= refMillis) Direction.SINCE else Direction.UNTIL
        val a0 = Instant.ofEpochMilli(minOf(refMillis, nowMillis)).atZone(zone)
        val b = Instant.ofEpochMilli(maxOf(refMillis, nowMillis)).atZone(zone)

        var a = a0
        val years = ChronoUnit.YEARS.between(a, b)
        a = a.plusYears(years)
        val months = ChronoUnit.MONTHS.between(a, b)
        a = a.plusMonths(months)

        var s = ChronoUnit.SECONDS.between(a, b)
        val days = s / 86400; s -= days * 86400
        val hours = s / 3600; s -= hours * 3600
        val minutes = s / 60
        val seconds = s - minutes * 60

        return Diff(
            direction = direction,
            years = years.toInt(),
            months = months.toInt(),
            days = days.toInt(),
            hours = hours.toInt(),
            minutes = minutes.toInt(),
            seconds = seconds.toInt(),
            totalDays = (maxOf(refMillis, nowMillis) - minOf(refMillis, nowMillis)) / 86_400_000L,
        )
    }
}

package com.example.medilens

object ReminderDecisionEngine {

    enum class ReminderType { SOFT, HARD }

    private const val HARD_SCORE_THRESHOLD = 50
    private const val HIGH_MISS_RATE       = 0.4f

    fun decide(score: AdherenceScoreEntity?): ReminderType {
        if (score == null) return ReminderType.SOFT
        val missRate = score.missCountLast7.toFloat() / 7f
        return if (score.score < HARD_SCORE_THRESHOLD || missRate >= HIGH_MISS_RATE) {
            ReminderType.HARD
        } else {
            ReminderType.SOFT
        }
    }

    fun computeAfterTaken(
        existing: AdherenceScoreEntity?,
        prescriptionId: Long,
        lateMins: Int
    ): AdherenceScoreEntity {
        val current = existing ?: AdherenceScoreEntity(prescriptionId = prescriptionId)
        val onTime  = lateMins <= 30

        val newScore  = (current.score + if (onTime) 5 else 0).coerceAtMost(100)
        val newStreak = if (onTime) current.streak + 1 else 0

        val newDrift = when {
            lateMins in 15..120 -> (current.driftMinutes + 5).coerceAtMost(60)
            lateMins <= 5 && current.driftMinutes > 0 -> (current.driftMinutes - 5).coerceAtLeast(0)
            else -> current.driftMinutes
        }

        return current.copy(
            score             = newScore,
            streak            = newStreak,
            consecutiveMisses = 0,
            driftMinutes      = newDrift,
            lastUpdated       = System.currentTimeMillis()
        )
    }

    fun computeAfterMissed(
        existing: AdherenceScoreEntity?,
        prescriptionId: Long
    ): AdherenceScoreEntity {
        val current = existing ?: AdherenceScoreEntity(prescriptionId = prescriptionId)
        return current.copy(
            score             = (current.score - 15).coerceAtLeast(0),
            streak            = 0,
            missCountLast7    = (current.missCountLast7 + 1).coerceAtMost(7),
            consecutiveMisses = current.consecutiveMisses + 1,
            lastUpdated       = System.currentTimeMillis()
        )
    }
}
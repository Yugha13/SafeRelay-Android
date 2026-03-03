package com.saferelay.android.model

/**
 * Rule-based emergency intelligence engine.
 * Mirrors the iOS SafeRelay approach: keyword matching → auto-escalate priority.
 * No ML needed — fast, offline, deterministic.
 */
object EmergencyIntelligenceEngine {

    // Keywords that signal a CRITICAL life-threatening situation
    private val criticalKeywords = listOf(
        "sos", "help", "emergency", "dying", "dead", "fire", "trapped",
        "bleeding", "unconscious", "drowning", "earthquake", "flood", "collapsed",
        "explosion", "attack", "shooting", "bomb", "救命", "救助", "مدد", "मदद"
    )

    // Keywords that signal an URGENT (non-immediately-life-threatening) situation
    private val urgentKeywords = listOf(
        "injured", "hurt", "pain", "missing", "lost", "stuck", "need",
        "food", "water", "shelter", "medicine", "hospital", "ambulance",
        "stranded", "danger", "gas leak", "power outage", "evacuate"
    )

    /**
     * Classify a message text into a (EmergencyMessageType, PriorityLevel) pair.
     * Called before sending to auto-escalate priority on dangerous content.
     */
    fun classify(content: String): Pair<EmergencyMessageType, PriorityLevel> {
        val normalized = content.lowercase().trim()

        // Explicit SOS command is always critical
        if (normalized.startsWith("/sos") || normalized == "sos") {
            return Pair(EmergencyMessageType.SOS, PriorityLevel.CRITICAL)
        }

        // Check for critical keywords
        val isCritical = criticalKeywords.any { kw -> normalized.contains(kw) }
        if (isCritical) {
            // Determine the most specific emergency type
            val type = when {
                normalized.contains("fire") || normalized.contains("explosion") -> EmergencyMessageType.SOS
                normalized.contains("bleeding") || normalized.contains("unconscious") ||
                normalized.contains("dying") -> EmergencyMessageType.MEDICAL_REQUEST
                else -> EmergencyMessageType.SOS
            }
            return Pair(type, PriorityLevel.CRITICAL)
        }

        // Check for urgent keywords
        val isUrgent = urgentKeywords.any { kw -> normalized.contains(kw) }
        if (isUrgent) {
            val type = when {
                normalized.contains("food") || normalized.contains("water") ||
                normalized.contains("shelter") -> EmergencyMessageType.RESOURCE_UPDATE
                normalized.contains("hospital") || normalized.contains("ambulance") ||
                normalized.contains("medicine") -> EmergencyMessageType.MEDICAL_REQUEST
                else -> EmergencyMessageType.BROADCAST
            }
            return Pair(type, PriorityLevel.URGENT)
        }

        return Pair(EmergencyMessageType.NORMAL, PriorityLevel.INFO)
    }

    /**
     * Sort messages by priority: CRITICAL first, then URGENT, then INFO.
     * Within the same priority: most recent first.
     */
    fun List<SafeRelayMessage>.sortedByEmergencyPriority(): List<SafeRelayMessage> =
        sortedWith(
            compareBy<SafeRelayMessage> { msg ->
                when (msg.priorityLevel) {
                    PriorityLevel.CRITICAL -> 0
                    PriorityLevel.URGENT -> 1
                    PriorityLevel.INFO -> 2
                }
            }.thenByDescending { it.timestamp }
        )
}

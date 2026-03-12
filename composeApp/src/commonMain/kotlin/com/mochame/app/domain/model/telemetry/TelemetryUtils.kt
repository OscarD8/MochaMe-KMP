package com.mochame.app.domain.model.telemetry

import com.mochame.app.domain.model.Resonance
import kotlin.math.abs

/**
 * The "Strain Lever" for Gemini Nano analysis.
 * Formula: ((ArousalFactor * Intensity * Duration) * FocusMultiplier) / (Resilience * 20.0)
 * * Resilience is composed of Pleasure, Dominance, and Biophilia.
 */
fun Moment.calculateStrain(): Double {
    val currentMood = this.core.mood
    val isFocus = this.detail.isFocusTime ?: false

    // 1. ADAPTIVE AROUSAL (The Weighted V-Curve)
    // Arousal range is -2..2.
    val arousalFactor = if (isFocus) {
        // High Tax: Focus extremes are costly (Range 2.0 to 4.0)
        abs(currentMood.arousal.toDouble()) + 2.0
    } else {
        // Low Tax: General existence (Range 1.5 to 2.5)
        (abs(currentMood.arousal.toDouble()) * 0.5) + 1.5
    }

    // 2. DIMENSIONAL DATA
    val intensity = this.core.intensityScale.toDouble()
    val duration = this.detail.durationMinutes?.toDouble() ?: 30.0

    // 3. RESILIENCE BUFFER (Pleasure + Dominance + Biophilia)
    // Shifts -2..2 scale to 1.0..5.0. Biophilia is already 1-5.
    val pleasureWeight = currentMood.pleasure + 3.0
    val dominanceWeight = currentMood.dominance + 3.0
    val biophiliaWeight = this.detail.biophiliaScale?.toDouble() ?: 1.0 // Default to minimum buffer

    val resilience = pleasureWeight + dominanceWeight + biophiliaWeight

    // 4. THE CALCULATION
    val focusMultiplier = if (isFocus) 1.5 else 1.0
    val rawLoad = (arousalFactor * intensity * duration) * focusMultiplier

    // Normalize to 1-100 range for UI and AI processing
    // Minimum resilience is 3.0 (-2,-2,1), max is 15.0 (2,2,5).
    return rawLoad / (resilience * 20.0)
}

// REFACTOR: Consider flexibility, catharsis etc.
val Mood.targetResonance: Resonance
    get() = when (this) {
        Mood.FOCUS, Mood.ENERGIZED -> Resonance.LOGIC   // Maintain clarity
        Mood.WONDER, Mood.CALM -> Resonance.WONDER     // Deepen the spark
        Mood.BORED -> Resonance.WONDER                 // Break the flatline
        Mood.SAD, Mood.TIRED -> Resonance.JOY          // Emotional lift
        Mood.FRUSTRATED -> Resonance.LOGIC             // Stoic grounding
        Mood.NEUTRAL -> Resonance.entries.random()     // Variety
    }
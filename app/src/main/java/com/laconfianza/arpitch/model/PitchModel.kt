package com.laconfianza.arpitch.model

import kotlin.math.abs

object PitchUnits {
    const val METERS_PER_YARD = 0.9144f
    fun yardsToMeters(yards: Float): Float = yards * METERS_PER_YARD
    fun metersToYards(meters: Float): Float = meters / METERS_PER_YARD
}

data class PitchSpec(
    val distanceMeters: Float = PitchUnits.yardsToMeters(22f),
    val pitchWidthMeters: Float = 3.05f,
    val wicketHeightMeters: Float = 0.7112f,
    val wicketSpanMeters: Float = 0.2286f,
    val stumpDiameterMeters: Float = 0.036f,
    val bowlingCreaseMeters: Float = 2.64f,
    val poppingCreaseOffsetMeters: Float = 1.22f,
    val returnCreaseHalfSpanMeters: Float = 1.32f,
) {
    init {
        require(distanceMeters in 1f..60f) { "Distance must be between 1 m and 60 m" }
    }

    val yards: Float get() = PitchUnits.metersToYards(distanceMeters)

    fun isNearYards(value: Float, tolerance: Float = 0.03f): Boolean = abs(yards - value) <= tolerance
}

enum class PlacementPhase { SCANNING, AIMING, LOCKED }
enum class SurfaceQuality { SEARCHING, FAIR, GOOD, EXCELLENT }

data class ScreenPoint(val x: Float, val y: Float, val visible: Boolean = true)

data class UiSnapshot(
    val phase: PlacementPhase = PlacementPhase.SCANNING,
    val quality: SurfaceQuality = SurfaceQuality.SEARCHING,
    val distanceMeters: Float = PitchUnits.yardsToMeters(22f),
    val depthSupported: Boolean = false,
    val trackedPlanes: Int = 0,
    val fps: Int = 0,
    val trackingText: String = "Starting AR…",
    val guidance: String = "Move the phone slowly to scan the ground",
    val cameraToBowlingEndMeters: Float? = null,
    val battingLabel: ScreenPoint? = null,
    val bowlingLabel: ScreenPoint? = null,
    val distanceLabel: ScreenPoint? = null,
)

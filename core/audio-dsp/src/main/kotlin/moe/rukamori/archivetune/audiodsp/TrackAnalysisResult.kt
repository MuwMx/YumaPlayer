package moe.rukamori.archivetune.audiodsp

data class TrackAnalysisResult(
    val bpm: Double = 0.0,
    val mixInTime: Double = 0.0,
    val mixOutTime: Double = 0.0,
    val contentEndTime: Double = 0.0,
    val firstBeat: Double = 0.0,
    val rawJson: String = ""
)

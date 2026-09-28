package moe.rukamori.archivetune.audiodsp

import kotlinx.coroutines.flow.Flow

interface AnalysisStore {
    fun getCached(trackId: String): TrackAnalysisResult?
    val analysisEvents: Flow<Pair<String, TrackAnalysisResult>>
}

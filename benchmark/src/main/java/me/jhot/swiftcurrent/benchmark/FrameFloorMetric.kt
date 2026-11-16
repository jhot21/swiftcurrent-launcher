package me.jhot.swiftcurrent.benchmark

import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.TraceMetric
import androidx.benchmark.traceprocessor.TraceProcessor

/**
 * Fails the benchmark when the target drew fewer than [minFrames] frames during a run: a swipe that
 * missed its target would otherwise still produce a (tiny, meaningless) FrameTimingMetric result.
 */
@OptIn(ExperimentalMetricApi::class)
class FrameFloorMetric(private val minFrames: Int) : TraceMetric() {
    override fun getMeasurements(
        captureInfo: CaptureInfo,
        traceSession: TraceProcessor.Session,
    ): List<Measurement> {
        val frames = traceSession.query(
            """
            SELECT count(*) AS c FROM slice
            JOIN thread_track ON slice.track_id = thread_track.id
            JOIN thread ON thread_track.utid = thread.utid
            JOIN process ON thread.upid = process.upid
            WHERE process.name = '${captureInfo.targetPackageName}' AND slice.name LIKE 'Choreographer#doFrame%'
            """.trimIndent(),
        ).first().long("c")
        check(frames >= minFrames) { "only $frames frames were drawn, expected at least $minFrames" }
        return listOf(Measurement("uiFrames", frames.toDouble()))
    }
}

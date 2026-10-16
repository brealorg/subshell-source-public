package app.subshell.prototype.media

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Last meaningful vertical feed movement reported by the owning LazyListState. */
enum class FeedScrollDirection {
    UP,
    DOWN,
    NONE,
}

/** Distinguishes direct finger drag from inertial/programmatic scrolling. */
enum class FeedViewportMotion {
    IDLE,
    DRAGGING,
    FLINGING,
}

/**
 * Authoritative feed viewport state.
 *
 * `atStart` and `atEnd` come from LazyListState.canScrollBackward/Forward rather than
 * inferred media geometry. This makes first/last-video ownership deterministic.
 */
data class FeedViewportState(
    val motion: FeedViewportMotion = FeedViewportMotion.IDLE,
    val direction: FeedScrollDirection = FeedScrollDirection.NONE,
    val atStart: Boolean = false,
    val atEnd: Boolean = false,
)

/** Geometry for one actual video surface inside the usable window viewport. */
data class VideoSurfaceMetrics(
    val visibleFraction: Float,
    val visibleViewportFraction: Float,
    val centerDistanceFraction: Float,
    val isTerminalItem: Boolean = false,
    val centerOffsetFraction: Float = Float.MAX_VALUE,
    val surfaceTopFraction: Float = Float.MAX_VALUE,
    val surfaceBottomFraction: Float = Float.MAX_VALUE,
    val surfaceHeightFraction: Float = 0f,
) {
    fun normalized(): VideoSurfaceMetrics = copy(
        visibleFraction = visibleFraction.coerceIn(0f, 1f),
        visibleViewportFraction = visibleViewportFraction.coerceIn(0f, 1f),
        centerDistanceFraction = centerDistanceFraction.coerceAtLeast(0f),
        centerOffsetFraction = centerOffsetFraction.takeIf(Float::isFinite) ?: Float.MAX_VALUE,
        surfaceTopFraction = surfaceTopFraction.takeIf(Float::isFinite) ?: Float.MAX_VALUE,
        surfaceBottomFraction = surfaceBottomFraction.takeIf(Float::isFinite) ?: Float.MAX_VALUE,
        surfaceHeightFraction = surfaceHeightFraction.coerceAtLeast(0f),
    )
}

/**
 * MEDIA-PERF-02 deterministic mixed-feed owner selection.
 *
 * The playback owner is the qualified video surface nearest a viewport anchor. During a
 * direct drag the anchor is shifted slightly in the travel direction, which lets short media
 * own playback while it passes through the viewport. During a fling no new owner is started.
 * At the physical start/end of a finite feed the first/last qualified video wins explicitly.
 */
object VideoVisibilityPolicy {
    const val MANUAL_KEEP_VISIBLE_FRACTION = 0.01f
    const val OWNER_RETAIN_VISIBLE_FRACTION = 0.01f
    const val OWNER_RETAIN_VISIBLE_VIEWPORT_FRACTION = 0.005f
    const val OWNER_MIN_VISIBLE_FRACTION = 0.15f
    const val OWNER_MIN_VISIBLE_VIEWPORT_FRACTION = 0.04f
    const val OWNER_SWITCH_HYSTERESIS_FRACTION = 0.05f
    const val DIRECTIONAL_ANCHOR_OFFSET_FRACTION = 0.05f
    const val DIRECTIONAL_ANCHOR_RAMP_DISTANCE_FRACTION = 0.12f
    const val PRELOAD_MIN_VISIBLE_FRACTION = 0.03f
    const val PRELOAD_MIN_VISIBLE_VIEWPORT_FRACTION = 0.015f

    data class Decision(
        val activeVideoId: String?,
        val manualVideoId: String?,
        val transitionReason: String? = null,
    )

    /** Selects the final video only when the backing feed is known to be exhausted. */
    fun selectTerminalVideoId(
        orderedVideoIds: List<String>,
        feedExhausted: Boolean,
    ): String? = if (feedExhausted) orderedVideoIds.lastOrNull() else null

    /**
     * Compatibility entry point for tests/callers that only have visible fractions.
     * Fraction-only input has no signed centre geometry, so it uses an idle centre anchor.
     */
    fun decide(
        activeVideoId: String?,
        manualVideoId: String?,
        visibleFractions: Map<String, Float>,
        autoplayEnabled: Boolean,
        viewportSettled: Boolean,
    ): Decision = selectViewportOwner(
        activeVideoId = activeVideoId,
        manualVideoId = manualVideoId,
        metricsByMediaId = visibleFractions.mapValues { (_, fraction) ->
            VideoSurfaceMetrics(
                visibleFraction = fraction,
                visibleViewportFraction = fraction,
                centerDistanceFraction = 1f - fraction.coerceIn(0f, 1f),
                centerOffsetFraction = Float.MAX_VALUE,
            )
        },
        autoplayEnabled = autoplayEnabled,
        viewport = FeedViewportState(
            motion = if (viewportSettled) FeedViewportMotion.IDLE else FeedViewportMotion.FLINGING,
        ),
    )

    fun selectViewportOwner(
        activeVideoId: String?,
        manualVideoId: String?,
        metricsByMediaId: Map<String, VideoSurfaceMetrics>,
        autoplayEnabled: Boolean,
        viewport: FeedViewportState,
        orderedVideoIds: List<String> = emptyList(),
        distanceSinceDirectionChangeFraction: Float = Float.POSITIVE_INFINITY,
    ): Decision {
        val normalized = metricsByMediaId.mapValues { (_, metrics) -> metrics.normalized() }

        if (manualVideoId != null) {
            val manualMetrics = normalized[manualVideoId]
            if (manualMetrics != null && retainsOwner(manualMetrics)) {
                return Decision(activeVideoId = manualVideoId, manualVideoId = manualVideoId)
            }
        }

        if (!autoplayEnabled) return Decision(activeVideoId = null, manualVideoId = null)

        val activeMetrics = activeVideoId?.let(normalized::get)
        val activeRetained = activeMetrics?.let(::retainsOwner) == true

        // A fling may keep a still-visible owner, but never starts or replaces playback.
        if (viewport.motion == FeedViewportMotion.FLINGING) {
            return Decision(
                activeVideoId = activeVideoId.takeIf { activeRetained },
                manualVideoId = null,
            )
        }

        edgeOwner(
            metricsByMediaId = normalized,
            orderedVideoIds = orderedVideoIds,
            viewport = viewport,
        )?.let { edgeOwnerId ->
            return Decision(
                activeVideoId = edgeOwnerId,
                manualVideoId = null,
                transitionReason = when {
                    edgeOwnerId == activeVideoId -> null
                    viewport.atEnd -> "viewport_end_owner"
                    else -> "viewport_start_owner"
                },
            )
        }

        val candidate = bestAnchorCandidate(
            metricsByMediaId = normalized,
            orderedVideoIds = orderedVideoIds,
            viewport = viewport,
            activeVideoId = activeVideoId,
            distanceSinceDirectionChangeFraction = distanceSinceDirectionChangeFraction,
        )

        if (activeVideoId == null) {
            return Decision(
                activeVideoId = candidate,
                manualVideoId = null,
                transitionReason = candidate?.let {
                    if (viewport.motion == FeedViewportMotion.DRAGGING) {
                        "viewport_drag_acquire"
                    } else {
                        "viewport_idle_acquire"
                    }
                },
            )
        }

        if (candidate == null) {
            return Decision(
                activeVideoId = activeVideoId.takeIf { activeRetained },
                manualVideoId = null,
            )
        }
        if (candidate == activeVideoId) {
            return Decision(activeVideoId = activeVideoId, manualVideoId = null)
        }
        if (!activeRetained) {
            return Decision(
                activeVideoId = candidate,
                manualVideoId = null,
                transitionReason = "viewport_owner_lost",
            )
        }

        if (!directionAllowsReplacement(activeVideoId, candidate, orderedVideoIds, viewport.direction)) {
            return Decision(activeVideoId = activeVideoId, manualVideoId = null)
        }

        val activeScore = ownerScore(
            metrics = activeMetrics,
            viewport = viewport,
            distanceSinceDirectionChangeFraction = distanceSinceDirectionChangeFraction,
        )
        val candidateScore = ownerScore(
            metrics = normalized.getValue(candidate),
            viewport = viewport,
            distanceSinceDirectionChangeFraction = distanceSinceDirectionChangeFraction,
        )
        return if (candidateScore + OWNER_SWITCH_HYSTERESIS_FRACTION < activeScore) {
            Decision(
                activeVideoId = candidate,
                manualVideoId = null,
                transitionReason = if (viewport.motion == FeedViewportMotion.DRAGGING) {
                    "viewport_drag_takeover"
                } else {
                    "viewport_idle_takeover"
                },
            )
        } else {
            Decision(activeVideoId = activeVideoId, manualVideoId = null)
        }
    }

    /**
     * Selects the next likely owner continuously, including while dragging or flinging.
     * It never starts playback or requests audio focus.
     */
    fun selectPreloadCandidate(
        activeVideoId: String?,
        metricsByMediaId: Map<String, VideoSurfaceMetrics>,
        orderedVideoIds: List<String> = emptyList(),
        viewport: FeedViewportState = FeedViewportState(),
    ): String? {
        val normalized = metricsByMediaId.mapValues { (_, metrics) -> metrics.normalized() }

        edgeOwner(
            metricsByMediaId = normalized.filterKeys { it != activeVideoId },
            orderedVideoIds = orderedVideoIds,
            viewport = viewport,
            preload = true,
        )?.let { return it }

        val activeIndex = activeVideoId?.let(orderedVideoIds::indexOf)?.takeIf { it >= 0 }
        if (activeIndex != null && viewport.direction != FeedScrollDirection.NONE) {
            val directionalIds = when (viewport.direction) {
                FeedScrollDirection.DOWN -> orderedVideoIds.drop(activeIndex + 1)
                FeedScrollDirection.UP -> orderedVideoIds.take(activeIndex).asReversed()
                FeedScrollDirection.NONE -> emptyList()
            }
            directionalIds.firstOrNull { mediaId ->
                mediaId != activeVideoId && normalized[mediaId]?.let(::qualifiesForPreload) == true
            }?.let { return it }
        }

        return normalized.entries
            .asSequence()
            .filter { (mediaId, metrics) ->
                mediaId != activeVideoId && qualifiesForPreload(metrics)
            }
            .sortedWith(
                anchorComparator(
                    orderedVideoIds,
                    viewport,
                    activeVideoId,
                    Float.POSITIVE_INFINITY,
                ),
            )
            .firstOrNull()
            ?.key
    }

    fun preferSurface(
        first: VideoSurfaceMetrics,
        second: VideoSurfaceMetrics,
    ): VideoSurfaceMetrics = listOf(first.normalized(), second.normalized())
        .sortedWith(
            compareBy<VideoSurfaceMetrics> { it.centerDistanceFraction }
                .thenByDescending { it.visibleViewportFraction }
                .thenByDescending { it.visibleFraction },
        )
        .first()

    private fun edgeOwner(
        metricsByMediaId: Map<String, VideoSurfaceMetrics>,
        orderedVideoIds: List<String>,
        viewport: FeedViewportState,
        preload: Boolean = false,
    ): String? {
        val qualifies: (VideoSurfaceMetrics) -> Boolean = if (preload) {
            ::qualifiesForPreload
        } else {
            ::qualifiesForOwner
        }
        return when {
            viewport.atEnd -> orderedVideoIds.asReversed().firstOrNull { mediaId ->
                metricsByMediaId[mediaId]?.let { metrics ->
                    metrics.isTerminalItem && qualifies(metrics)
                } == true
            }
            viewport.atStart -> orderedVideoIds.firstOrNull { mediaId ->
                metricsByMediaId[mediaId]?.let(qualifies) == true
            }
            else -> null
        }
    }

    private fun bestAnchorCandidate(
        metricsByMediaId: Map<String, VideoSurfaceMetrics>,
        orderedVideoIds: List<String>,
        viewport: FeedViewportState,
        activeVideoId: String?,
        distanceSinceDirectionChangeFraction: Float,
    ): String? = metricsByMediaId.entries
        .asSequence()
        .filter { (_, metrics) -> qualifiesForOwner(metrics) }
        .sortedWith(
            anchorComparator(
                orderedVideoIds,
                viewport,
                activeVideoId,
                distanceSinceDirectionChangeFraction,
            ),
        )
        .firstOrNull()
        ?.key

    private fun anchorComparator(
        orderedVideoIds: List<String>,
        viewport: FeedViewportState,
        activeVideoId: String?,
        distanceSinceDirectionChangeFraction: Float,
    ): Comparator<Map.Entry<String, VideoSurfaceMetrics>> {
        val activeIndex = activeVideoId?.let(orderedVideoIds::indexOf)?.takeIf { it >= 0 }
        fun rank(mediaId: String): Int = orderedVideoIds.indexOf(mediaId).takeIf { it >= 0 } ?: Int.MAX_VALUE
        fun rankDistance(mediaId: String): Int = activeIndex?.let { abs(rank(mediaId) - it) } ?: 0
        return compareBy<Map.Entry<String, VideoSurfaceMetrics>> {
            ownerScore(it.value, viewport, distanceSinceDirectionChangeFraction)
        }.thenBy {
            rankDistance(it.key)
        }.thenByDescending {
            it.value.visibleViewportFraction
        }.thenByDescending {
            it.value.visibleFraction
        }.thenBy {
            rank(it.key)
        }.thenBy {
            it.key
        }
    }

    private fun directionAllowsReplacement(
        activeVideoId: String,
        candidateVideoId: String,
        orderedVideoIds: List<String>,
        direction: FeedScrollDirection,
    ): Boolean {
        if (direction == FeedScrollDirection.NONE) return true
        val activeIndex = orderedVideoIds.indexOf(activeVideoId)
        val candidateIndex = orderedVideoIds.indexOf(candidateVideoId)
        if (activeIndex < 0 || candidateIndex < 0) return true
        return when (direction) {
            FeedScrollDirection.DOWN -> candidateIndex > activeIndex
            FeedScrollDirection.UP -> candidateIndex < activeIndex
            FeedScrollDirection.NONE -> true
        }
    }

    private fun ownerScore(
        metrics: VideoSurfaceMetrics,
        viewport: FeedViewportState,
        distanceSinceDirectionChangeFraction: Float,
    ): Float {
        val targetOffset = directionalAnchorOffset(
            direction = viewport.direction,
            distanceSinceDirectionChangeFraction = distanceSinceDirectionChangeFraction,
        )
        return if (metrics.centerOffsetFraction != Float.MAX_VALUE) {
            abs(metrics.centerOffsetFraction - targetOffset)
        } else {
            metrics.centerDistanceFraction
        }
    }

    private fun directionalAnchorOffset(
        direction: FeedScrollDirection,
        distanceSinceDirectionChangeFraction: Float,
    ): Float {
        val directionSign = when (direction) {
            FeedScrollDirection.DOWN -> 1f
            FeedScrollDirection.UP -> -1f
            FeedScrollDirection.NONE -> 0f
        }
        if (directionSign == 0f) return 0f
        val rampProgress = (
            distanceSinceDirectionChangeFraction / DIRECTIONAL_ANCHOR_RAMP_DISTANCE_FRACTION
        ).coerceIn(0f, 1f)
        return directionSign * DIRECTIONAL_ANCHOR_OFFSET_FRACTION * rampProgress
    }

    private fun retainsOwner(metrics: VideoSurfaceMetrics): Boolean {
        val normalized = metrics.normalized()
        return normalized.visibleFraction >= OWNER_RETAIN_VISIBLE_FRACTION ||
            normalized.visibleViewportFraction >= OWNER_RETAIN_VISIBLE_VIEWPORT_FRACTION
    }

    private fun qualifiesForOwner(metrics: VideoSurfaceMetrics): Boolean {
        val normalized = metrics.normalized()
        return normalized.visibleFraction >= OWNER_MIN_VISIBLE_FRACTION ||
            normalized.visibleViewportFraction >= OWNER_MIN_VISIBLE_VIEWPORT_FRACTION
    }

    private fun qualifiesForPreload(metrics: VideoSurfaceMetrics): Boolean {
        val normalized = metrics.normalized()
        return normalized.visibleFraction >= PRELOAD_MIN_VISIBLE_FRACTION ||
            normalized.visibleViewportFraction >= PRELOAD_MIN_VISIBLE_VIEWPORT_FRACTION
    }

    fun measureSurface(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        viewportWidth: Int,
        viewportHeight: Int,
        isTerminalItem: Boolean,
    ): VideoSurfaceMetrics {
        val width = (right - left).coerceAtLeast(0f)
        val height = (bottom - top).coerceAtLeast(0f)
        val totalArea = width * height
        if (totalArea <= 0f || viewportWidth <= 0 || viewportHeight <= 0) {
            return VideoSurfaceMetrics(
                visibleFraction = 0f,
                visibleViewportFraction = 0f,
                centerDistanceFraction = Float.MAX_VALUE,
                isTerminalItem = isTerminalItem,
                centerOffsetFraction = Float.MAX_VALUE,
            )
        }

        val visibleLeft = max(left, 0f)
        val visibleTop = max(top, 0f)
        val visibleRight = min(right, viewportWidth.toFloat())
        val visibleBottom = min(bottom, viewportHeight.toFloat())
        val visibleWidth = (visibleRight - visibleLeft).coerceAtLeast(0f)
        val visibleHeight = (visibleBottom - visibleTop).coerceAtLeast(0f)
        val surfaceCenterY = (top + bottom) / 2f
        val viewportCenterY = viewportHeight / 2f
        val centerOffsetFraction = (surfaceCenterY - viewportCenterY) / viewportHeight

        return VideoSurfaceMetrics(
            visibleFraction = ((visibleWidth * visibleHeight) / totalArea).coerceIn(0f, 1f),
            visibleViewportFraction = (visibleHeight / viewportHeight).coerceIn(0f, 1f),
            centerDistanceFraction = abs(centerOffsetFraction),
            isTerminalItem = isTerminalItem,
            centerOffsetFraction = centerOffsetFraction,
            surfaceTopFraction = top / viewportHeight,
            surfaceBottomFraction = bottom / viewportHeight,
            surfaceHeightFraction = height / viewportHeight,
        )
    }

    fun visibleAreaFraction(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        viewportWidth: Int,
        viewportHeight: Int,
    ): Float = measureSurface(
        left = left,
        top = top,
        right = right,
        bottom = bottom,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        isTerminalItem = false,
    ).visibleFraction
}

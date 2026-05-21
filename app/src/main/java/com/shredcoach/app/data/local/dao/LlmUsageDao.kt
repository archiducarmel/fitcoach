package com.shredcoach.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.shredcoach.app.data.local.entity.LlmUsageEventEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

/**
 * Projections agrégées exposées par les queries du DAO. Tous les @ColumnInfo
 * names correspondent aux aliases SQL.
 */
data class UsageTotals(
    val totalCalls: Int,
    val totalTokens: Int,
    val totalCostUsd: Double,
    val avgLatencyMs: Double,
    val successRate: Double,
)

data class UsageByAssistant(
    val assistantKey: String,
    val calls: Int,
    val tokens: Int,
    val costUsd: Double,
)

data class UsageByModel(
    val provider: String,
    val model: String,
    val calls: Int,
    val tokens: Int,
    val costUsd: Double,
)

data class UsageByProvider(
    val provider: String,
    val calls: Int,
    val tokens: Int,
    val costUsd: Double,
)

data class UsageHourBucket(
    val hour: Int,         // 0..23
    val dayOfWeek: Int,    // 0=Monday..6=Sunday (SQLite strftime '%w' shifted)
    val calls: Int,
    val tokens: Int,
)

data class UsageDayBucket(
    val day: String,       // ISO yyyy-MM-dd
    val calls: Int,
    val tokens: Int,
    val costUsd: Double,
)

@Dao
interface LlmUsageDao {

    @Insert
    suspend fun insert(event: LlmUsageEventEntity): Long

    @Query("DELETE FROM llm_usage_events")
    suspend fun deleteAll()

    @Query("DELETE FROM llm_usage_events WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: LocalDateTime): Int

    @Query("SELECT COUNT(*) FROM llm_usage_events")
    suspend fun count(): Int

    @Query("SELECT MIN(timestamp) FROM llm_usage_events")
    suspend fun earliestTimestamp(): LocalDateTime?

    /** Totaux sur une fenêtre temporelle. NULL si pas de data. */
    @Query("""
        SELECT
            COUNT(*) as totalCalls,
            COALESCE(SUM(tokensInput + tokensOutput + tokensThinking), 0) as totalTokens,
            COALESCE(SUM(costUsd), 0.0) as totalCostUsd,
            COALESCE(AVG(latencyMs), 0.0) as avgLatencyMs,
            COALESCE(AVG(CASE WHEN success THEN 1.0 ELSE 0.0 END), 0.0) as successRate
        FROM llm_usage_events
        WHERE timestamp >= :since
    """)
    suspend fun getTotalsSince(since: LocalDateTime): UsageTotals

    /** Group by assistant. Ordonné par tokens DESC. */
    @Query("""
        SELECT
            assistantKey,
            COUNT(*) as calls,
            COALESCE(SUM(tokensInput + tokensOutput + tokensThinking), 0) as tokens,
            COALESCE(SUM(costUsd), 0.0) as costUsd
        FROM llm_usage_events
        WHERE timestamp >= :since
        GROUP BY assistantKey
        ORDER BY tokens DESC
    """)
    suspend fun getByAssistantSince(since: LocalDateTime): List<UsageByAssistant>

    /** Group by (provider, model). Ordonné par tokens DESC. */
    @Query("""
        SELECT
            provider,
            model,
            COUNT(*) as calls,
            COALESCE(SUM(tokensInput + tokensOutput + tokensThinking), 0) as tokens,
            COALESCE(SUM(costUsd), 0.0) as costUsd
        FROM llm_usage_events
        WHERE timestamp >= :since
        GROUP BY provider, model
        ORDER BY tokens DESC
    """)
    suspend fun getByModelSince(since: LocalDateTime): List<UsageByModel>

    /** Group by provider seulement. */
    @Query("""
        SELECT
            provider,
            COUNT(*) as calls,
            COALESCE(SUM(tokensInput + tokensOutput + tokensThinking), 0) as tokens,
            COALESCE(SUM(costUsd), 0.0) as costUsd
        FROM llm_usage_events
        WHERE timestamp >= :since
        GROUP BY provider
        ORDER BY tokens DESC
    """)
    suspend fun getByProviderSince(since: LocalDateTime): List<UsageByProvider>

    /**
     * Buckets 24h × 7j (168 cellules) pour le heatmap "quand l'user
     * consomme-t-il". `strftime('%w', timestamp)` retourne 0=Sunday→6=Saturday
     * en SQLite ; on shift côté Kotlin pour avoir 0=Monday→6=Sunday (style EU).
     */
    @Query("""
        SELECT
            CAST(strftime('%H', timestamp) AS INTEGER) as hour,
            CAST(strftime('%w', timestamp) AS INTEGER) as dayOfWeek,
            COUNT(*) as calls,
            COALESCE(SUM(tokensInput + tokensOutput + tokensThinking), 0) as tokens
        FROM llm_usage_events
        WHERE timestamp >= :since
        GROUP BY hour, dayOfWeek
    """)
    suspend fun getHourlyHeatmap(since: LocalDateTime): List<UsageHourBucket>

    /** Série journalière pour line chart. */
    @Query("""
        SELECT
            date(timestamp) as day,
            COUNT(*) as calls,
            COALESCE(SUM(tokensInput + tokensOutput + tokensThinking), 0) as tokens,
            COALESCE(SUM(costUsd), 0.0) as costUsd
        FROM llm_usage_events
        WHERE timestamp >= :since
        GROUP BY day
        ORDER BY day ASC
    """)
    suspend fun getDailySeries(since: LocalDateTime): List<UsageDayBucket>

    /** Observable count des events pour invalider les agrégations en live. */
    @Query("SELECT COUNT(*) FROM llm_usage_events")
    fun observeCount(): Flow<Int>
}

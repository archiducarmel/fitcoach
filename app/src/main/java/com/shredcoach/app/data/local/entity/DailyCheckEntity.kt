package com.shredcoach.app.data.local.entity


import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime

@Entity(
    tableName = "daily_checks",
    foreignKeys = [
        ForeignKey(
            entity = NutritionScheduleEntity::class,
            parentColumns = ["id"],
            childColumns = ["nutritionScheduleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("nutritionScheduleId"), Index("date")]
)
@Immutable
data class DailyCheckEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val nutritionScheduleId: Long,
    val date: LocalDate,
    val checked: Boolean = false,
    val checkedAt: LocalDateTime? = null
)

package com.shredcoach.app.data.local.converter

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.shredcoach.app.data.local.entity.EquipmentType
import com.shredcoach.app.data.local.entity.FitnessGoal
import com.shredcoach.app.data.local.entity.FitnessLevel
import com.shredcoach.app.data.local.entity.MealType
import com.shredcoach.app.data.local.entity.NutritionType
import com.shredcoach.app.domain.model.ExerciseVariant
import com.shredcoach.app.domain.model.MuscleGroup
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class Converters {
    private val gson = Gson()

    // LocalDateTime
    @TypeConverter
    fun fromLocalDateTime(value: LocalDateTime?): String? {
        return value?.toString()
    }

    @TypeConverter
    fun toLocalDateTime(value: String?): LocalDateTime? {
        return value?.let { LocalDateTime.parse(it) }
    }

    // LocalDate
    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? {
        return value?.toString()
    }

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? {
        return value?.let { LocalDate.parse(it) }
    }

    // LocalTime
    @TypeConverter
    fun fromLocalTime(value: LocalTime?): String? {
        return value?.toString()
    }

    @TypeConverter
    fun toLocalTime(value: String?): LocalTime? {
        return value?.let { LocalTime.parse(it) }
    }

    // Set<Int> (for workout days)
    @TypeConverter
    fun fromIntSet(value: Set<Int>?): String? {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toIntSet(value: String?): Set<Int>? {
        if (value == null) return null
        val type = object : TypeToken<Set<Int>>() {}.type
        return gson.fromJson(value, type)
    }

    // MuscleGroup
    @TypeConverter
    fun fromMuscleGroup(value: MuscleGroup?): String? {
        return value?.name
    }

    @TypeConverter
    fun toMuscleGroup(value: String?): MuscleGroup? {
        return value?.let { MuscleGroup.valueOf(it) }
    }

    // ExerciseVariant
    @TypeConverter
    fun fromExerciseVariant(value: ExerciseVariant?): String? {
        return value?.name
    }

    @TypeConverter
    fun toExerciseVariant(value: String?): ExerciseVariant? {
        return value?.let { ExerciseVariant.valueOf(it) }
    }

    // FitnessLevel
    @TypeConverter
    fun fromFitnessLevel(value: FitnessLevel?): String? {
        return value?.name
    }

    @TypeConverter
    fun toFitnessLevel(value: String?): FitnessLevel? {
        return value?.let { FitnessLevel.valueOf(it) }
    }

    // EquipmentType
    @TypeConverter
    fun fromEquipmentType(value: EquipmentType?): String? {
        return value?.name
    }

    @TypeConverter
    fun toEquipmentType(value: String?): EquipmentType? {
        return value?.let { EquipmentType.valueOf(it) }
    }

    // FitnessGoal
    @TypeConverter
    fun fromFitnessGoal(value: FitnessGoal?): String? {
        return value?.name
    }

    @TypeConverter
    fun toFitnessGoal(value: String?): FitnessGoal? {
        return value?.let { FitnessGoal.valueOf(it) }
    }

    // NutritionType
    @TypeConverter
    fun fromNutritionType(value: NutritionType?): String? {
        return value?.name
    }

    @TypeConverter
    fun toNutritionType(value: String?): NutritionType? {
        return value?.let { NutritionType.valueOf(it) }
    }

    // MealType
    @TypeConverter
    fun fromMealType(value: MealType?): String? = value?.name

    @TypeConverter
    fun toMealType(value: String?): MealType? = value?.let { MealType.valueOf(it) }

    // PhotoType
    @TypeConverter
    fun fromPhotoType(value: com.shredcoach.app.data.local.entity.PhotoType?): String? = value?.name

    @TypeConverter
    fun toPhotoType(value: String?): com.shredcoach.app.data.local.entity.PhotoType? = value?.let { com.shredcoach.app.data.local.entity.PhotoType.valueOf(it) }

    // AnalysisVerdict (glucose daily analysis)
    @TypeConverter
    fun fromAnalysisVerdict(value: com.shredcoach.app.data.local.entity.AnalysisVerdict?): String? =
        value?.name

    @TypeConverter
    fun toAnalysisVerdict(value: String?): com.shredcoach.app.data.local.entity.AnalysisVerdict? =
        value?.let { com.shredcoach.app.data.local.entity.AnalysisVerdict.valueOf(it) }
}

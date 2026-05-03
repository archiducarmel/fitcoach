package com.shredcoach.app.presentation.profile

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shredcoach.app.data.local.entity.*
import com.shredcoach.app.data.repository.NutritionRepository
import com.shredcoach.app.data.repository.UserRepository
import com.shredcoach.app.domain.nutrition.TdeeCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class ProfileState(
    val profile: UserProfileEntity? = null,
    val weightLogs: List<WeightLogEntity> = emptyList(),
    val isLoading: Boolean = true,
    // Edit fields
    val editFirstName: String = "",
    val editLastName: String = "",
    val editAge: String = "",
    val editHeight: String = "",
    val editWeight: String = "",
    val editTargetWeight: String = "",
    val editSex: String = "M",
    // Mesures
    val editWaist: String = "", val editChest: String = "", val editArm: String = "",
    val editThigh: String = "", val editHip: String = "", val editCalf: String = "",
    // Weight dialog
    val showAddWeight: Boolean = false,
    val newWeight: String = "",
    // Calculs
    val weeklyChange: Double = 0.0, // kg/semaine (négatif = perte)
    val showDeleteConfirm: Boolean = false
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val nutritionRepository: NutritionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    init { loadProfile(); loadWeightLogs() }

    private fun loadProfile() {
        viewModelScope.launch {
            userRepository.getUserProfile().collect { profile ->
                if (profile != null) {
                    _state.update {
                        it.copy(
                            profile = profile, isLoading = false,
                            editFirstName = profile.firstName, editLastName = profile.lastName,
                            editAge = profile.age.toString(), editHeight = profile.heightCm.toString(),
                            editWeight = profile.currentWeightKg.toString(),
                            editTargetWeight = profile.targetWeightKg.toString(),
                            editSex = profile.sex,
                            editWaist = if (profile.waistCm > 0) profile.waistCm.toString() else "",
                            editChest = if (profile.chestCm > 0) profile.chestCm.toString() else "",
                            editArm = if (profile.armCm > 0) profile.armCm.toString() else "",
                            editThigh = if (profile.thighCm > 0) profile.thighCm.toString() else "",
                            editHip = if (profile.hipCm > 0) profile.hipCm.toString() else "",
                            editCalf = if (profile.calfCm > 0) profile.calfCm.toString() else ""
                        )
                    }
                } else {
                    userRepository.insertUserProfile(UserProfileEntity(firstName = "Athlète"))
                }
            }
        }
    }

    private fun loadWeightLogs() {
        viewModelScope.launch {
            userRepository.getAllWeightLogs().collect { logs ->
                val weeklyChange = calculateWeeklyChange(logs)
                _state.update { it.copy(weightLogs = logs, weeklyChange = weeklyChange) }
            }
        }
    }

    private fun calculateWeeklyChange(logs: List<WeightLogEntity>): Double {
        if (logs.size < 2) return 0.0
        val sorted = logs.sortedBy { it.date }
        val recent = sorted.last()
        val weekAgo = sorted.filter { it.date <= recent.date.minusDays(5) }.lastOrNull() ?: sorted.first()
        val days = ChronoUnit.DAYS.between(weekAgo.date, recent.date).coerceAtLeast(1)
        return (recent.weightKg - weekAgo.weightKg) / days * 7
    }

    // Edit handlers
    fun onFirstNameChanged(v: String) { _state.update { it.copy(editFirstName = v) } }
    fun onLastNameChanged(v: String) { _state.update { it.copy(editLastName = v) } }
    fun onAgeChanged(v: String) { if (v.isEmpty() || v.matches(Regex("^\\d+$"))) _state.update { it.copy(editAge = v) } }
    fun onHeightChanged(v: String) { if (v.isEmpty() || v.matches(Regex("^\\d+$"))) _state.update { it.copy(editHeight = v) } }
    fun onWeightChanged(v: String) { if (v.isEmpty() || v.matches(Regex("^\\d*\\.?\\d*$"))) _state.update { it.copy(editWeight = v) } }
    fun onTargetWeightChanged(v: String) { if (v.isEmpty() || v.matches(Regex("^\\d*\\.?\\d*$"))) _state.update { it.copy(editTargetWeight = v) } }
    fun onSexChanged(v: String) { _state.update { it.copy(editSex = v) } }
    fun onMeasureChanged(field: String, v: String) {
        if (v.isNotEmpty() && !v.matches(Regex("^\\d*\\.?\\d*$"))) return
        _state.update { when (field) {
            "waist" -> it.copy(editWaist = v); "chest" -> it.copy(editChest = v)
            "arm" -> it.copy(editArm = v); "thigh" -> it.copy(editThigh = v)
            "hip" -> it.copy(editHip = v); "calf" -> it.copy(editCalf = v)
            else -> it
        }}
    }

    fun saveProfile() {
        val s = _state.value; val p = s.profile ?: return
        viewModelScope.launch {
            userRepository.updateUserProfile(p.copy(
                firstName = s.editFirstName.trim().replaceFirstChar { it.uppercase() },
                lastName = s.editLastName.trim().replaceFirstChar { it.uppercase() },
                age = s.editAge.toIntOrNull() ?: p.age, heightCm = s.editHeight.toIntOrNull() ?: p.heightCm,
                currentWeightKg = s.editWeight.toDoubleOrNull() ?: p.currentWeightKg,
                targetWeightKg = s.editTargetWeight.toDoubleOrNull() ?: p.targetWeightKg,
                sex = s.editSex,
                waistCm = s.editWaist.toDoubleOrNull() ?: 0.0, chestCm = s.editChest.toDoubleOrNull() ?: 0.0,
                armCm = s.editArm.toDoubleOrNull() ?: 0.0, thighCm = s.editThigh.toDoubleOrNull() ?: 0.0,
                hipCm = s.editHip.toDoubleOrNull() ?: 0.0, calfCm = s.editCalf.toDoubleOrNull() ?: 0.0
            ))
            recalculateTDEE()
        }
    }

    // Profile photo
    fun updateProfilePhoto(path: String) {
        val p = _state.value.profile ?: return
        // Supprimer l'ancienne photo si elle existe
        p.profilePhotoPath?.let { old -> try { java.io.File(old).delete() } catch (_: Exception) {} }
        val newPath = path.ifBlank { null }
        viewModelScope.launch { userRepository.updateUserProfile(p.copy(profilePhotoPath = newPath)) }
    }

    // Objectifs (mise à jour directe en DB)
    fun updateLevel(value: FitnessLevel) { val p = _state.value.profile ?: return; viewModelScope.launch { userRepository.updateUserProfile(p.copy(level = value)); loadProfile() } }
    fun updateEquipment(value: EquipmentType) { val p = _state.value.profile ?: return; viewModelScope.launch { userRepository.updateUserProfile(p.copy(equipment = value)); loadProfile() } }
    fun updateGoal(value: FitnessGoal) { val p = _state.value.profile ?: return; viewModelScope.launch { userRepository.updateUserProfile(p.copy(goal = value)); loadProfile(); recalculateTDEE() } }

    // Weight tracking
    fun showAddWeight() { _state.update { it.copy(showAddWeight = true, newWeight = "") } }
    fun hideAddWeight() { _state.update { it.copy(showAddWeight = false) } }
    fun onNewWeightChanged(v: String) { if (v.isEmpty() || v.matches(Regex("^\\d*\\.?\\d*$"))) _state.update { it.copy(newWeight = v) } }

    fun addWeightLog() {
        val w = _state.value.newWeight.toDoubleOrNull() ?: return
        viewModelScope.launch {
            userRepository.insertWeightLog(WeightLogEntity(date = LocalDate.now(), weightKg = w))
            userRepository.updateUserProfile(_state.value.profile!!.copy(currentWeightKg = w))
            _state.update { it.copy(showAddWeight = false) }
            recalculateTDEE()
        }
    }

    fun deleteWeightLog(log: WeightLogEntity) {
        viewModelScope.launch { userRepository.deleteWeightLog(log) }
    }

    /**
     * Recalcule le TDEE à partir du profil actuel et met à jour NutritionGoalEntity.
     * Appelé automatiquement quand le poids ou l'objectif changent.
     */
    private suspend fun recalculateTDEE() {
        val profile = userRepository.getUserProfileOnce() ?: return
        val existing = nutritionRepository.getNutritionGoalOnce() ?: NutritionGoalEntity()
        val newCalories = TdeeCalculator.targetCalories(
            sex = profile.sex,
            weightKg = profile.currentWeightKg,
            heightCm = profile.heightCm,
            age = profile.age,
            activityLevel = existing.activityLevel,
            goal = profile.goal
        )
        nutritionRepository.saveNutritionGoal(
            existing.copy(
                targetCalories = newCalories,
                weight = profile.currentWeightKg,
                height = profile.heightCm,
                age = profile.age,
                sex = profile.sex,
                goal = profile.goal.name
            )
        )
    }

    // Delete account
    fun showDeleteConfirm() { _state.update { it.copy(showDeleteConfirm = true) } }
    fun hideDeleteConfirm() { _state.update { it.copy(showDeleteConfirm = false) } }

    fun deleteAllData(context: Context) {
        viewModelScope.launch {
            context.deleteDatabase("shredcoach_db")
            _state.update { it.copy(showDeleteConfirm = false) }
            // Force restart
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Runtime.getRuntime().exit(0)
        }
    }

    // Export backup
    fun exportBackup(context: Context) {
        viewModelScope.launch {
            try {
                val p = _state.value.profile ?: return@launch
                val logs = _state.value.weightLogs
                val sb = StringBuilder()
                sb.appendLine("=== ShredCoach Backup ===")
                sb.appendLine("Date: ${LocalDate.now()}")
                sb.appendLine("\n--- Profil ---")
                sb.appendLine("Nom: ${p.firstName} ${p.lastName}")
                sb.appendLine("Age: ${p.age} | Taille: ${p.heightCm}cm | Poids: ${p.currentWeightKg}kg")
                sb.appendLine("Objectif: ${p.targetWeightKg}kg | Goal: ${p.goal}")
                sb.appendLine("Mesures: Taille=${p.waistCm} Poitrine=${p.chestCm} Bras=${p.armCm} Cuisse=${p.thighCm}")
                sb.appendLine("\n--- Historique Poids ---")
                logs.sortedBy { it.date }.forEach { sb.appendLine("${it.date}: ${it.weightKg}kg") }

                val file = File(context.cacheDir, "shredcoach_backup_${LocalDate.now()}.txt")
                file.writeText(sb.toString())
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "ShredCoach Backup")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Sauvegarder"))
            } catch (_: Exception) {}
        }
    }
}

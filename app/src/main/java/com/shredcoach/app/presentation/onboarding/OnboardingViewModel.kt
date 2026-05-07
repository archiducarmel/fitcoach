package com.shredcoach.app.presentation.onboarding


import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shredcoach.app.data.local.entity.*
import com.shredcoach.app.data.repository.NutritionRepository
import com.shredcoach.app.data.repository.UserRepository
import com.shredcoach.app.domain.nutrition.TdeeCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class OnboardingState(
    val currentPage: Int = 0,
    val firstName: String = "",
    val lastName: String = "",
    val age: String = "30",
    val height: String = "178",
    val weight: String = "80",
    val sex: String = "M",
    val goal: FitnessGoal = FitnessGoal.SHRED,
    val level: FitnessLevel = FitnessLevel.INTERMEDIATE,
    val equipment: EquipmentType = EquipmentType.FULL_GYM,
    val targetCalories: String = "2200",
    val targetProteins: String = "180",
    val isComplete: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val nutritionRepository: NutritionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    fun nextPage() { _state.update { it.copy(currentPage = it.currentPage + 1) } }
    fun prevPage() { if (_state.value.currentPage > 0) _state.update { it.copy(currentPage = it.currentPage - 1) } }

    fun onFirstNameChanged(v: String) { _state.update { it.copy(firstName = v) } }
    fun onLastNameChanged(v: String) { _state.update { it.copy(lastName = v) } }
    fun onAgeChanged(v: String) { if (v.isEmpty() || v.matches(Regex("^\\d+$"))) _state.update { it.copy(age = v) } }
    fun onHeightChanged(v: String) { if (v.isEmpty() || v.matches(Regex("^\\d+$"))) _state.update { it.copy(height = v) } }
    fun onWeightChanged(v: String) { if (v.isEmpty() || v.matches(Regex("^\\d*\\.?\\d*$"))) _state.update { it.copy(weight = v) } }
    fun onSexChanged(v: String) { _state.update { it.copy(sex = v) } }
    fun onGoalChanged(v: FitnessGoal) { _state.update { it.copy(goal = v) } }
    fun onLevelChanged(v: FitnessLevel) { _state.update { it.copy(level = v) } }
    fun onEquipmentChanged(v: EquipmentType) { _state.update { it.copy(equipment = v) } }
    fun onCaloriesChanged(v: String) { if (v.isEmpty() || v.matches(Regex("^\\d+$"))) _state.update { it.copy(targetCalories = v) } }
    fun onProteinsChanged(v: String) { if (v.isEmpty() || v.matches(Regex("^\\d+$"))) _state.update { it.copy(targetProteins = v) } }

    fun calculateTDEE(): Int {
        val s = _state.value
        // Base sédentaire (BMR × 1.20) + ajustement objectif. Le bonus
        // calorique des séances est ensuite ajouté dynamiquement par
        // NutritionViewModel selon l'activité réelle.
        return TdeeCalculator.targetCaloriesSedentaryBase(
            sex = s.sex,
            weightKg = s.weight.toDoubleOrNull() ?: 80.0,
            heightCm = s.height.toIntOrNull() ?: 178,
            age = s.age.toIntOrNull() ?: 30,
            goal = s.goal
        )
    }

    fun completeOnboarding() {
        val s = _state.value
        viewModelScope.launch {
            // Guard race avec post-restore : si un profil existe déjà (restore
            // depuis Google Drive vient juste de peupler la DB), on ne réinsert
            // pas — sinon duplicate UserProfileEntity. On marque juste l'onboarding
            // terminé pour déclencher la nav.
            val existing = userRepository.getUserProfileOnce()
            if (existing != null) {
                _state.update { it.copy(isComplete = true) }
                return@launch
            }
            // Créer le profil
            userRepository.insertUserProfile(UserProfileEntity(
                firstName = s.firstName.ifBlank { "Athlète" },
                lastName = s.lastName,
                age = s.age.toIntOrNull() ?: 30,
                sex = s.sex,
                heightCm = s.height.toIntOrNull() ?: 178,
                currentWeightKg = s.weight.toDoubleOrNull() ?: 80.0,
                targetWeightKg = when (s.goal) {
                    FitnessGoal.SHRED -> (s.weight.toDoubleOrNull() ?: 80.0) - 5.0
                    FitnessGoal.BULK -> (s.weight.toDoubleOrNull() ?: 80.0) + 5.0
                    FitnessGoal.MAINTAIN -> s.weight.toDoubleOrNull() ?: 80.0
                },
                level = s.level,
                equipment = s.equipment,
                goal = s.goal
            ))

            // Créer les objectifs nutrition
            nutritionRepository.saveNutritionGoal(NutritionGoalEntity(
                targetCalories = s.targetCalories.toIntOrNull() ?: calculateTDEE(),
                targetProteins = s.targetProteins.toIntOrNull() ?: 180,
                targetCarbs = 220, targetFats = 70,
                weight = s.weight.toDoubleOrNull() ?: 80.0,
                height = s.height.toIntOrNull() ?: 178,
                age = s.age.toIntOrNull() ?: 30,
                sex = s.sex,
                goal = s.goal.name
            ))

            // Seed les aliments
            nutritionRepository.seedFoodsIfEmpty()

            _state.update { it.copy(isComplete = true) }
        }
    }

    /**
     * Court-circuit pour le cas "restore depuis Google Drive pendant onboarding".
     * La DB a déjà été repeuplée par le pipeline restore (UserProfileEntity inclus),
     * donc on ne doit PAS appeler [completeOnboarding] qui ferait un insert
     * dupliqué. On marque juste l'onboarding terminé pour déclencher la nav.
     */
    fun markCompletedFromRestore() {
        _state.update { it.copy(isComplete = true) }
    }
}

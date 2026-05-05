package com.shredcoach.app.presentation.calendar


import androidx.compose.runtime.Immutable
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shredcoach.app.data.local.entity.ScheduledWorkoutEntity
import com.shredcoach.app.data.local.entity.WorkoutEntity
import com.shredcoach.app.data.local.entity.WorkoutLogEntity
import com.shredcoach.app.data.local.secure.SecureKeyStore
import com.shredcoach.app.data.remote.LlmProvider
import com.shredcoach.app.data.repository.ChatRepository
import com.shredcoach.app.data.repository.ScheduledWorkoutRepository
import com.shredcoach.app.data.repository.UserRepository
import com.shredcoach.app.data.repository.WorkoutRepository
import com.shredcoach.app.domain.calendar.FrenchHolidays
import com.shredcoach.app.domain.calendar.FrenchSchoolHolidays
import com.shredcoach.app.notification.NotificationScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import javax.inject.Inject

/**
 * State global du calendrier.
 */
@Immutable
data class CalendarState(
    val currentMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate? = LocalDate.now(),
    // Données du mois courant
    val monthScheduled: List<ScheduledWorkoutEntity> = emptyList(),
    val monthLogs: List<WorkoutLogEntity> = emptyList(),
    val holidays: Map<LocalDate, String> = emptyMap(),
    val schoolHolidays: Set<LocalDate> = emptySet(),
    // Config utilisateur
    val workoutDays: Set<Int> = emptySet(), // 1-7 (Lun-Dim)
    val schoolZone: FrenchSchoolHolidays.Zone = FrenchSchoolHolidays.Zone.C,
    // Favoris disponibles pour quick schedule
    val favoriteWorkouts: List<WorkoutEntity> = emptyList(),
    // Stats assiduité
    val adherencePercent: Int = 0,
    val completedThisMonth: Int = 0,
    val plannedThisMonth: Int = 0,
    val streakDays: Int = 0,
    // Prochaine séance à venir
    val nextUpcoming: ScheduledWorkoutEntity? = null,
    // Suggestions IA
    val isSuggesting: Boolean = false,
    val suggestedDates: List<LocalDate> = emptyList(),
    val suggestionMessage: String = "",
    val suggestionError: String? = null,
    // UI
    val showScheduleSheet: Boolean = false,
    val prefillDate: LocalDate? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val scheduledRepo: ScheduledWorkoutRepository,
    private val workoutRepository: WorkoutRepository,
    private val userRepository: UserRepository,
    private val chatRepository: ChatRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(CalendarState())
    val state: StateFlow<CalendarState> = _state.asStateFlow()

    /** Job du collect du mois courant — annulé à chaque changement de mois pour éviter les fuites. */
    private var monthCollectJob: Job? = null

    init {
        viewModelScope.launch {
            val profile = userRepository.getUserProfileOnce()
            _state.update {
                it.copy(
                    workoutDays = profile?.workoutDays ?: setOf(1, 3, 5),
                    streakDays = profile?.currentStreakDays ?: 0
                )
            }
        }
        // Observer les favoris en continu (l'user peut en ajouter depuis un autre écran)
        viewModelScope.launch {
            workoutRepository.getFavoriteWorkouts()
                .catch { emit(emptyList()) }
                .collect { favs ->
                    _state.update { it.copy(favoriteWorkouts = favs) }
                }
        }
        // Observer en continu TOUTES les séances → recalcule nextUpcoming à chaque changement
        // (création, suppression, completion...). Flux global non-scopé au mois courant.
        viewModelScope.launch {
            scheduledRepo.getAll().collect { all ->
                val today = LocalDate.now()
                val upcoming = all
                    .filter { it.status == "PLANNED" && !it.date.isBefore(today) }
                    .sortedWith(compareBy({ it.date }, { it.time ?: LocalTime.MIN }))
                    .firstOrNull()
                _state.update { it.copy(nextUpcoming = upcoming) }
            }
        }
        loadMonth(_state.value.currentMonth)
    }

    /** Charge les données pour un mois donné (scheduled + logs + holidays + stats). */
    private fun loadMonth(month: YearMonth) {
        val start = month.atDay(1)
        val end = month.atEndOfMonth()

        // Annuler le collect précédent (sinon fuite + écrasements de state croisés)
        monthCollectJob?.cancel()
        monthCollectJob = viewModelScope.launch {
            _state.update { it.copy(currentMonth = month, isLoading = true) }

            // Flows combinés : scheduled + logs (observation continue)
            scheduledRepo.getBetween(start, end).collect { scheduled ->
                val logs = try {
                    workoutRepository.getRecentWorkoutLogs(100).first()
                        .filter { it.date.toLocalDate() in start..end }
                } catch (_: Exception) { emptyList() }

                val completedThisMonth = logs.count { it.completed }
                val plannedThisMonth = scheduled.size
                val adherence = if (plannedThisMonth == 0) {
                    if (completedThisMonth > 0) 100 else 0
                } else {
                    ((completedThisMonth.toDouble() / plannedThisMonth) * 100).toInt().coerceIn(0, 100)
                }

                val holidays = FrenchHolidays.mapForRange(start, end)
                val schoolHolidays = FrenchSchoolHolidays.daysInRange(start, end, _state.value.schoolZone)

                _state.update {
                    it.copy(
                        monthScheduled = scheduled,
                        monthLogs = logs,
                        holidays = holidays,
                        schoolHolidays = schoolHolidays,
                        completedThisMonth = completedThisMonth,
                        plannedThisMonth = plannedThisMonth,
                        adherencePercent = adherence,
                        isLoading = false
                    )
                }
            }
        }
    }

    // ── Navigation mensuelle ──
    fun goPrevMonth() = loadMonth(_state.value.currentMonth.minusMonths(1))
    fun goNextMonth() = loadMonth(_state.value.currentMonth.plusMonths(1))
    fun goToday() {
        val today = LocalDate.now()
        loadMonth(YearMonth.from(today))
        _state.update { it.copy(selectedDate = today) }
    }

    fun selectDate(date: LocalDate?) {
        _state.update { it.copy(selectedDate = date) }
    }

    // ── Bottom sheet ouverture/fermeture ──
    fun openScheduleSheet(date: LocalDate? = null) {
        _state.update { it.copy(showScheduleSheet = true, prefillDate = date ?: it.selectedDate ?: LocalDate.now()) }
    }
    fun closeScheduleSheet() {
        _state.update { it.copy(showScheduleSheet = false, prefillDate = null) }
    }

    // ── CRUD séances ──

    /**
     * Crée une nouvelle séance planifiée. Programme automatiquement les rappels si time != null.
     */
    fun scheduleSession(
        date: LocalDate,
        time: LocalTime? = null,
        workoutId: Long? = null,
        title: String = "",
        note: String = "",
        source: String = "manual"
    ) {
        viewModelScope.launch {
            val entity = ScheduledWorkoutEntity(
                date = date,
                time = time,
                workoutId = workoutId,
                title = title,
                note = note,
                source = source
            )
            val id = scheduledRepo.insert(entity)
            // Programmer les rappels (shaker 2h + start 30min)
            NotificationScheduler.scheduleWorkoutReminders(appContext, id, date, time)
            closeScheduleSheet()
        }
    }

    fun deleteSchedule(id: Long) {
        viewModelScope.launch {
            NotificationScheduler.cancelWorkoutReminders(appContext, id)
            scheduledRepo.deleteById(id)
        }
    }

    fun markSkipped(id: Long) {
        viewModelScope.launch {
            scheduledRepo.markSkipped(id)
            NotificationScheduler.cancelWorkoutReminders(appContext, id)
        }
    }

    // ── IA : suggestion prochaines séances ──

    /**
     * Demande à Shreddy de proposer les prochaines séances basées sur :
     *  - workoutDays (jours préférés)
     *  - historique récent (recentLogs)
     *  - streak actuel
     *  - séances déjà planifiées (pour ne pas doublonner)
     */
    fun suggestNextSessions() {
        viewModelScope.launch {
            _state.update { it.copy(isSuggesting = true, suggestionError = null) }

            val profile = userRepository.getUserProfileOnce()
            val workoutDays = profile?.workoutDays ?: setOf(1, 3, 5)

            // Calcul local : prochains jours de workoutDays dans les 14 prochains jours
            val today = LocalDate.now()
            val existingDates = scheduledRepo.getUpcoming(today, 20).map { it.date }.toSet()
            val suggestions = mutableListOf<LocalDate>()
            var cursor = today
            while (suggestions.size < 5 && cursor.isBefore(today.plusDays(21))) {
                val dayOfWeek = cursor.dayOfWeek.value // 1 = Lundi
                if (dayOfWeek in workoutDays && cursor !in existingDates) {
                    suggestions.add(cursor)
                }
                cursor = cursor.plusDays(1)
            }

            // Message IA (contextualisé)
            val apiKey = userRepository.getApiKey(SecureKeyStore.Provider.LLM)
            val llmMessage = if (apiKey.isNotBlank() && suggestions.isNotEmpty() && profile != null) {
                try {
                    val provider = runCatching { LlmProvider.valueOf(profile.llmProvider) }
                        .getOrDefault(LlmProvider.GROQ)
                    val model = profile.llmModel.takeIf { it.isNotBlank() }

                    val datesStr = suggestions.joinToString(", ") {
                        it.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.FRANCE) + " " + it.dayOfMonth + "/" + it.monthValue
                    }
                    val firstName = profile.firstName.ifBlank { "toi" }
                    val prompt = """
D'après les habitudes de $firstName (jours préférés: ${workoutDays.sorted()}, streak actuel: ${profile.currentStreakDays}j),
voici les 5 prochaines dates suggérées : $datesStr.

Rédige en UNE seule phrase (max 180 chars) une justification motivante et humoristique pour programmer ces séances.
Pas de salutations, pas de liste — juste la phrase.
                    """.trimIndent()

                    kotlinx.coroutines.withTimeout(12_000) {
                        chatRepository.quickCoachMessage(
                            prompt = prompt,
                            systemPrompt = "Tu es Shreddy, coach sportif IA. Réponds en français, ton humoristique bienveillant, max 180 caractères, une seule phrase.",
                            provider = provider,
                            apiKey = apiKey,
                            model = model
                        )
                    }.getOrNull()?.takeIf { it.isNotBlank() }
                } catch (_: Exception) { null }
            } else null

            val fallbackMsg = "${suggestions.size} séances suggérées selon tes jours préférés. Let's go !"

            _state.update {
                it.copy(
                    isSuggesting = false,
                    suggestedDates = suggestions,
                    suggestionMessage = llmMessage ?: fallbackMsg
                )
            }
        }
    }

    /** Accepte toutes les suggestions en les créant en DB en un seul coup. */
    fun acceptAllSuggestions() {
        val dates = _state.value.suggestedDates
        if (dates.isEmpty()) return
        viewModelScope.launch {
            dates.forEach { date ->
                val entity = ScheduledWorkoutEntity(
                    date = date,
                    time = null,
                    source = "ai_suggestion",
                    title = "Séance Shreddy"
                )
                scheduledRepo.insert(entity)
            }
            _state.update { it.copy(suggestedDates = emptyList(), suggestionMessage = "") }
        }
    }

    fun dismissSuggestions() {
        _state.update { it.copy(suggestedDates = emptyList(), suggestionMessage = "", suggestionError = null) }
    }
}

package com.shredcoach.app.domain.nutrition

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.google.gson.Gson
import com.shredcoach.app.data.local.entity.MealScanEntity
import com.shredcoach.app.data.remote.MealAnalysisResult
import java.text.Normalizer

// ═══════════════════════════════════════
// IngredientAggregator
// ═══════════════════════════════════════
//
// Pipeline d'analyse des scans repas pour produire des insights nutrition.
//
// Pourquoi ce module : la table `foods` ne fournit pas une vue exploitable
// (chaque scan crée un FoodEntity unique nommé "📷 Riz au poulet à la
// marocaine" → impossible d'agréger). En revanche, chaque MealScanEntity
// stocke `resultJson` = un MealAnalysisResult sérialisé qui contient la
// liste DÉCOMPOSÉE d'ingrédients avec leurs poids, macros et catégorie LLM.
//
// On parse donc les JSON des 30 derniers jours, on normalise les noms
// d'ingrédients (les LLM varient leur orthographe : "haricot vert", "haricots
// verts cuits", "haricot-vert"), on classe en catégories diététiques fixes,
// et on calcule plusieurs vues agrégées : top, répartition par catégorie,
// distribution Nutri-Score, score de diversité.

/**
 * Catégories diététiques de regroupement (vision macro-nutrition).
 * Distinct de la catégorie LLM par-ingrédient (qui peut être tout et
 * n'importe quoi : "Cat", "Légume", "Viande blanche"…). On classe ici dans
 * un nombre fini de buckets pour le donut + le code couleur du wordcloud.
 */
enum class IngredientCategory(
    val displayName: String,
    @androidx.annotation.StringRes val displayNameRes: Int,
    val color: Color,
    val emoji: String,
) {
    PROTEINES_ANIMALES("Protéines animales", com.shredcoach.app.R.string.ingredient_cat_animal_proteins, Color(0xFFEF4444), "🥩"),
    PROTEINES_VEGETALES("Protéines végétales", com.shredcoach.app.R.string.ingredient_cat_plant_proteins, Color(0xFF8B5CF6), "🫘"),
    LEGUMES("Légumes", com.shredcoach.app.R.string.ingredient_cat_vegetables, Color(0xFF10B981), "🥦"),
    FECULENTS("Féculents", com.shredcoach.app.R.string.ingredient_cat_starches, Color(0xFFF59E0B), "🍚"),
    FRUITS("Fruits", com.shredcoach.app.R.string.ingredient_cat_fruits, Color(0xFFEC4899), "🍎"),
    LAITIERS("Laitiers", com.shredcoach.app.R.string.ingredient_cat_dairy, Color(0xFF60A5FA), "🥛"),
    LIPIDES("Matières grasses", com.shredcoach.app.R.string.ingredient_cat_fats, Color(0xFFEAB308), "🧈"),
    BOISSONS("Boissons", com.shredcoach.app.R.string.ingredient_cat_drinks, Color(0xFF06B6D4), "🥤"),
    SUCRES("Sucres & desserts", com.shredcoach.app.R.string.ingredient_cat_sweets, Color(0xFFF472B6), "🍰"),
    AUTRES("Autres", com.shredcoach.app.R.string.ingredient_cat_other, Color(0xFF94A3B8), "•"),
}

@Immutable
data class IngredientStat(
    val displayName: String,
    val normalizedKey: String,
    val category: IngredientCategory,
    val totalGrams: Int,
    val totalCalories: Int,
    val occurrences: Int,   // nombre d'apparitions dans des plats (peut être > scanCount si plusieurs plats par scan)
    val scanCount: Int,     // nombre de scans distincts qui contiennent cet ingrédient
)

@Immutable
data class CategoryShare(
    val category: IngredientCategory,
    val totalGrams: Int,
    val ingredientCount: Int,    // nombre d'ingrédients distincts dans cette catégorie
    val percentage: Float,       // 0f..1f sur le total des grammes
)

@Immutable
data class NutriScoreDistribution(
    val countA: Int,
    val countB: Int,
    val countC: Int,
    val countD: Int,
    val countE: Int,
) {
    val total: Int get() = countA + countB + countC + countD + countE

    /** Part A+B (qualité haute) sur 0f..1f, 0f si aucun scan. */
    val highQualityShare: Float
        get() = if (total == 0) 0f else (countA + countB).toFloat() / total

    fun count(grade: Char): Int = when (grade) {
        'A' -> countA; 'B' -> countB; 'C' -> countC; 'D' -> countD; 'E' -> countE
        else -> 0
    }
}

@Immutable
data class NutritionInsights(
    val topIngredients: List<IngredientStat>,           // tri descendant par totalGrams
    val categoryShares: List<CategoryShare>,            // tri descendant par percentage
    val nutriScoreDistribution: NutriScoreDistribution,
    val totalScans: Int,                                // nombre de scans dans la fenêtre
    val totalUniqueIngredients: Int,                    // nb d'ingrédients normalisés distincts
    val totalGrams: Int,                                // somme des grammes ingrédients
    val periodDays: Int,
) {
    val isEmpty: Boolean get() = totalScans == 0 || topIngredients.isEmpty()
}

object IngredientAggregator {

    private val gson = Gson()

    /**
     * Point d'entrée principal. Parse les JSON, normalise et agrège.
     *
     * @param scans    Liste des MealScanEntity sur la fenêtre voulue.
     * @param periodDays Période en jours (pour l'affichage côté UI).
     */
    fun aggregate(scans: List<MealScanEntity>, periodDays: Int = 30): NutritionInsights {
        if (scans.isEmpty()) {
            return NutritionInsights(
                topIngredients = emptyList(),
                categoryShares = emptyList(),
                nutriScoreDistribution = NutriScoreDistribution(0, 0, 0, 0, 0),
                totalScans = 0,
                totalUniqueIngredients = 0,
                totalGrams = 0,
                periodDays = periodDays
            )
        }

        // Map: normalizedKey → accumulator
        data class Acc(
            val displayName: String,
            val category: IngredientCategory,
            var totalGrams: Int = 0,
            var totalCalories: Int = 0,
            var occurrences: Int = 0,
            val scanIds: MutableSet<Long> = mutableSetOf(),
        )

        val byIngredient = HashMap<String, Acc>()

        // Distribution Nutri-Score
        var a = 0; var b = 0; var c = 0; var d = 0; var e = 0

        for (scan in scans) {
            // Distribution Nutri-Score (on garde même les scans sans grade pour le compte global)
            when (scan.nutriScoreGrade.firstOrNull()) {
                'A' -> a++; 'B' -> b++; 'C' -> c++; 'D' -> d++; 'E' -> e++
            }

            // Parse le resultJson — robuste : ignore si malformé
            val analysis = parseResultJson(scan.resultJson) ?: continue

            for (dish in analysis.dishes) {
                for (ing in dish.ingredients) {
                    if (ing.name.isBlank() || ing.weightG <= 0) continue
                    val normalized = normalizeIngredientName(ing.name)
                    if (normalized.isBlank()) continue
                    val display = displayNameFromNormalized(normalized)
                    val category = categorize(normalized, ing.category)

                    val acc = byIngredient.getOrPut(normalized) {
                        Acc(displayName = display, category = category)
                    }
                    acc.totalGrams += ing.weightG
                    acc.totalCalories += ing.calories
                    acc.occurrences += 1
                    acc.scanIds += scan.id
                }
            }
        }

        // Construire les IngredientStat triés par poids
        val topIngredients = byIngredient.entries
            .map { (key, acc) ->
                IngredientStat(
                    displayName = acc.displayName,
                    normalizedKey = key,
                    category = acc.category,
                    totalGrams = acc.totalGrams,
                    totalCalories = acc.totalCalories,
                    occurrences = acc.occurrences,
                    scanCount = acc.scanIds.size,
                )
            }
            .sortedByDescending { it.totalGrams }

        // Répartition par catégorie
        val totalGrams = topIngredients.sumOf { it.totalGrams }
        val categoryShares = topIngredients
            .groupBy { it.category }
            .map { (cat, items) ->
                val sumG = items.sumOf { it.totalGrams }
                CategoryShare(
                    category = cat,
                    totalGrams = sumG,
                    ingredientCount = items.size,
                    percentage = if (totalGrams == 0) 0f else sumG.toFloat() / totalGrams
                )
            }
            .sortedByDescending { it.percentage }

        return NutritionInsights(
            topIngredients = topIngredients,
            categoryShares = categoryShares,
            nutriScoreDistribution = NutriScoreDistribution(a, b, c, d, e),
            totalScans = scans.size,
            totalUniqueIngredients = topIngredients.size,
            totalGrams = totalGrams,
            periodDays = periodDays,
        )
    }

    private fun parseResultJson(json: String): MealAnalysisResult? {
        if (json.isBlank()) return null
        return try {
            gson.fromJson(json, MealAnalysisResult::class.java)
        } catch (_: Exception) {
            null
        }
    }

    // ═══════════════════════════════════════
    // NORMALISATION DE NOM D'INGRÉDIENT
    // ═══════════════════════════════════════
    //
    // Les LLM produisent un texte FR libre. Pour agréger fiablement, on
    // applique une chaîne de normalisations :
    //  1. Lowercase
    //  2. Retire diacritiques (é→e, à→a, ç→c…) via NFKD
    //  3. Retire ponctuation et chiffres
    //  4. Retire articles/prépositions FR ("de", "des", "à la"…)
    //  5. Retire qualificatifs courants ("frais", "cuit", "bio", "grillé"…)
    //  6. Lemmatise pluriel→singulier (règle simple "s" final)
    //  7. Compacte espaces multiples
    //
    // Exemples :
    //  "Haricots verts cuits"  → "haricot vert"
    //  "Filet de poulet grillé" → "filet poulet"
    //  "Pommes de terre vapeur" → "pomme terre"
    //  "Œufs au plat"          → "oeuf"
    //  "Riz blanc cuit"        → "riz"

    private val STOPWORDS = setOf(
        "de", "du", "des", "le", "la", "les", "l", "d", "à", "a", "au", "aux",
        "et", "en", "ou", "un", "une", "avec", "sans", "pour", "dans"
    )

    private val QUALIFIERS = setOf(
        // cuissons
        "cuit", "cuite", "cuits", "cuites",
        "cru", "crue", "crus", "crues",
        "grille", "grillee", "grilles", "grillees",
        "roti", "rotie", "rotis", "roties",
        "bouilli", "bouillie", "bouillis", "bouillies",
        "vapeur", "saute", "sautee", "sautes", "sautees",
        "poele", "poelee", "poeles", "poelees",
        "frit", "frite", "frits", "frites",
        "fume", "fumee", "fumes", "fumees",
        "marine", "marinee", "marines", "marinees",
        // origine/qualité
        "bio", "frais", "fraiche", "fraiches",
        "sec", "seche", "sechee", "seches", "sechees",
        "complet", "complete", "complets", "completes",
        "nature", "naturel", "naturelle", "naturels", "naturelles",
        // état
        "haches", "hachee", "hachees", "hache",
        "emiette", "emiettee", "emiettes", "emiettees",
        "decortique", "decortiquee", "decortiques", "decortiquees",
        "rape", "rapee", "rapes", "rapees",
        "tranche", "tranchee", "tranches", "tranchees",
        // descripteurs neutres
        "petit", "petite", "petits", "petites",
        "gros", "grosse", "grosses",
        "moyen", "moyenne", "moyens", "moyennes",
        "entier", "entiere", "entiers", "entieres",
    )

    fun normalizeIngredientName(raw: String): String {
        if (raw.isBlank()) return ""
        // 1. Lowercase + 2. retire diacritiques
        val noAccent = Normalizer.normalize(raw.lowercase(), Normalizer.Form.NFKD)
            .replace(Regex("\\p{M}+"), "")
        // 3. Retire tout sauf lettres ASCII et espaces (vire chiffres/ponctuation/emojis)
        val onlyLetters = noAccent.replace(Regex("[^a-z\\s']"), " ")
        // Compacte espaces
        val tokens = onlyLetters.split(Regex("\\s+")).filter { it.isNotBlank() }
        // 4 + 5. retire stopwords + qualifiers
        // 6. lemmatise pluriel
        val cleaned = tokens
            .filter { it !in STOPWORDS && it !in QUALIFIERS }
            .map { lemmatize(it) }
            .filter { it.isNotBlank() }
        if (cleaned.isEmpty()) return ""
        // Limite à 3 tokens max — au-delà c'est sûrement une description
        // (ex: "filet poulet jaune cuit basse temperature" → "filet poulet jaune")
        return cleaned.take(3).joinToString(" ")
    }

    /**
     * Lemmatisation très simple : pluriel → singulier sur règles FR de base.
     * Pas de dictionnaire — vise 80% de cas avec règles purement morphologiques.
     */
    private fun lemmatize(token: String): String = when {
        token.length <= 3 -> token
        token.endsWith("eaux") -> token.dropLast(3) + "au"   // gateaux → gateau
        token.endsWith("aux") -> token.dropLast(2) + "l"     // chevaux → cheval (approx)
        token.endsWith("oux") -> token.dropLast(1)           // genoux → genou
        token.endsWith("ies") -> token.dropLast(1)           // tomatoes-FR rare
        token.endsWith("es") && token.length > 4 -> token.dropLast(1)  // tomates → tomate
        token.endsWith("s") -> token.dropLast(1)             // pluriel standard
        else -> token
    }

    /** Réinjection d'une casse "présentable" à partir du nom normalisé. */
    private fun displayNameFromNormalized(normalized: String): String =
        normalized.split(' ').joinToString(" ") { word ->
            if (word.isEmpty()) word
            else word.replaceFirstChar { it.uppercaseChar() }
        }

    // ═══════════════════════════════════════
    // CATÉGORISATION DIÉTÉTIQUE
    // ═══════════════════════════════════════
    //
    // On classe par mot-clé sur le nom normalisé. La catégorie LLM (`category`
    // dans Ingredient) est utilisée en fallback si elle est non vide et non
    // générique. Les listes ne se veulent pas exhaustives mais couvrent les
    // ingrédients courants d'un régime FR.

    private val PROT_ANIMAL = setOf(
        "poulet", "poule", "dinde", "canard", "veau", "boeuf", "agneau", "porc",
        "jambon", "bacon", "lardon", "saucisse", "merguez", "chorizo",
        "steak", "escalope", "blanc", "filet", "cuisse", "viande",
        "poisson", "saumon", "thon", "cabillaud", "merlu", "lieu", "sole",
        "sardine", "maquereau", "anchois", "hareng", "truite", "bar", "dorade",
        "crevette", "moule", "huitre", "calamar", "encornet", "poulpe", "homard",
        "oeuf", "blanc oeuf", "jaune oeuf",
    )

    private val PROT_VEGETAL = setOf(
        "lentille", "pois chiche", "pois casse", "haricot rouge", "haricot blanc",
        "haricot noir", "feve", "edamame", "soja", "tofu", "tempeh", "seitan",
        "azuki", "mungo", "cornille", "niebe", "flageolet",
    )

    private val LEGUMES = setOf(
        "tomate", "carotte", "courgette", "aubergine", "poivron", "piment",
        "oignon", "echalote", "ail", "poireau", "celeri", "fenouil",
        "salade", "laitue", "epinard", "roquette", "mache", "endive",
        "chou", "brocoli", "chou-fleur", "chou rouge", "chou kale",
        "champignon", "cepe", "girolle", "pleurote",
        "haricot vert", "haricot beurre", "petit pois",
        "asperge", "artichaut", "betterave", "navet", "radis", "panais",
        "concombre", "courge", "potiron", "potimarron", "butternut",
        "olive", "cornichon", "poireau",
        "legume",
    )

    private val FECULENTS = setOf(
        "riz", "pate", "spaghetti", "tagliatelle", "penne", "macaroni", "lasagne", "ravioli",
        "ble", "boulgour", "couscous", "semoule", "quinoa", "millet", "sarrasin", "epeautre",
        "pain", "baguette", "biscotte", "tortilla", "wrap", "naan", "pita",
        "pomme terre", "patate", "patate douce", "igname", "manioc", "taro",
        "polenta", "mais", "epi mais",
        "feculent",
    )

    private val FRUITS = setOf(
        "pomme", "poire", "banane", "orange", "clementine", "mandarine", "citron",
        "pamplemousse", "fraise", "framboise", "myrtille", "cassis", "groseille",
        "cerise", "abricot", "peche", "nectarine", "prune", "raisin",
        "kiwi", "ananas", "mangue", "papaye", "passion", "litchi", "grenade",
        "figue", "datte", "pasteque", "melon", "avocat", "noix coco",
        "fruit",
    )

    private val LAITIERS = setOf(
        "lait", "yaourt", "yogourt", "fromage blanc", "skyr", "kefir",
        "fromage", "comte", "emmental", "gruyere", "cheddar", "mozzarella",
        "parmesan", "feta", "ricotta", "chevre", "brie", "camembert", "roquefort",
        "creme", "creme fraiche", "beurre", "mascarpone", "petit suisse",
    )

    private val LIPIDES = setOf(
        "huile", "huile olive", "huile colza", "huile tournesol", "huile coco",
        "ghee", "saindoux", "margarine",
        "noix", "amande", "noisette", "cacahuete", "pistache", "pignon",
        "graine", "lin", "chia", "sesame", "tournesol",
    )

    private val BOISSONS = setOf(
        "eau", "the", "infusion", "tisane", "cafe", "expresso",
        "jus", "smoothie", "soda", "limonade", "biere", "vin",
        "boisson", "shake", "shaker", "lait amande", "lait soja", "lait avoine",
    )

    private val SUCRES = setOf(
        "sucre", "miel", "sirop", "confiture", "marmelade", "compote",
        "chocolat", "cacao", "praline", "nutella", "biscuit", "gateau",
        "tarte", "creme dessert", "glace", "sorbet", "bonbon", "caramel",
    )

    /**
     * Classe un ingrédient normalisé. Cherche d'abord une correspondance
     * exacte ou par sous-chaîne dans les buckets prioritaires, sinon utilise
     * la catégorie LLM en fallback, sinon AUTRES.
     */
    fun categorize(normalized: String, llmCategory: String): IngredientCategory {
        if (normalized.isBlank()) return IngredientCategory.AUTRES

        // Détection multi-token → on teste le nom complet ET chaque token
        val candidates = listOf(normalized) + normalized.split(' ').filter { it.length > 2 }

        for (term in candidates) {
            if (matchesAny(term, PROT_ANIMAL)) return IngredientCategory.PROTEINES_ANIMALES
            if (matchesAny(term, PROT_VEGETAL)) return IngredientCategory.PROTEINES_VEGETALES
            if (matchesAny(term, LEGUMES)) return IngredientCategory.LEGUMES
            if (matchesAny(term, FECULENTS)) return IngredientCategory.FECULENTS
            if (matchesAny(term, FRUITS)) return IngredientCategory.FRUITS
            if (matchesAny(term, LAITIERS)) return IngredientCategory.LAITIERS
            if (matchesAny(term, LIPIDES)) return IngredientCategory.LIPIDES
            if (matchesAny(term, BOISSONS)) return IngredientCategory.BOISSONS
            if (matchesAny(term, SUCRES)) return IngredientCategory.SUCRES
        }

        // Fallback sur la catégorie LLM (heuristique simple)
        val llmLower = llmCategory.lowercase()
        return when {
            llmLower.contains("legume") -> IngredientCategory.LEGUMES
            llmLower.contains("fruit") -> IngredientCategory.FRUITS
            llmLower.contains("feculent") || llmLower.contains("cereal") -> IngredientCategory.FECULENTS
            llmLower.contains("viande") || llmLower.contains("poisson") -> IngredientCategory.PROTEINES_ANIMALES
            llmLower.contains("legumineuse") || llmLower.contains("vegetal") -> IngredientCategory.PROTEINES_VEGETALES
            llmLower.contains("laitier") || llmLower.contains("fromage") -> IngredientCategory.LAITIERS
            llmLower.contains("matiere grasse") || llmLower.contains("graisse") -> IngredientCategory.LIPIDES
            llmLower.contains("boisson") -> IngredientCategory.BOISSONS
            llmLower.contains("sucre") || llmLower.contains("dessert") -> IngredientCategory.SUCRES
            else -> IngredientCategory.AUTRES
        }
    }

    /** Match exact OU le terme du dictionnaire est contenu dans la chaîne testée. */
    private fun matchesAny(term: String, dict: Set<String>): Boolean {
        if (term in dict) return true
        // Pour les entrées multi-mots du dictionnaire ("pomme terre", "haricot vert"),
        // vérifier qu'elles sont contenues dans le nom normalisé.
        return dict.any { entry -> entry.contains(' ') && term.contains(entry) }
    }
}

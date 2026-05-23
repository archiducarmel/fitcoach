package com.shredcoach.app.domain.llm

/**
 * Architecture sous-jacente du modèle. Important pour comprendre les trade-offs
 * (latence, mémoire, contexte) et pour les filtres UI avancés.
 *
 * **Détection auto possible depuis le model ID** :
 *  - Contient "moe", "mixtral", "phi-3.5-moe", "scout-17b-16e" → MoE
 *  - Contient "jamba" → HYBRID_MAMBA_TRANSFORMER
 *  - Contient "mamba" → MAMBA
 *  - Contient "recurrentgemma" → RNN
 *  - Contient "flux", "diffusion", "sd-", "sdxl" → DIFFUSION
 *  - Contient "magpie-flow" → FLOW_MATCHING
 *  - Contient "dino", "clip" en visuel → CONVOLUTIONAL
 *  - Contient "alphafold", "esm", "boltz" → GRAPH_NEURAL (ou TRANSFORMER selon modèle)
 *  - Default → TRANSFORMER_DENSE
 */
enum class ModelArchitecture(val displayName: String, val emoji: String) {
    TRANSFORMER_DENSE("Transformer dense", "⚙️"),
    TRANSFORMER_MOE("Transformer MoE", "🧩"),
    MAMBA("Mamba / SSM", "🌀"),
    HYBRID_MAMBA_TRANSFORMER("Hybride Mamba+Transformer", "🌀⚙️"),
    RNN("RNN / Recurrent", "🔁"),
    DIFFUSION("Diffusion", "🌊"),
    FLOW_MATCHING("Flow matching", "💧"),
    CONVOLUTIONAL("Convolutional", "🔲"),
    GRAPH_NEURAL("Graph neural", "🕸️"),
    UNKNOWN("Architecture inconnue", "❓"),
}

/**
 * Source des poids du modèle. Permet aux utilisateurs de filtrer selon leur
 * préférence éthique/contrôle (open source vs API-only).
 */
enum class WeightsSource(val displayName: String, val emoji: String) {
    /** Code complet + weights + dataset public et reproductible (rare). */
    OPEN_SOURCE("Open source complet", "🟢"),
    /**
     * Weights publics (Hugging Face) mais code/data partiel.
     * Llama, Mistral, Qwen, DeepSeek, Phi, Gemma, etc.
     */
    OPEN_WEIGHTS("Open weights", "🟡"),
    /** API-only, weights propriétaires. GPT-4o, Claude, Gemini, openai/o-series. */
    CLOSED_SOURCE("Propriétaire (API-only)", "🔴"),
    /** Statut indéterminé. */
    UNKNOWN("Inconnu", "❓"),
}

/**
 * Domaine d'expertise du modèle. Drive les filtres et les badges UI pour
 * orienter le user vers les modèles pertinents pour son cas d'usage.
 *
 * Un modèle peut avoir plusieurs domaines secondaires mais on en garde un
 * primaire (le plus saillant marketing-wise).
 */
enum class ModelDomain(val displayName: String, val emoji: String) {
    GENERAL("Généraliste", "🌐"),
    CODE("Code / Programmation", "💻"),
    MEDICAL("Médical", "⚕️"),
    FINANCE("Finance", "💰"),
    LEGAL("Juridique", "⚖️"),
    BIOLOGY("Biologie / Protéines", "🧬"),
    CHEMISTRY("Chimie / Molécules", "⚗️"),
    DRUG_DISCOVERY("Drug discovery", "💊"),
    AUTONOMOUS_DRIVING("Conduite autonome", "🚗"),
    WEATHER_CLIMATE("Météo / Climat", "🌦️"),
    SAFETY_MODERATION("Sécurité / Modération", "🛡️"),
    PII_DETECTION("Détection PII", "🔒"),
    OCR_DOCUMENT("OCR / Documents", "📄"),
    TRANSLATION("Traduction", "🌍"),
    CREATIVE("Créatif / Écriture", "✍️"),
    CYBERSECURITY("Cybersécurité", "🛡️"),
    SCIENTIFIC_3D("3D / Modélisation", "🧊"),
    AGRICULTURE("Agriculture", "🌾"),
    RETAIL("Retail / Commerce", "🛒"),
    GAMING("Gaming / RL", "🎮"),
    UNKNOWN("Inconnu", "❓"),
}

/**
 * Région d'origine du publisher (pas le data center). Utile pour la
 * compliance réglementaire (RGPD, restrictions export, etc.) et pour
 * informer l'utilisateur du contexte géopolitique du modèle.
 */
enum class ModelOriginRegion(val displayName: String, val flag: String) {
    US("États-Unis", "🇺🇸"),
    CHINA("Chine", "🇨🇳"),
    FRANCE("France", "🇫🇷"),
    UK("Royaume-Uni", "🇬🇧"),
    GERMANY("Allemagne", "🇩🇪"),
    ISRAEL("Israël", "🇮🇱"),
    JAPAN("Japon", "🇯🇵"),
    SOUTH_KOREA("Corée du Sud", "🇰🇷"),
    SINGAPORE("Singapour", "🇸🇬"),
    INDIA("Inde", "🇮🇳"),
    UAE("Émirats arabes unis", "🇦🇪"),
    CANADA("Canada", "🇨🇦"),
    NETHERLANDS("Pays-Bas", "🇳🇱"),
    SWITZERLAND("Suisse", "🇨🇭"),
    UNKNOWN("Origine inconnue", "🏳️"),
}

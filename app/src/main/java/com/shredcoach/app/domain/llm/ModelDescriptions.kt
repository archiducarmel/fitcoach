package com.shredcoach.app.domain.llm

/**
 * Descriptions ≤30 mots pour chaque modele LLM affiche dans le picker Playground.
 *
 * **Format** : `<finalite> · <contexte d'usage>`
 *
 * **Strategie de matching** (priorite descendante) :
 *  1. **Match exact** sur l'id complet (e.g. "meta/llama-3.3-70b-instruct")
 *     → description editorialisee pour CE modele specifique
 *  2. **Pattern famille** (e.g. "llama-3.3" → toutes les variantes)
 *     → description generique de la famille (fallback)
 *  3. **Publisher fallback** (e.g. "openai" + LANGUAGE)
 *  4. **Heuristique** sur l'id (params count, "code"/"vision"/etc.)
 *
 * **Sources** : recherche web profonde sur build.nvidia.com, HuggingFace,
 * blogs officiels publishers, fiches modeles, papers ArXiv.
 */
object ModelDescriptions {

    fun describe(id: String, publisher: String?): String? {
        // 1. Match exact en priorite (O(1) lookup)
        EXACT_MATCH[id]?.let { return it }
        EXACT_MATCH[id.lowercase()]?.let { return it }

        // 2. Fallback pattern-matching famille
        return patternFallback(id.lowercase(), publisher?.lowercase())
    }

    // ─────────────────────────────────────────────────────────────────────
    // EXACT MATCH : description individualisee par modele (~140 entrees)
    // ─────────────────────────────────────────────────────────────────────

    private val EXACT_MATCH: Map<String, String> = mapOf(
        // ═══ NVIDIA NIM — chat generique ═══════════════════════════════════
        "01-ai/yi-large" to "Multilingue 3T tokens · Raisonnement bilingue avance en anglais/chinois.",
        "abacusai/dracarys-llama-3.1-70b-instruct" to "70B finetune code · Generation code optimisee sur Llama 3.1 base.",
        "adept/fuyu-8b" to "8B multimodal leger · Vision+texte sans encodeur separe, 16K context.",
        "aisingapore/sea-lion-7b-instruct" to "7B multilingue ASEAN · 11 langues asiatiques, architecture MPT 256K vocab.",
        "ai21labs/jamba-1.5-large-instruct" to "398B MoE (94B actifs) · Hybrid Transformer-Mamba, 256K context long-range.",
        "bigcode/starcoder2-15b" to "15B code generaliste · 600+ langages, 16K window, 4T tokens code.",
        "bytedance/seed-oss-36b-instruct" to "36B generaliste · Raisonnement, 512K context, thinking budget flexible.",
        "databricks/dbrx-instruct" to "132B MoE (36B actifs) · 16 experts, 32K context, 12T tokens cures.",
        "deepseek-ai/deepseek-coder-6.7b-instruct" to "6.7B code compact · 2B tokens instruction, 78.6% HumanEval.",
        "deepseek-ai/deepseek-v4-flash" to "284B MoE (13B actifs) · 1M context, attention hybride compressee rapide.",
        "deepseek-ai/deepseek-v4-pro" to "1.6T MoE (49B actifs) · Trois modes reasoning, attention CSA/HCA avancee.",
        "meta/codellama-70b" to "70B code specialise · 1TB donnees code, 100K context, Python variant.",
        "meta/llama-3.1-70b-instruct" to "70B chat universel · 128K context, 8 langues, 39.3M GPU hours.",
        "meta/llama-3.1-8b-instruct" to "8B chat leger · 128K context, 15T tokens, multilingual dialogue.",
        "meta/llama-3.2-11b-vision-instruct" to "11B vision-langage · Image reasoning, VQA, DocVQA, 6B img-texte pairs.",
        "meta/llama-3.2-1b-instruct" to "1B ultra-compact · Edge device, 128K context, distilled from 8B/70B.",
        "meta/llama-3.2-3b-instruct" to "3B efficace edge · 9T tokens, 128K context, on-device dialogue.",
        "meta/llama-3.2-90b-vision-instruct" to "90B vision puissante · 6B img-texte pairs, adapter vision integre.",
        "meta/llama-3.3-70b-instruct" to "70B optimise texte · 15T tokens, 128K context, surpasse 3.1/3.2.",
        "meta/llama-4-maverick-17b-128e-instruct" to "17B MoE 128E (400B) · 1M context, multimodal native 5 images.",
        "meta/llama-guard-4-12b" to "12B moderation contenu · 163.8K context, texte+image, taxonomie MLCommons.",
        "meta/llama2-70b" to "70B fondation base · 2T tokens, 4K context, architecture transformer optimisee.",
        "minimaxai/minimax-m2.7" to "230B MoE (10B actifs) · 196K context, genie logiciel et productivite complexe.",
        "moonshotai/kimi-k2.6" to "1T MoE (32B actifs) · 262K context, agentic multimodal, orchestration swarm.",

        // ═══ NVIDIA NIM — Microsoft / Mistral / Google / IBM ════════════════
        "microsoft/kosmos-2" to "Comprehension multimodale · Analyse images avec extraction d'informations spatiales et detection d'objets.",
        "microsoft/phi-3-vision-128k-instruct" to "Vision compacte · OCR, tables, graphiques et comprehension visuelle legere sur dispositifs.",
        "microsoft/phi-3.5-moe-instruct" to "MoE efficient · Activation selective 6.6B/42B pour inference rapide sur taches generales.",
        "microsoft/phi-4-mini-instruct" to "Raisonnement compact 3.8B · 128K context, donnees haute qualite, instruction following.",
        "microsoft/phi-4-multimodal-instruct" to "Multimodalite omnicanale · Texte, images, audio simultanes avec traitement unifie input/output.",
        "mistralai/codestral-22b-instruct-v0.1" to "Code 22B · 80+ langages, fill-in-the-middle, refactorisation et tests automatises.",
        "mistralai/ministral-14b-instruct-2512" to "Multimodal compact 14B · 10 images, multilingue, 262K context avec encodeur vision optimise.",
        "mistralai/mistral-7b-instruct-v0.3" to "7B efficace · Instruction following, chat, code avec attention glissante et function calling.",
        "mistralai/mistral-large" to "MoE expert 675B (41B actifs) · Multimodal, function calling, 256K context.",
        "mistralai/mistral-large-2-instruct" to "Flagship dense 123B · Raisonnement avance, maths, code avec 128K context.",
        "mistralai/mistral-large-3-675b-instruct-2512" to "MoE multimodal 675B (41B actifs) · Vision, agentic, 262K context avec encodeur.",
        "mistralai/mistral-medium-3.5-128b" to "Dense 128B · Vision, coding, agents avec fenetre 256K context.",
        "mistralai/mistral-nemotron" to "Agentic 8B · Function calling, coding, workflows agents avec distillation.",
        "mistralai/mistral-small-4-119b-2603" to "MoE 119B (6.5B actifs) · Agentic, raisonnement configurable, 262K context.",
        "mistralai/mixtral-8x22b-v0.1" to "MoE 176B (39B actifs) · Multilingual, maths, code, 64K context Apache 2.0.",
        "mistralai/mixtral-8x7b-instruct-v0.1" to "MoE 56B (14B actifs) · Instruction tuning, chat, 32K context, FR/EN/IT/DE/ES.",
        "google/codegemma-1.1-7b" to "Code 7B · Fill-in-the-middle, 80% code dans training, 500B tokens synthetiques.",
        "google/codegemma-7b" to "Code completion 7B · Infilling, explication code avec 8K context.",
        "google/deplot" to "Comprehension graphiques · Conversion plot-to-text pour Q&A graphiques avec one-shot.",
        "google/gemma-2-2b-it" to "2B instruct leger · MQA, distillation, normalisation hybride pour conversation efficace.",
        "google/gemma-2b" to "Modele ultra-compact 2B · Attention glissante, knowledge distillation, edge deployment.",
        "google/gemma-3-12b-it" to "Vision 12B + 400M vision · Multilingue 140 langues, 128K context.",
        "google/gemma-3-4b-it" to "Multimodal ultra-light 4B · Matformer, 128K context, edge devices.",
        "google/gemma-3n-e2b-it" to "Efficacite extreme 6B (2B effectif) · Multimodal audio/video, 2GB VRAM edge.",
        "google/gemma-3n-e4b-it" to "Efficacite equilibree 8B (4B effectif) · Multimodal, 33K context, appareils mobiles.",
        "google/gemma-4-31b-it" to "Dense frontier 31B · Vision native, function calling, 262K context, raisonnement.",
        "google/recurrentgemma-2b" to "RNN efficace 2B · Griffin hybride, generation sequences longues, memoire constante.",
        "ibm/granite-3.0-3b-a800m-instruct" to "MoE 3B (800M actifs) · 40 experts, multilingue 12 langues, Apache 2.0.",
        "ibm/granite-3.0-8b-instruct" to "Dense 8B · GQA, RoPE, multilingue 11 langues, summarization Q&A.",
        "ibm/granite-34b-code-instruct" to "Code 34B · 116 langages, Git commits, fixing, translation, 8K context.",
        "ibm/granite-8b-code-instruct" to "Code 8B · 116 langages, 128K context, long-context generation.",
        "nv-mistralai/mistral-nemo-12b-instruct" to "NeMo 12B · 128K context, Tekken tokenizer, langues europeennes.",

        // ═══ NVIDIA NIM — Specialises (safety, embed, scientific, etc.) ═════
        "nvidia/ai-synthetic-video-detector" to "Detection videos AI · Verification authenticite contenu multimedia temps reel GPU.",
        "nvidia/cosmos-reason2-8b" to "Raisonnement physique 8B · Robotique et physique AI agents vision multimodaux.",
        "nvidia/embed-qa-4" to "Embedding Q&A dense · Retrieval augmente generative pour applications entreprise.",
        "nvidia/gliner-pii" to "Detection PII/PHI · Redaction donnees personnelles structures textes multidomaines.",
        "nvidia/ising-calibration-1-35b-a3b" to "Calibration quantique · Quantum computing AI workflows correction d'erreurs.",
        "nvidia/llama-3.1-nemoguard-8b-content-safety" to "Moderation LLM 8B · Securite dialogue humain-IA, 23 categories de risques.",
        "nvidia/llama-3.1-nemoguard-8b-topic-control" to "Controle sujets 8B · Moderation thematique dialogue humain-IA multilingue.",
        "nvidia/llama-3.1-nemotron-51b-instruct" to "Instruction chat 51B · Taches conversationnelles instruction-following.",
        "nvidia/llama-3.1-nemotron-70b-instruct" to "Chat aligne 70B · RLHF, classement ArenaHard tres eleve.",
        "nvidia/llama-3.1-nemotron-nano-8b-v1" to "Chat local 8B · Raisonnement, RAG, petit GPU RTX, efficace.",
        "nvidia/llama-3.1-nemotron-nano-vl-8b-v1" to "Vision-langage 8B · Multimodal understanding documents, images, mode GPU.",
        "nvidia/llama-3.1-nemotron-safety-guard-8b-v3" to "Safety guard 8B · Multilingue 9 langues moderation LLM/VLM.",
        "nvidia/llama-3.1-nemotron-ultra-253b-v1" to "Ultra modele 253B · Math complexe, raisonnement scientifique haute efficacite.",
        "nvidia/llama-3.2-nemoretriever-1b-vlm-embed-v1" to "Embedding multimodal 1B · Retrieval cross-modal texte/image/document.",
        "nvidia/llama-3.2-nv-embedqa-1b-v1" to "Embedding Q&A 1B · Retrieval question-reponse multilingue 26 langues.",
        "nvidia/llama-3.3-nemotron-super-49b-v1" to "Raisonnement 49B · Coding, math, chat efficace cache GPU simple.",
        "nvidia/llama-3.3-nemotron-super-49b-v1.5" to "Raisonnement 49B v1.5 · Chat, RAG, tool-calling, 131K context.",
        "nvidia/llama-nemotron-embed-1b-v2" to "Embedding dense 1B · Q&A multilingue retrieval 26 langues, documents longs.",
        "nvidia/llama-nemotron-embed-vl-1b-v2" to "Embedding vision-langage 1B · Multimodal RAG texte/image/document.",
        "nvidia/llama3-chatqa-1.5-70b" to "Chat-Q&A retrieval 70B · Dialogue RAG, conversations questions-reponses.",
        "nvidia/mistral-nemo-minitron-8b-8k-instruct" to "Chat distille 8B · Instruction-following, pruned Mistral NeMo efficace.",
        "nvidia/nemoretriever-parse" to "Parser documents VLM · Extraction texte, OCR, tableaux dans images/PDF.",
        "nvidia/nemotron-3-content-safety" to "Content safety compact · Multimodal multilingue 12 langues moderation.",
        "nvidia/nemotron-3-nano-30b-a3b" to "Agents 30B MoE · 1M context, agentic RAG, workflows complexes.",
        "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning" to "Omni multimodal 30B · Texte, image, video, audio, agents unifies.",
        "nvidia/nemotron-3-super-120b-a12b" to "Raisonnement 120B MoE · 1M context, agents multi-step, tool-use.",
        "nvidia/nemotron-4-340b-instruct" to "Instruction 340B ultra · Donnees synthetiques, chat multi-tour, alignment.",
        "nvidia/nemotron-4-340b-reward" to "Reward model 340B · Helpfulness, correctness, coherence pour preference.",
        "nvidia/nemotron-content-safety-reasoning-4b" to "Safety reasoning 4B · Politique securite adaptable, dialog moderation.",
        "nvidia/nemotron-mini-4b-instruct" to "Chat roleplay 4B · RAG, Q&A, function-calling sur GPU 2GB.",
        "nvidia/nemotron-nano-12b-v2-vl" to "Vision-doc 12B · Documents multi-images, resume, VQA.",
        "nvidia/nemotron-nano-3-30b-a3b" to "Agents 30B hybride · 1M context, agentic RAG, workflows complexes.",
        "nvidia/nemotron-parse" to "Parser OCR documents · Extraction texte, tables, images dans PDF.",
        "nvidia/neva-22b" to "Vision-langage 22B · Multimodal chat images, instruction-following.",
        "nvidia/nv-embed-v1" to "Embedding 4K · MTEB #1 retrieval, classification, clustering.",
        "nvidia/nv-embedcode-7b-v1" to "Embedding code 7B · Retrieval texte-code hybride programmation.",
        "nvidia/nv-embedqa-e5-v5" to "Embedding Q&A 1K · Question-reponse retrieval dense 512 tokens.",
        "nvidia/nv-embedqa-mistral-7b-v2" to "Embedding Mistral 4K · Q&A retrieval MTEB #1 dense.",
        "nvidia/nvclip" to "Vision-langage multimodal · Zero-shot classification, search image-texte.",
        "nvidia/nvidia-nemotron-nano-9b-v2" to "Chat-vision 9B hybride · Raisonnement texte+image, 131K context.",
        "nvidia/riva-translate-4b-instruct" to "Traduction 4B · Neural machine translation, 12 langues.",
        "nvidia/riva-translate-4b-instruct-v1.1" to "Traduction amelioree 4B · NMT 12 langues, benchmark FLORES.",
        "nvidia/vila" to "Vision-langage edge/cloud · Multi-image, video, quantization, Jetson.",

        // ═══ NVIDIA NIM — Qwen, OpenAI gpt-oss, Sarvam, etc. ════════════════
        "openai/gpt-oss-120b" to "Open-weights 117B (5.1B actifs MoE) · Reasoning, agentic, function calling, tres rapide Groq.",
        "openai/gpt-oss-20b" to "Open-weights 21B (3.6B actifs MoE) · Performance proche o3-mini, tourne sur 16GB RAM.",
        "qwen/qwen3-coder-480b-a35b-instruct" to "Code MoE 480B (35B actifs) · 262K context, 70% ratio code training.",
        "qwen/qwen3-next-80b-a3b-instruct" to "Hybrid linear-MoE 80B (3B actifs) · 262K natif/1M etendu, 10x throughput.",
        "qwen/qwen3.5-122b-a10b" to "Vision MoE 122B (10B actifs) · Texte+image+video, 262K context (fev 2026).",
        "qwen/qwen3.5-397b-a17b" to "Flagship multimodal MoE 397B (17B actifs) · 262K natif/1M etendu hybride.",
        "sarvamai/sarvam-m" to "Indic 24B bilingue · Math +21.6%, Hindi et 11 langues indiennes.",
        "snowflake/arctic-embed-l" to "Embedding 568M · Matryoshka, 8K context, retrieval dense haute qualite.",
        "stepfun-ai/step-3.5-flash" to "MoE 196B (11B actifs) · Code ultra-rapide, 256K context, 100-350 tokens/s.",
        "stockmark/stockmark-2-100b-instruct" to "Japonais business 96B · 32K context, documents complexes, bilingue JP/EN.",
        "upstage/solar-10.7b-instruct" to "Upscaling depth 10.7B · Depasse Mixtral 8x7B, instruction-tuning.",
        "writer/palmyra-creative-122b" to "Creatif narratif 122B Llama3.1 · Poesie, scriptwriting, dialogue coherent.",
        "writer/palmyra-fin-70b-32k" to "Finance 70B 32K · 100% needle-in-haystack, CFA III 73%.",
        "writer/palmyra-med-70b" to "Biomedical 70B 8K · 85.87% benchmarks, EHR, notes cliniques.",
        "writer/palmyra-med-70b-32k" to "Medical long-context 70B 32K · 85.87% biomedical, resumes discharge avances.",
        "z-ai/glm-5.1" to "Agentic coding 754B MoE hybride · 256B+1S experts, 8h autonomie, SWE 58.4.",
        "zyphra/zamba2-7b-instruct" to "Hybrid SSM-attention 7B · Mamba2+attention, 25% plus rapide qu'un Transformer 7B.",
        "baai/bge-m3" to "Embedding multi-fonction 100+ langues · Dense, multi-vector, sparse retrieval, 8K.",

        // ═══ GITHUB MODELS ═══════════════════════════════════════════════════
        "ai21-labs/ai21-jamba-1.5-large" to "Hybride SSM-Transformer 398B (94B actifs) · 256K context pour documents complexes.",
        "cohere/cohere-command-a" to "Generaliste 111B · 256K context, 23 langues, tool use avance.",
        "cohere/cohere-command-r-08-2024" to "Chat outille 32B · 128K context, 23 langues, 50% plus rapide.",
        "cohere/cohere-command-r-plus-08-2024" to "RAG cite sources 104B · 128K context, grounding avance, enterprise.",
        "deepseek/deepseek-r1" to "Reasoning MoE 671B (37B actifs) · RL avance, 70% AIME 2024.",
        "deepseek/deepseek-r1-0528" to "Reasoning ameliore mai 2025 · 163K context, 87.5% AIME 2025.",
        "deepseek/deepseek-v3-0324" to "MoE hybrid reasoning 671B (37B actifs) · MLA, 163K context, mars 2025.",
        "microsoft/mai-ds-r1" to "Reasoning securise · DeepSeek-R1 post-traine Microsoft, 99.3% sur sujets bloques.",
        "microsoft/phi-4" to "Reasoning STEM compact 14B · Math avance, coding, raisonnement logique.",
        "microsoft/phi-4-mini-reasoning" to "Reasoning math compact 3.8B · 128K context, 1.4M questions STEM.",
        "microsoft/phi-4-reasoning" to "Reasoning STEM dense 14B · Math avance, coding, proof generation.",
        "mistral-ai/codestral-2501" to "Code 22B · 256K context, 80+ langages, 86.6% HumanEval.",
        "mistral-ai/ministral-3b" to "Vision-langage compact 3.8B · 256K context, edge deployment.",
        "mistral-ai/mistral-medium-2505" to "Frontier reasoning multimodal · 128K context, texte+image avance.",
        "mistral-ai/mistral-small-2503" to "Dense 24B multimodal · 128K context, vision integree (mars 2025).",
        "openai/gpt-4.1" to "Instruction-suivi avance 1M tokens · 1.04M context, 87.4% IFEval, coding superieur.",
        "openai/gpt-4.1-mini" to "Mini rapide 1M tokens · 1.04M context, instruction-suivi efficace.",
        "openai/gpt-4.1-nano" to "Ultra-leger classification · 1.04M tokens, 80.1% MMLU, latence minimale.",
        "openai/gpt-4o" to "Omnimodal etat-de-l'art · Texte, audio, image, video, reponse en 232ms.",
        "openai/gpt-4o-mini" to "Compact multimodal · 128K context, 82% MMLU, fine-tuning optimise.",
        "openai/gpt-5" to "Flagship reasoning 1M tokens · GPT-5.5 (avr 2026), 82.7% Terminal-Bench.",
        "openai/gpt-5-chat" to "Conversation GPT-5 · Raisonnement multi-niveaux, agents avances.",
        "openai/gpt-5-mini" to "Reasoning rapide 400K tokens · 400K context, 128K output, efficacite cout.",
        "openai/gpt-5-nano" to "Ultra-rapide 400K · 400K tokens, latence minimale, classification.",
        "openai/o1" to "Reasoning profond chaining · 200K context, RL avance, mathematiques.",
        "openai/o1-mini" to "Reasoning STEM 128K · Coding competitive (1650 Elo), 80% moins cher.",
        "openai/o1-preview" to "Reasoning recherche 200K · 100K output, pensee complexe (sans vision).",
        "openai/o3" to "Reasoning ultra-avance avril 2025 · Web, fichiers, vision, 3x ARC-AGI vs o1.",
        "openai/o3-mini" to "Reasoning efficace 200K · 87.3% AIME, 79.7% GPQA avec effort moyen.",
        "openai/o4-mini" to "Reasoning rapide vision (avr 2025) · 200K context, 20% mieux que o3-mini.",
        "openai/text-embedding-3-large" to "Embedding multilingue 3072 dim · 64.6% MTEB, 8K tokens.",
        "openai/text-embedding-3-small" to "Embedding compact 1536 dim · Matryoshka learning, 8K tokens.",
        "xai/grok-3" to "Reasoning agentic multimodal · 1M tokens, 200K H100s, reasoning minutes.",
        "xai/grok-3-mini" to "Reasoning lean 70B · 131K context, 85% performance, 4-6x latence.",

        // ═══ GEMINI (Google API directe) ═══════════════════════════════════
        "gemini-2.5-flash" to "Raisonnement rapide multimodal · Taches a haut volume avec thinking, agents production.",
        "gemini-3-flash-preview" to "Raisonnement agentic haute vitesse · Workflows interactifs, assistants multi-tours, coding iteratif.",
        "gemini-3.5-flash" to "IA frontiere 4x plus rapide · Agents complexes, coding avance, processus entreprise multi-semaines.",
        "gemini-2.0-flash" to "Multimodal natif images/audio · Taches complexes, generation d'images, TTS multilingue.",

        // ═══ GROQ (variantes hosted sur LPU) ═══════════════════════════════
        "meta-llama/llama-4-scout-17b-16e-instruct" to "Scout MoE 17B sur Groq LPU · Raisonnement rapide multimodal, ultra-basse latence.",
        "llama-3.3-70b-versatile" to "Llama 3.3 70B versatile Groq · Coding, raisonnement avance, 276 tokens/sec sur LPU.",

        // ═══ MISTRAL API DIRECTE ═══════════════════════════════════════════
        "mistral-small-latest" to "Prototypage economique polyvalent · Multilingue, function calling, rapport qualite-prix optimal.",
        "pixtral-large-latest" to "Vision 124B multimodal · Documents/graphiques, 30 images, raisonnement math visuel.",

        // ═══ ANTHROPIC CLAUDE (API directe) ════════════════════════════════
        "claude-sonnet-4-20250514" to "Claude Sonnet 4 equilibre · Coding SWE-bench 72%, outils, vision, raisonnement etendu.",
        "claude-haiku-4-20250514" to "Claude Haiku 4 ultrarapide · 4-5x plus rapide que Sonnet, agents paralleles, 98.5 tok/s.",
        "claude-opus-4-20250514" to "Claude Opus 4 expert · Problemes complexes multi-heures, SWE-bench 72%, terminal 43%.",

        // ═══ POLLINATIONS (text-to-image gratuit, no auth) ═════════════════
        "flux" to "FLUX Pollinations gratuit · Texte-to-image haute fidelite, API publique sans authentification.",
        "turbo" to "Turbo Pollinations rapide · Iterations rapides, prototypage, equilibre performance/qualite.",
        "kontext" to "Kontext Pollinations img2img · Edition contextuelle FLUX, transformations avec texte guidant.",
        "gptimage" to "GPTImage Pollinations premium · Dimensions 64-2048px, controle seed, generation multi-images.",

        // ═══ CLOUDFLARE WORKERS AI (image gen) ═════════════════════════════
        "@cf/black-forest-labs/flux-1-schnell" to "FLUX 1 Schnell Cloudflare · Texte-image photorealiste rapide, 1024px, edge Workers.",
        "@cf/bytedance/stable-diffusion-xl-lightning" to "SDXL Lightning Cloudflare · 2 steps ultra-rapides, haute qualite low-latency Workers.",
        "@cf/stabilityai/stable-diffusion-xl-base-1.0" to "SDXL Base Cloudflare · Photorealisme natif 1024x1024, couleurs vibrantes, contraste.",
        "@cf/lykon/dreamshaper-8-lcm" to "DreamShaper 8 LCM · Photorealistic, 5-15 steps optimises pour production rapide.",
        "@cf/runwayml/stable-diffusion-v1-5-img2img" to "SD 1.5 img2img Cloudflare · Transformation et edition creative, force ajustable 0-1.",
        "@cf/black-forest-labs/flux-2-klein-9b" to "FLUX 2 Klein 9B multimodal · 4 steps <0.5s, edition multi-images, input <=512x512.",
    )

    // ─────────────────────────────────────────────────────────────────────
    // PATTERN FALLBACK : famille / publisher / heuristique
    // (utilise si EXACT_MATCH ne contient pas l'id, e.g. nouveau modele)
    // ─────────────────────────────────────────────────────────────────────

    private fun patternFallback(lower: String, pub: String?): String? = when {
        // Reasoning models
        lower.contains("nemotron-ultra") -> "Modele NVIDIA Nemotron Ultra · Entreprise top-tier pour agents complexes et reasoning."
        lower.contains("nemotron-super") -> "Modele NVIDIA Nemotron Super · Reasoning et chat assistant haut de gamme."
        lower.contains("nemotron-nano") -> "Modele NVIDIA Nemotron Nano · Petit modele rapide edge/embarque."
        lower.contains("nemotron") -> "Famille NVIDIA Nemotron · Modeles entreprise reasoning et agents sur GPU NVIDIA."
        lower.contains("nemoguard") -> "NVIDIA NemoGuard · Safety/moderation pour pipelines LLM securises."
        lower.contains("magpie") -> "NVIDIA Magpie TTS multilingue · Voix naturelles pour voice agents."
        lower.contains("parakeet") -> "NVIDIA Parakeet STT FastConformer · Transcription audio voice agents."
        lower.contains("canary") -> "NVIDIA Canary STT · Reconnaissance vocale multilingue avec traduction."
        lower.contains("alphafold") || lower.contains("rfdiffusion") || lower.contains("proteinmpnn") ->
            "Modele scientifique NVIDIA · Biologie computationnelle proteines/molecules."
        lower.contains("diffdock") || lower.contains("molmim") || lower.contains("genmol") ->
            "Modele NVIDIA drug discovery · Docking, generation molecules, design medicaments."
        lower.contains("cuopt") -> "NVIDIA cuOpt · Optimisation logistique, routage vehicules a grande echelle."
        lower.contains("fourcastnet") || lower.contains("corrdiff") ->
            "Modele NVIDIA prediction climat/meteo · Simulation atmospherique haute resolution."

        // Famille generic
        lower.contains("llama-4") -> "Meta Llama 4 · MoE multimodal generation 2025 pour chat et agents."
        lower.contains("llama-3") -> "Meta Llama 3 · Famille open-weights pour chat polyvalent multilingue."
        lower.contains("gemini") -> "Google Gemini · Multimodal long-contexte avec reasoning."
        lower.contains("gemma") -> "Google Gemma · Open-weights performant multilingue."
        lower.contains("claude") -> "Anthropic Claude · Chat IA safety-first, reasoning et coding excellents."
        lower.contains("mistral") || lower.contains("mixtral") -> "Mistral AI · Open-weights europeen, multilingue, function calling."
        lower.contains("qwen") -> "Alibaba Qwen · Multilingue (29 langues), code, math performants."
        lower.contains("phi") -> "Microsoft Phi · Petit modele excellent en raisonnement (math/sciences)."

        // Publisher fallback
        pub == "meta" || pub == "meta-llama" -> "Modele Meta · Open-weights Llama, chat polyvalent multilingue."
        pub == "openai" -> "Modele OpenAI · Chat IA generaliste reference du marche."
        pub == "anthropic" -> "Modele Anthropic Claude · Safety-first avec reasoning excellent."
        pub == "google" -> "Modele Google · Multimodal famille Gemini/Gemma."
        pub == "microsoft" -> "Modele Microsoft · Phi (raisonnement compact) ou Azure-hosted."
        pub == "mistralai" || pub == "mistral-ai" -> "Modele Mistral AI · Europeen open-weights, multilingue."
        pub == "deepseek-ai" -> "Modele DeepSeek · Open-weights chinois, code et reasoning excellents."
        pub == "cohere" -> "Modele Cohere · Enterprise RAG-natif avec citations sources."
        pub == "nvidia" || pub == "nv-mistralai" -> "Modele NVIDIA · Optimise GPU NVIDIA pour deploiement entreprise."
        pub == "ibm" -> "Modele IBM Granite · Enterprise Apache 2.0 avec compliance."
        pub == "xai" -> "Modele xAI Grok · Conversational avec acces temps reel."
        pub == "ai21labs" || pub == "ai21-labs" -> "AI21 Jamba · Mamba+Transformer hybride pour long contexte."
        pub == "01-ai" -> "01.AI Yi · Bilingue chinois/anglais open-weights."
        pub == "moonshotai" -> "Moonshot AI Kimi · Open-weights chinois reasoning long contexte."
        pub == "minimaxai" -> "MiniMax · Open-weights chinois multimodal."
        pub == "z-ai" -> "Z.ai (GLM/Zhipu) · Open-weights chinois reasoning et agents."
        pub == "stepfun-ai" -> "StepFun · Open-weights chinois multimodal."
        pub == "writer" -> "Writer Palmyra · Specialise contenu (creative/finance/medical) selon variante."
        pub == "snowflake" -> "Snowflake Arctic · Enterprise data-warehouse natif."
        pub == "databricks" -> "Databricks DBRX · Open-weights MoE enterprise code et reasoning."
        pub == "huggingface" -> "Modele HuggingFace community · Disponible sur le Hub open-source."
        pub == "abacusai" -> "AbacusAI · Enterprise AI platform."
        pub == "aisingapore" -> "AI Singapore SEA-LION · Langues d'Asie du Sud-Est."
        pub == "tiiuae" -> "TII (UAE) Falcon · Open-weights arabe/anglais."
        pub == "bigcode" -> "BigCode · Open-source code generation (StarCoder famille)."
        pub == "adept" -> "Adept · Multimodal et agent specialise navigation web."
        pub == "stockmark" -> "Stockmark · Japonais bilingue business documents."
        pub == "upstage" -> "Upstage SOLAR · Coreen, dense upscaling depth."
        pub == "sarvamai" -> "Sarvam AI · Specialise langues indiennes (Hindi + 10 autres)."
        pub == "zyphra" -> "Zyphra Zamba · Hybride SSM-attention efficace."
        pub == "bytedance" -> "ByteDance · Open-weights chinois reasoning long contexte."
        pub == "baai" -> "BAAI · Embeddings/retrieval haute qualite multilingue."

        // Heuristique sur l'id
        lower.contains("embed") -> "Modele d'embeddings · RAG, recherche semantique, clustering."
        lower.contains("rerank") -> "Modele reranker · Amelioration pertinence en pipeline RAG."
        lower.contains("whisper") || lower.contains("asr") -> "Modele speech-to-text · Transcription audio en texte."
        lower.contains("tts") -> "Modele text-to-speech · Synthese vocale a partir de texte."
        lower.contains("code") || lower.contains("starcoder") -> "Modele code · Generation, completion et debug multi-langages."
        lower.contains("vision") || lower.contains("-vl-") || lower.contains("vlm") ->
            "Modele Vision-Language · Analyse d'images et chat multimodal."
        lower.contains("guard") || lower.contains("safety") -> "Modele safety/moderation · Pipeline LLM securise."

        else -> null
    }
}

package com.shredcoach.app.domain.llm

/**
 * Descriptions ≤30 mots pour chaque famille de modeles LLM affichee dans le
 * picker Playground. Recherche basee sur les fiches officielles HuggingFace,
 * blogs des publishers (Meta, OpenAI, Anthropic, Mistral, etc.) et docs
 * comparatives independantes (DataCamp, Galaxy.ai, ArtificialAnalysis).
 *
 * **Format** : `<finalite> · <contexte d'usage recommande>`
 *
 * **Strategie de matching** (priorite descendante) :
 *  1. Match exact sur l'id complet (e.g. "meta/llama-3.3-70b-instruct")
 *  2. Match sur le pattern famille (e.g. "llama-3.3" → toutes les variantes)
 *  3. Match sur le publisher + type (e.g. "openai" + LANGUAGE)
 *  4. Fallback heuristique base sur l'id (params count, "code"/"vision"/etc.)
 */
object ModelDescriptions {

    /**
     * Retourne une description ≤30 mots ou null si aucun match.
     * Caller utilise ?: ancienne_description pour fallback API summary.
     */
    fun describe(id: String, publisher: String?): String? {
        val lower = id.lowercase()
        val pub = publisher?.lowercase()

        // ─── Pattern matching priorite par famille ──────────────────────────
        return when {
            // META LLAMA
            lower.contains("llama-4-maverick") -> "Modele agentique MoE 17B/400B. Excellent pour orchestration d'outils, automatisation complexe et workflows multi-etapes avec raisonnement long."
            lower.contains("llama-4-scout") -> "MoE 17B/109B avec vision multimodale. Ideal chat vision economique, comprehension d'images et conversations grand contexte (10M tokens)."
            lower.contains("llama-3.3-70b") -> "Modele instruct multilingue 70B. Excellent chat, generation code, raisonnement et extraction structuree au cout d'un modele 70B avec qualite proche du 405B."
            lower.contains("llama-3.2") && lower.contains("vision") -> "Vision-Language model 11B/90B. Analyse d'images, OCR, comprehension de graphiques et chat multimodal en 8 langues."
            lower.contains("llama-3.2") && (lower.contains("1b") || lower.contains("3b")) -> "Petit modele edge optimise mobile/IoT. Tres economique pour chat basique, classification et resumes courts en environnement contraint."
            lower.contains("llama-3.1-405b") -> "Plus gros modele open-weights Meta (405B dense). Reasoning complexe, ecriture longue, recherche academique. Coute cher en inference."
            lower.contains("llama-3.1-70b") -> "Modele 70B equilibre performances/cout. Chat polyvalent multilingue, code, instruction-following, agentique de base."
            lower.contains("llama-3.1-8b") -> "Petit modele 8B rapide et economique. Chat simple, classification, extraction et tasks legeres a haut volume."

            // OPENAI
            lower.contains("gpt-5") -> "Frontier model OpenAI flagship 2025. Reasoning expert, qualite maximale pour problemes complexes critiques. Acces gated Copilot Pro+."
            lower.contains("/o4") || lower.contains("openai/o4") -> "Reasoning model 2026 successor o3. Math/code/sciences avec chain-of-thought etendu. Acces gated Copilot Pro+."
            lower.contains("/o3") || lower.contains("openai/o3") -> "Reasoning model OpenAI dedie au raisonnement chain-of-thought. Excellent en math, code, sciences et problemes complexes. Acces gated."
            lower.contains("/o1") || lower.contains("openai/o1") -> "Premier reasoning model OpenAI (2024). Thinking etendu avant reponse pour problemes complexes. Lent mais precis sur math/code."
            lower.contains("gpt-oss-120b") -> "Modele open-weights OpenAI 117B (MoE 5.1B actifs). Reasoning, agentique, function calling. Tres rapide sur Groq, performances proches o4-mini."
            lower.contains("gpt-oss-20b") -> "Modele open-weights OpenAI 21B (MoE 3.6B actifs). Performance proche de o3-mini. Tourne sur 16GB RAM, ideal edge et inference locale rapide."
            lower.contains("gpt-4.1-nano") -> "Plus petit modele 4.1 OpenAI. Ultra-economique pour classification, extraction simple et taches a tres haut volume avec latence minimale."
            lower.contains("gpt-4.1-mini") -> "Modele 4.1 mini. Equilibre tres bon rapport qualite/cout. Chat assistant, automation, content moderation, agents legers."
            lower.contains("gpt-4.1") -> "Generation 4.1 OpenAI 2025. Tres bon raisonnement multimodal, coding agent, instruction-following. Bon defaut pour la plupart des cas."
            lower.contains("gpt-4o-mini") -> "Petit modele multimodal economique. Chat, classification, extraction donnees structurees, customer support a haut volume avec latence minimale."
            lower.contains("gpt-4o") -> "Modele multimodal phare OpenAI. Vision + texte avec qualite maximale. Genere du contenu nuance, analyses complexes, support entreprise (finance/sante)."
            lower.contains("gpt-3.5-turbo") -> "Modele legacy economique. Chat basique, prototypes rapides. Deprecie au profit de gpt-4o-mini/gpt-4.1-nano plus performants au meme prix."
            lower.contains("text-embedding-3-large") -> "Embeddings 3072 dimensions OpenAI haute qualite. Recherche semantique, RAG, clustering pour applications de production exigeantes."
            lower.contains("text-embedding-3-small") -> "Embeddings 1536 dimensions economiques. RAG basique, recherche semantique a haut volume avec bon rapport qualite/cout."
            lower.contains("text-embedding-ada") -> "Embeddings legacy OpenAI. Toujours fonctionnels mais remplaces par text-embedding-3 (qualite et cout meilleurs)."
            lower.contains("whisper") -> "Modele speech-to-text OpenAI multilingue. Transcription audio/podcast, sous-titres, voice agents. Robuste sur audio bruite et accents."
            lower.contains("tts-1-hd") -> "Text-to-speech haute fidelite OpenAI. Voix naturelles pour podcasts, audiobooks, voice agents qualitatifs (latence ~1s)."
            lower.contains("/tts-1") || lower.endsWith("tts-1") -> "Text-to-speech temps reel OpenAI. Latence minimale (~300ms) pour voice agents interactifs et applications conversationnelles."
            lower.contains("dall-e-3") -> "Generateur d'images OpenAI haute qualite. Suivi d'instructions precis, styles varies. Pour illustrations marketing et prototypes visuels."

            // ANTHROPIC CLAUDE
            lower.contains("claude-opus-4.7") || lower.contains("claude-opus-4-7") -> "Plus puissant modele Anthropic 2026. Coding agentique cross-file, planification long-terme. Pour taches critiques exigeant fiabilite maximale."
            lower.contains("claude-opus") -> "Modele Anthropic le plus capable. Raisonnement complexe, agentique long-terme, analyse documents longs. Le plus cher de la gamme."
            lower.contains("claude-sonnet") -> "Equilibre performance/cout. Defaut Anthropic recommande : developpement, ecriture, analyse, agents, code review, support 1M tokens."
            lower.contains("claude-haiku-4.5") || lower.contains("claude-haiku-4-5") -> "Modele Anthropic rapide et abordable. Classification, resumes courts, batch processing a haut volume. Latence minimale."
            lower.contains("claude-haiku") -> "Modele Anthropic le plus rapide. Classification, customer support, taches simples a haute concurrence et faible cout."

            // MICROSOFT PHI
            lower.contains("phi-4-multimodal") -> "Phi-4 multimodal 5.6B. Voix + images + texte pour assistants intelligents on-device, traduction temps reel et photo analysis."
            lower.contains("phi-4-mini") -> "Phi-4 mini 3.8B compact. Text-only, contexte 128K. Edge devices, inference locale et tasks specialisees a faible cout."
            lower.contains("phi-4") -> "Modele 14B Microsoft specialise raisonnement complexe (math/sciences). Petits parametres mais performances proches modeles plus gros."
            lower.contains("phi-3.5-moe") -> "Phi-3.5 MoE 42B/6.6B actifs. Tres efficace pour chat assistant qualitatif a cout modere."
            lower.contains("phi-3-medium") -> "Phi-3 medium 14B. Raisonnement et chat polyvalent pour environnements compute-constrained avec bon rapport qualite/poids."
            lower.contains("phi-3-mini") -> "Phi-3 mini 3.8B compact. Edge devices, smartphones, IoT. Chat basique et instructions avec contexte 128K."

            // MISTRAL
            lower.contains("codestral-embed") -> "Embeddings code Mistral pour RAG/recherche dans bases de code, completion contextuelle et navigation codebase intelligente."
            lower.contains("codestral") -> "Modele code Mistral 22B sur 80+ langages. Code completion temps reel, fill-in-the-middle, refactoring et tests automatiques."
            lower.contains("mistral-large") -> "Plus gros modele Mistral. Raisonnement avance, function calling natif, JSON structure. Pour agents enterprise critiques."
            lower.contains("mistral-medium") -> "Modele Mistral medium 2025. Bon equilibre performance/cout pour chat assistant et agents en production avec function calling."
            lower.contains("mistral-small") -> "Modele Mistral small. Economique pour chat, classification, extraction. Function calling et JSON natifs."
            lower.contains("mixtral-8x22b") -> "MoE 141B/39B actifs Apache 2.0. Reasoning, code, math. Plus rapide que dense 70B avec meilleure qualite. Recall precis sur 64K tokens."
            lower.contains("mixtral-8x7b") -> "MoE 47B/13B actifs original. Bon multilingue (FR/EN/IT/DE/ES) et code. Apache 2.0, base de nombreuses applications open-source."
            lower.contains("ministral") -> "Modele Mistral edge 3B/8B. Smartphones, embarque, IoT. Latence minimale pour usage on-device."

            // DEEPSEEK
            lower.contains("deepseek-v4-pro") -> "DeepSeek V4 Pro reasoning MoE 1.6T. Top modele open-weights pour math/code/sciences complexes. Thinking etendu obligatoire."
            lower.contains("deepseek-v4-flash") -> "DeepSeek V4 Flash MoE 284B. Reasoning rapide pour problemes math/code/logique avec chain-of-thought etendu."
            lower.contains("deepseek-r1") -> "Reasoning model open-weights MoE 671B/37B actifs. Chain-of-thought avant reponse pour math, debug, analyse financiere et code complexe."
            lower.contains("deepseek-v3") -> "Modele DeepSeek V3 conversation et creativite. Pas de reasoning explicite : ideal ecriture, contenu creatif et questions factuelles fluides."
            lower.contains("deepseek-coder") -> "Modele code dedie DeepSeek. Generation, completion, debugging multilingue (Python/JS/Java/C++/etc.) avec bon raisonnement algorithmique."

            // GOOGLE GEMINI / GEMMA
            lower.contains("gemini-3") -> "Gemini 3 frontier 2026. Multimodal (texte/image/audio/video), reasoning expert, action agentique. Pour applications complexes."
            lower.contains("gemini-2.5-flash") -> "Modele multimodal Google rapide. Texte/image/audio/video + tool use + raisonnement avec budget thinking. Defaut Gemini recommande."
            lower.contains("gemini-2.0-flash") -> "Gemini 2.0 Flash legacy ultra-rapide. Chat economique multimodal pour applications a haute frequence et latence critique."
            lower.contains("gemini-1.5-pro") -> "Gemini 1.5 Pro long-contexte (1-2M tokens). Analyse documents tres longs, video, codebase complete. Remplace par 2.5 Pro/Flash."
            lower.contains("gemma-3-27b") -> "Open model Google 27B. Texte+image, 140+ langues, contexte 128K. Tres economique pour deploiement self-hosted polyvalent."
            lower.contains("gemma-3-12b") -> "Open model Google 12B. Multilingue 140 langues, vision. Bon equilibre qualite/taille pour self-hosting moderne."
            lower.contains("gemma-3") -> "Famille Gemma 3 open-weights 1B/4B/12B/27B. Multilingue 140 langues, vision, contexte 128K. Apache-style licence permissive."
            lower.contains("gemma-2-27b") -> "Open model Google 27B (2024). Chat conversationnel et code. Apache-style licence. Remplace par Gemma 3."
            lower.contains("gemma-2-9b") -> "Open model Google 9B (2024). Polyvalent et economique pour self-hosting. Remplace par Gemma 3 plus capable."
            lower.contains("gemma-2") -> "Famille Gemma 2 open-weights Google (2024). Chat, code, multilingue. Remplace par Gemma 3 multimodal."

            // QWEN
            lower.contains("qwen3-coder") -> "Qwen3 Coder agentique 80B/3B actifs MoE. Tool calling RL-trained, contexte 256K-1M. Niveau Claude Sonnet 4 pour debug, browser-use et API integrations."
            lower.contains("qwen3") -> "Qwen3 (Alibaba 2025). Reasoning, multilingue (29 langues), tool calling. Famille 0.6B-235B avec MoE pour grands modeles."
            lower.contains("qwen2.5-coder") -> "Qwen 2.5 Coder. Code generation/repair/reasoning sur 40+ langages. Performances proches GPT-4o en coding open-source."
            lower.contains("qwen2.5") -> "Qwen 2.5 (Alibaba). Famille 0.5B-72B. Chat multilingue (29 langues), code, math, instruction-following. Bon defaut open-source."
            lower.contains("qwen-vl") -> "Qwen Vision-Language. OCR, comprehension graphiques, charts, documents et chat multimodal. Performances etat-de-l'art open-source."
            lower.startsWith("qwen") -> "Modele Qwen Alibaba multilingue. Chat, code, math en 29 langues. Famille open-weights performante."

            // COHERE
            lower.contains("command-r-plus") -> "Cohere Command R+ enterprise. RAG, tool use et workflows agents avec citation sources. Optimise pour search et knowledge-base assistants."
            lower.contains("command-r") -> "Cohere Command R. RAG natif avec citations sources. Economique pour search/Q&A enterprise et chatbots knowledge-base."
            lower.contains("command") -> "Modele Cohere enterprise. RAG et tool use natifs avec citations. Pour assistants entreprise et search semantique."

            // AI21 JAMBA
            lower.contains("jamba-1.5-large") -> "AI21 SSM-Transformer hybride 398B/94B actifs. Contexte 256K. Analyse financiere, documents legaux longs, raisonnement RAG. 2.5x plus rapide qu'un dense equivalent."
            lower.contains("jamba-1.5-mini") -> "AI21 SSM-Transformer 52B/12B actifs. Contexte 256K. Customer support, resumes documents longs. Function calling et JSON natifs."
            lower.contains("jamba") -> "Famille Jamba AI21 (SSM-Mamba + Transformer hybride). Long contexte (256K-1M) tres efficace. Apache 2.0."

            // NVIDIA NEMOTRON / MAGPIE / PARAKEET
            lower.contains("nemotron-ultra") -> "NVIDIA Nemotron Ultra. Modele entreprise top-tier pour agents complexes, reasoning et orchestration multi-outils."
            lower.contains("nemotron-super") -> "NVIDIA Nemotron Super. Reasoning et chat assistant haut de gamme avec function calling robuste."
            lower.contains("nemotron-nano") -> "NVIDIA Nemotron Nano. Petit modele rapide pour chat embarque et edge inference."
            lower.contains("nemotron") -> "Famille NVIDIA Nemotron. Modeles entreprise pour agents, reasoning et workflows production sur GPU NVIDIA."
            lower.contains("nemoguard") -> "NVIDIA NemoGuard safety/moderation. Detection PII, jailbreak, contenu toxique pour pipelines LLM securises."
            lower.contains("magpie") -> "NVIDIA Magpie TTS multilingue (9 langues). Voix naturelles pour voice agents, podcasts et lecture audio (En/Es/Fr/De/It/Zh/Ja/Hi/Vi)."
            lower.contains("parakeet") -> "NVIDIA Parakeet STT 600M FastConformer. Transcription audio jusqu'a 24min, voice agents, sous-titres, conversational AI."
            lower.contains("canary") -> "NVIDIA Canary STT multilingue. Reconnaissance vocale + traduction pour audio 4 langues (En/Es/Fr/De)."
            lower.contains("nvclip") -> "NVIDIA NV-CLIP embeddings multimodaux. Recherche texte<>image et image<>image pour catalogues visuels et moderation contenu."
            lower.contains("nvidia/embed") -> "Embeddings NVIDIA optimises GPU. RAG, recherche semantique multilingue pour applications a haut throughput."
            lower.contains("alphafold") || lower.contains("rfdiffusion") || lower.contains("proteinmpnn") -> "Modele scientifique NVIDIA. Biologie computationnelle : structure proteines, drug discovery, design moleculaire."
            lower.contains("diffdock") || lower.contains("molmim") || lower.contains("genmol") -> "Modele drug discovery NVIDIA. Docking moleculaire, generation molecules, design medicaments. Pour pharma et recherche."
            lower.contains("cuopt") -> "NVIDIA cuOpt optimisation logistique. Routage vehicules (VRP), supply chain, planification operations a grande echelle."
            lower.contains("fourcastnet") || lower.contains("corrdiff") -> "Modele NVIDIA prediction climat/meteo. Simulation atmospherique haute resolution pour previsions et changement climatique."

            // 01-AI / Yi
            lower.contains("yi-large") -> "01.AI Yi Large. Modele chinois multilingue (En/Zh) bilingue performant. Alternative a Qwen pour chat et reasoning."
            lower.startsWith("01-ai/") -> "Modele 01.AI Yi. Famille bilingue chinois/anglais open-weights pour chat et code."

            // XAI GROK
            lower.contains("grok-3-mini") -> "xAI Grok 3 Mini. Modele economique de la famille Grok. Chat et reasoning de base avec acces real-time (selon deployment)."
            lower.contains("grok") -> "xAI Grok. Modele conversationnel avec acces temps reel (selon deployment). Style irrevernicieux et raisonnement avance."

            // IBM GRANITE
            lower.contains("granite-code") -> "IBM Granite Code. Modele code enterprise sur 116 langages. Apache 2.0, optimise pour devops, IaC et applications entreprise."
            lower.contains("granite") -> "IBM Granite. Famille enterprise Apache 2.0. Modeles base et instruct pour entreprises avec compliance et auditabilite."

            // STABILITY / FLUX / IMAGE GEN
            lower.contains("flux-1.1") || lower.contains("flux-pro") -> "Black Forest Labs FLUX 1.1 Pro. Generation images haute qualite avec excellent suivi prompts. Style photoreal et artistique."
            lower.contains("flux-schnell") -> "FLUX Schnell BFL. Generation rapide (1-4 steps) pour prototypes, mood boards et iteration creative haute frequence."
            lower.contains("flux-2-klein") -> "FLUX 2 Klein 9B img2img. Transformation d'images existantes via prompt textuel. Contrainte input ≤512×512."
            lower.contains("flux") -> "Black Forest Labs FLUX. Generateur images haute qualite alternative SDXL/DALL-E. Open-weights."
            lower.contains("stable-diffusion-xl") || lower.contains("sdxl") -> "Stable Diffusion XL Stability AI. Generation images 1024px polyvalente. Open-weights, fine-tuning facile pour styles specifiques."
            lower.contains("stable-diffusion") -> "Stable Diffusion (Stability AI). Generateur images open-weights base de nombreux modeles communautaires."
            lower.contains("dreamshaper") -> "DreamShaper SDXL fine-tune. Specialise styles artistiques, fantasy, portraits stylises. Communaute Civitai."
            lower.contains("sd-v1.5-img2img") -> "Stable Diffusion 1.5 img2img. Transformation d'images existantes vers nouveau prompt. Legacy mais stable."

            // EMBEDDING / RERANKING
            lower.contains("bge-large") -> "BAAI BGE Large embeddings. Recherche semantique multilingue performante. Open-source, alternative a OpenAI embeddings."
            lower.contains("bge-m3") -> "BAAI BGE-M3 embeddings. Multilingue 100+ langues, multi-modal, multi-granular pour RAG cross-language."
            lower.contains("jina-embeddings") -> "Jina AI embeddings. Multilingue specialise long-document. RAG, search et code retrieval optimises."
            lower.contains("rerank") -> "Modele reranker. Re-classement de resultats de recherche pour ameliorer la pertinence des top-K dans pipelines RAG."

            // SCIENTIFIC SPECIFIC
            lower.contains("esmfold") || lower.contains("esm2") -> "Meta ESMFold/ESM-2. Prediction structure proteines a partir de sequence. Alternative a AlphaFold plus rapide."
            lower.contains("evo2") -> "Modele bioinformatique. Sequences ADN/ARN, design de genes et analyse genomique."

            // BY PUBLISHER GENERIQUE
            pub == "meta" || pub == "meta-llama" -> "Modele Meta Llama open-weights. Chat polyvalent, code, multilingue. Famille leader open-source."
            pub == "openai" -> "Modele OpenAI. Chat IA generaliste haute qualite. Reference du marche."
            pub == "anthropic" -> "Modele Anthropic Claude. Chat IA safety-first avec raisonnement et coding excellents."
            pub == "google" -> "Modele Google. Multimodal et tres bon contexte long. Famille Gemini/Gemma."
            pub == "microsoft" -> "Modele Microsoft Phi. Petit mais excellent raisonnement (math/sciences) pour son nombre de parametres."
            pub == "mistralai" || pub == "mistral-ai" -> "Modele Mistral AI. Open-weights europeen avec excellents multilingue, code et function calling."
            pub == "deepseek-ai" -> "Modele DeepSeek. Open-weights chinois performant pour code, math et reasoning."
            pub == "cohere" -> "Modele Cohere. Enterprise RAG-natif avec citations sources et tool use."
            pub == "nvidia" || pub == "nv-mistralai" -> "Modele NVIDIA. Optimise GPU NVIDIA pour deploiement entreprise sur DGX/NIM."
            pub == "ibm" -> "Modele IBM. Enterprise Apache 2.0 avec compliance et audit."
            pub == "xai" -> "Modele xAI Grok. Conversational avec acces temps reel (selon deployment)."
            pub == "ai21labs" -> "Modele AI21 Jamba. Architecture hybride Mamba/Transformer pour long contexte tres efficace."
            pub == "01-ai" -> "Modele 01.AI Yi. Bilingue chinois/anglais open-weights."
            pub == "moonshotai" -> "Modele Moonshot AI Kimi. Open-weights chinois pour reasoning et long contexte."
            pub == "minimaxai" -> "Modele MiniMax. Open-weights chinois multimodal."
            pub == "z-ai" -> "Modele Z.ai (GLM). Open-weights chinois Zhipu AI pour chat et reasoning."
            pub == "stepfun-ai" -> "Modele StepFun. Open-weights chinois pour applications multimodales."
            pub == "writer" -> "Modele Writer Palmyra. Specialise contenu marketing, finance ou medical selon variante."
            pub == "snowflake" -> "Modele Snowflake. Entreprise data warehouse-natif pour SQL et analyse."
            pub == "databricks" -> "Modele Databricks DBRX. Open-weights MoE entreprise pour code et raisonnement."
            pub == "huggingface" -> "Modele HuggingFace. Community open-source disponible sur le Hub."
            pub == "abacusai" -> "Modele AbacusAI. Enterprise AI platform."
            pub == "aisingapore" -> "AI Singapore SEA-LION. Specialise langues d'Asie du Sud-Est."
            pub == "tiiuae" -> "TII (UAE) Falcon. Open-weights arabe/anglais polyvalent."

            // FALLBACK FINAL : description generique selon kind detectable
            lower.contains("embed") -> "Modele d'embeddings pour RAG, recherche semantique et clustering."
            lower.contains("rerank") -> "Modele reranker pour amelioration de la pertinence en pipeline RAG."
            lower.contains("whisper") || lower.contains("asr") -> "Modele de reconnaissance vocale (speech-to-text)."
            lower.contains("tts") -> "Modele de synthese vocale (text-to-speech)."
            lower.contains("code") -> "Modele specialise generation et completion de code."
            lower.contains("vision") || lower.contains("-vl-") -> "Modele Vision-Language pour analyse d'images et chat multimodal."
            lower.contains("guard") || lower.contains("safety") -> "Modele de classification safety/moderation pour pipelines LLM securises."

            else -> null
        }
    }
}

package com.shredcoach.app.domain.llm

/**
 * Descriptions vulgarisees et user-friendly pour chaque modele LLM affiche
 * dans le picker Playground (~155 modeles individualises).
 *
 * **Tone** : langage simple, comprehensible par tout le monde (non-technique).
 *   - Ce que le modele FAIT (en langage du quotidien)
 *   - QUI peut l'utiliser et POUR QUOI
 *   - QUAND c'est le bon choix
 *
 * **Format** : 30-40 mots max, francais grand public.
 *
 * **Sources** : recherche web profonde sur build.nvidia.com, HuggingFace,
 * blogs officiels, fiches techniques. Specifications techniques (params,
 * context window, benchmarks) ont ete TRADUITES en termes accessibles.
 *
 * Strategie de matching :
 *  1. Match exact via [EXACT_MATCH] (O(1) lookup)
 *  2. Fallback pattern famille/publisher (cas non encore couvert)
 */
object ModelDescriptions {

    fun describe(id: String, publisher: String?): String? {
        EXACT_MATCH[id]?.let { return it }
        EXACT_MATCH[id.lowercase()]?.let { return it }
        return patternFallback(id.lowercase(), publisher?.lowercase())
    }

    private val EXACT_MATCH: Map<String, String> = mapOf(
        // ═══ META LLAMA — Famille populaire open-source ═══════════════════
        "meta/llama-3.3-70b-instruct" to
            "Le modele open-source phare de Meta. Excellent en conversation, ecriture, code et raisonnement. Bon choix par defaut pour assistant general, alternative gratuite a ChatGPT.",
        "meta-llama/llama-4-scout-17b-16e-instruct" to
            "Llama 4 Scout sur Groq : conversation tres rapide grace aux puces specialisees. Comprend les images. Pour applications temps reel exigeant une reponse ultra-fluide.",
        "llama-3.3-70b-versatile" to
            "Llama 3.3 ultra-rapide grace a Groq (276 mots/seconde !). Pour chatbots, assistants et applications ou la vitesse de reponse est critique.",
        "meta/llama-4-maverick-17b-128e-instruct" to
            "Nouvelle generation Meta multimodale. Comprend texte, images (jusqu'a 5) et gere de tres longues conversations. Pour assistants polyvalents nouvelle generation.",
        "meta/llama-3.2-90b-vision-instruct" to
            "Llama avec super-pouvoirs de vision : analyse d'images detaillee, lecture de graphiques, comprehension de documents scannes. Pour traiter des contenus visuels complexes.",
        "meta/llama-3.2-11b-vision-instruct" to
            "Version legere de Llama vision. Lit images, graphiques et documents tout en restant economique. Pour applications mobiles ou contraintes en cout.",
        "meta/llama-3.2-3b-instruct" to
            "Mini Llama 3B pensee pour fonctionner sur smartphones et appareils peu puissants. Pour assistants embarques, chatbots simples sans connexion internet.",
        "meta/llama-3.2-1b-instruct" to
            "Le plus petit Llama, ultra-compact. Tourne meme sur des montres connectees ou objets IoT. Pour reponses simples en mode offline.",
        "meta/llama-3.1-70b-instruct" to
            "Llama 3.1 grosse version : conversation experte multilingue (8 langues), gere de longs documents. Pour assistants exigeants polyvalents.",
        "meta/llama-3.1-8b-instruct" to
            "Llama 3.1 version legere. Bon equilibre vitesse-qualite pour chatbots, automatisations et taches a haut volume sans casser la banque.",
        "meta/codellama-70b" to
            "Llama specialise programmation. Genere, explique et corrige du code dans tous les langages populaires. Pour developpeurs qui veulent un assistant code dedie.",
        "meta/llama-guard-4-12b" to
            "Le gendarme de Llama : detecte contenus inappropries (haine, violence, etc.) dans textes et images. Pour moderation automatique de plateformes et reseaux sociaux.",
        "meta/llama2-70b" to
            "Ancien fleuron Meta (2023). Toujours fonctionnel pour conversation basique. Remplacer par Llama 3.3 plus performant. Garde une valeur historique.",

        // ═══ OPENAI — La reference grand public ════════════════════════════
        "openai/gpt-5" to
            "Le modele le plus puissant d'OpenAI (avril 2026). Pour les taches les plus exigeantes : analyses approfondies, problemes complexes, recherche scientifique. Reservation premium.",
        "openai/gpt-5-chat" to
            "GPT-5 optimise conversation. Echanges fluides et naturels avec un raisonnement de pointe. Pour assistants exigeants ou chatbots de tres haute qualite.",
        "openai/gpt-5-mini" to
            "Version reduite et abordable de GPT-5. Garde l'essentiel de la puissance avec une latence reduite. Pour applications de production a fort volume.",
        "openai/gpt-5-nano" to
            "GPT-5 ultra-leger et tres rapide. Pour classification de texte, extraction de donnees, taches simples en masse a moindre cout.",
        "openai/gpt-4o" to
            "Le couteau suisse d'OpenAI. Comprend texte, images, audio et video. Repond tres vite (232ms). Pour assistants multimodaux generalistes haut de gamme.",
        "openai/gpt-4o-mini" to
            "Version economique de GPT-4o. Garde les capacites multimodales (texte+image) a prix reduit. Bon choix pour chatbots, extraction de donnees et tasks legeres.",
        "openai/gpt-4.1" to
            "GPT-4.1 avec memoire enorme (1 million de mots !). Excellent pour analyser livres entiers, codebases ou archives. Suit les instructions avec precision.",
        "openai/gpt-4.1-mini" to
            "Mini-version de GPT-4.1, garde la grosse memoire (1M mots) a prix reduit. Pour analyser de gros documents sans payer le tarif premium.",
        "openai/gpt-4.1-nano" to
            "GPT-4.1 nano : ultra-leger et tres rapide. Pour classification de masse, extractions automatiques, taches simples en haut volume avec latence minimale.",
        "openai/o1" to
            "Le premier modele 'pensant' d'OpenAI. Reflechit avant de repondre pour resoudre des problemes math, sciences ou code complexes. Plus lent mais plus precis.",
        "openai/o1-mini" to
            "Mini-version raisonnante d'OpenAI. Specialise math et code competitifs. 80% moins cher que o1 tout en restant excellent en sciences exactes.",
        "openai/o1-preview" to
            "Version preview du raisonnement OpenAI. Reflexion profonde sur problemes complexes, mais sans support des images. Conservee pour compatibilite.",
        "openai/o3" to
            "Modele de raisonnement avance (avril 2025). Peut chercher sur le web, lire des fichiers et analyser des images. Pour recherches approfondies.",
        "openai/o3-mini" to
            "o3 version economique. Excellent en math (87% AIME) et sciences avec un cout reduit. Pour applications education ou tutorat scientifique.",
        "openai/o4-mini" to
            "Derniere generation de raisonnement OpenAI (2026). Comprend les images, 20% meilleur que o3-mini. Pour analyses visuelles et resolution rapide de problemes.",
        "openai/gpt-oss-120b" to
            "Premier modele open-source d'OpenAI. Performant pour reasoning et agents autonomes. Tres rapide sur Groq, peut tourner localement sur GPU H100.",
        "openai/gpt-oss-20b" to
            "Petit modele open-source d'OpenAI qui tourne meme avec 16 Go de RAM. Pour tester en local sans GPU puissant, performances proches o3-mini.",
        "openai/text-embedding-3-large" to
            "Convertit textes en 'empreintes numeriques' pour la recherche intelligente. Pour moteurs de recherche, chatbots avec memoire, classement de documents.",
        "openai/text-embedding-3-small" to
            "Version compacte des embeddings OpenAI. Pour recherche semantique et bases de connaissances avec un bon equilibre qualite/cout.",

        // ═══ ANTHROPIC CLAUDE — Sur sur la securite ═════════════════════════
        "claude-opus-4-20250514" to
            "Le modele Anthropic le plus capable. Excellent pour code complexe, analyse approfondie de gros projets et taches qui durent des heures. Pour besoins critiques.",
        "claude-sonnet-4-20250514" to
            "L'assistant equilibre d'Anthropic. Tres bon en code, ecriture, analyse et utilisation d'outils. Comprend les images. Le defaut recommande pour la plupart des cas.",
        "claude-haiku-4-20250514" to
            "Claude Haiku ultra-rapide (98 mots/sec). Pour chatbots, classification rapide et applications a haut volume ou la vitesse compte plus que la qualite max.",

        // ═══ GOOGLE GEMINI — Multimodal natif ══════════════════════════════
        "gemini-2.5-flash" to
            "Gemini Flash equilibre : rapide, multimodal (texte/image/audio/video) et abordable. Pour applications grand public, agents et chatbots polyvalents.",
        "gemini-3-flash-preview" to
            "Gemini 3 nouvelle generation (preview). Reasoning agent ameliore pour workflows interactifs, codage iteratif et assistants multi-tours sophistiques.",
        "gemini-3.5-flash" to
            "Gemini 3.5 frontier 4x plus rapide. Pour agents complexes, codage avance et workflows entreprise qui s'etalent sur plusieurs semaines.",
        "gemini-2.0-flash" to
            "Gemini 2.0 ancien Flash. Multimodal natif, peut meme generer des images et synthese vocale. Pour migrer vers 2.5/3 plus capable.",

        // ═══ GOOGLE GEMMA — Open-source de Google ══════════════════════════
        "google/gemma-3-12b-it" to
            "Gemma 3 grosse version open-source. Comprend texte et images, parle 140 langues. Pour applications self-hosted multilingues avec deploiement local.",
        "google/gemma-3-4b-it" to
            "Gemma 3 version mobile. Multimodal et tres compact. Pour tourner sur smartphones et tablettes sans connexion internet.",
        "google/gemma-3n-e2b-it" to
            "Mini Gemma optimise edge devices. Comprend texte, images, audio et video avec seulement 2GB de memoire. Pour IoT et appareils contraints.",
        "google/gemma-3n-e4b-it" to
            "Gemma edge equilibre. Multimodal pour smartphones et tablettes modernes. Bon compromis qualite/legerete sans serveur.",
        "google/gemma-4-31b-it" to
            "Gemma 4 nouvelle generation. Vision native, fonction calling et tres long contexte. Pour applications open-source ambitieuses.",
        "google/gemma-2-2b-it" to
            "Mini Gemma 2 ancienne generation. Compact pour conversation legere. Remplacer par Gemma 3 plus capable.",
        "google/gemma-2b" to
            "Gemma 2B original. Tres compact pour applications edge. Modele de base sans fine-tuning instruct.",
        "google/codegemma-7b" to
            "Gemma specialise programmation. Complete, suggere et explique du code. Pour assistants developpeur economiques en self-hosting.",
        "google/codegemma-1.1-7b" to
            "CodeGemma ameliore. Genere du code dans 80+ langages avec capacite fill-in-the-middle. Pour autocompletion IDE.",
        "google/deplot" to
            "Lit et explique des graphiques (barres, courbes, camemberts). Pour applications qui doivent extraire des donnees de visualisations scientifiques.",
        "google/recurrentgemma-2b" to
            "Gemma avec architecture RNN economique. Genere de longs textes avec memoire constante. Pour applications de streaming texte continu.",

        // ═══ MICROSOFT PHI — Petits modeles intelligents ════════════════════
        "microsoft/phi-4" to
            "Petit modele Microsoft qui dechire en math et sciences malgre sa taille modeste. Pour tutorat scientifique, aide aux devoirs, raisonnement logique.",
        "microsoft/phi-4-reasoning" to
            "Phi-4 specialise raisonnement profond. Math avance, code et generation de preuves. Pour applications STEM et resolution de problemes complexes.",
        "microsoft/phi-4-mini-reasoning" to
            "Mini Phi-4 raisonnant. Pour aide aux devoirs en math/sciences sur appareils legers. Entraine sur 1.4M de questions STEM.",
        "microsoft/phi-4-mini-instruct" to
            "Phi-4 mini conversation. Tres compact pour assistants embarques avec instructions a suivre et longue memoire (128K).",
        "microsoft/phi-4-multimodal-instruct" to
            "Phi-4 multimodal : comprend simultanement texte, images ET audio. Pour assistants vocaux intelligents et analyse de contenus mixtes.",
        "microsoft/phi-3-vision-128k-instruct" to
            "Phi-3 leger avec vision. Lit images, tableaux, graphiques. Pour applications mobiles qui doivent analyser des contenus visuels.",
        "microsoft/phi-3.5-moe-instruct" to
            "Phi-3.5 architecture MoE economique. Performances de gros modele a cout reduit grace a l'activation selective.",
        "microsoft/mai-ds-r1" to
            "Version Microsoft securisee de DeepSeek R1. Filtres de securite renforces, bloque 99.3% des sujets sensibles. Pour entreprise.",

        // ═══ MISTRAL — Open-source europeen ═════════════════════════════════
        "mistralai/codestral-22b-instruct-v0.1" to
            "Modele Mistral specialise code. Maitrise 80+ langages, autocompletion intelligente et tests automatises. Pour assistants developpeur self-hosted.",
        "mistral-ai/codestral-2501" to
            "Codestral derniere version. Excellent en code (86.6% HumanEval), contexte enorme (256K). Pour assistants developpeur professionnels.",
        "mistralai/mistral-large" to
            "Le fleuron Mistral. Multimodal, gros contexte, raisonnement expert. Pour applications enterprise critiques en France/Europe.",
        "mistralai/mistral-large-3-675b-instruct-2512" to
            "Mistral Large 3 nouvelle generation. Multimodal experts MoE, vision et agents. Pour applications enterprise de pointe.",
        "mistralai/mistral-large-2-instruct" to
            "Mistral Large 2 dense 123B. Math, code et raisonnement avance. Pour applications enterprise demandant qualite maximale.",
        "mistralai/mistral-medium-3.5-128b" to
            "Mistral Medium 3.5 equilibre. Comprend les images, code et raisonnement avance. Bon compromis qualite/cout enterprise.",
        "mistral-ai/mistral-medium-2505" to
            "Mistral Medium frontier multimodal. Comprend texte et images avec raisonnement avance. Alternative europeenne a GPT-4.",
        "mistralai/mistral-small-4-119b-2603" to
            "Mistral Small architecture MoE. Performance maximale a cout reduit. Pour applications enterprise agents et reasoning configurable.",
        "mistral-ai/mistral-small-2503" to
            "Mistral Small multimodal (mars 2025). Comprend texte et images. Pour chatbots et assistants enterprise abordables.",
        "mistral-small-latest" to
            "Mistral Small derniere version. Polyvalent et abordable pour prototypage, chatbots simples et automatisation business.",
        "mistralai/mistral-nemotron" to
            "Mistral co-developpe avec NVIDIA. Specialise agents et function calling. Pour applications qui orchestrent plusieurs outils.",
        "mistralai/ministral-14b-instruct-2512" to
            "Ministral multimodal compact. Comprend jusqu'a 10 images, multilingue. Pour applications edge avec capacites vision.",
        "mistral-ai/ministral-3b" to
            "Mini Mistral 3B vision-langage. Tres compact pour deploiement edge sur smartphones et IoT avec capacites multimodales.",
        "mistralai/mistral-7b-instruct-v0.3" to
            "Mistral 7B classique. Compact et efficace pour chat, code et instruction-following. Reference des debuts open-source.",
        "mistralai/mixtral-8x22b-v0.1" to
            "Mixtral grande MoE Apache 2.0. Excellent en multilingue, math et code. Pour deploiement self-hosted enterprise gratuit.",
        "mistralai/mixtral-8x7b-instruct-v0.1" to
            "Mixtral 8x7B premier MoE Mistral. Multilingue (FR/EN/IT/DE/ES) economique. Base populaire de nombreuses applications open-source.",
        "pixtral-large-latest" to
            "Pixtral specialise vision. Analyse documents, graphiques et jusqu'a 30 images simultanement. Pour applications visuelles avancees Mistral.",

        // ═══ DEEPSEEK — Reasoning open-source chinois ═══════════════════════
        "deepseek/deepseek-r1" to
            "Modele 'pensant' open-source chinois. Reflechit avant de repondre comme o1 d'OpenAI mais gratuit. Excellent en math et code.",
        "deepseek/deepseek-r1-0528" to
            "DeepSeek R1 version mai 2025. Reasoning ameliore, 87.5% au benchmark AIME 2025. Pour problemes math/code competitifs.",
        "deepseek/deepseek-v3-0324" to
            "DeepSeek V3 mars 2025. Modele conversationnel rapide avec post-training ameliore. Pour chat, ecriture et code quotidien.",
        "deepseek-ai/deepseek-v4-flash" to
            "DeepSeek V4 Flash : reasoning rapide. Pour problemes math/code/logique avec chain-of-thought. Memoire de 1 million de mots.",
        "deepseek-ai/deepseek-v4-pro" to
            "DeepSeek V4 Pro le plus puissant : 3 modes de raisonnement adaptables. Pour les problemes les plus complexes en sciences et code.",
        "deepseek-ai/deepseek-coder-6.7b-instruct" to
            "Petit modele DeepSeek dedie code. 78.6% HumanEval, ideal pour applications developpeur self-hosted avec contraintes de ressources.",

        // ═══ ALIBABA QWEN — Multilingue performant ══════════════════════════
        "qwen/qwen3-coder-480b-a35b-instruct" to
            "Qwen specialise code 480B. Massif et expert dans tous les langages de programmation. Pour applications code professionnelles haut de gamme.",
        "qwen/qwen3-next-80b-a3b-instruct" to
            "Qwen3 Next architecture hybride. 10x plus rapide que precedent, memoire enorme. Pour assistants conversationnels modernes.",
        "qwen/qwen3.5-122b-a10b" to
            "Qwen 3.5 multimodal (fev 2026). Comprend texte, images et videos. Pour assistants multimodaux open-source avances.",
        "qwen/qwen3.5-397b-a17b" to
            "Qwen 3.5 flagship 397B MoE. Multimodal frontier avec memoire enorme. Pour applications open-source les plus exigeantes.",

        // ═══ COHERE COMMAND — Enterprise RAG ═══════════════════════════════
        "cohere/cohere-command-a" to
            "Cohere Command A enterprise. Tres bon en 23 langues, utilise des outils externes. Pour assistants business multilingues professionnels.",
        "cohere/cohere-command-r-08-2024" to
            "Cohere Command R economique. Specialise recherche dans documents (RAG) avec citations sources. Pour bases de connaissances enterprise.",
        "cohere/cohere-command-r-plus-08-2024" to
            "Cohere Command R+ : grounding avance et citations sources. Pour assistants enterprise RAG critiques avec verification factuelle.",

        // ═══ AI21 JAMBA — Memoire enorme ════════════════════════════════════
        "ai21labs/jamba-1.5-large-instruct" to
            "Jamba Large architecture hybride innovante. Memoire enorme (256K mots). Pour analyser de tres longs documents juridiques ou contrats.",
        "ai21-labs/ai21-jamba-1.5-large" to
            "AI21 Jamba sur GitHub Models. Architecture SSM-Transformer pour gros contextes (256K). Pour documents legaux/financiers volumineux.",

        // ═══ XAI GROK — Avec acces temps reel ══════════════════════════════
        "xai/grok-3" to
            "Grok 3 de xAI : reasoning multimodal puissant. Pour assistants avec acces temps reel. Style irreverencieux et raisonnement minute.",
        "xai/grok-3-mini" to
            "Grok 3 Mini economique. 85% des performances pour 4-6x moins cher. Pour applications grand volume avec touche xAI.",

        // ═══ NVIDIA NEMOTRON — Famille NVIDIA ═══════════════════════════════
        "nvidia/llama-3.1-nemotron-ultra-253b-v1" to
            "Nemotron Ultra : modele NVIDIA gigantesque pour math complexe et raisonnement scientifique. Pour recherche academique et applications STEM exigeantes.",
        "nvidia/llama-3.1-nemotron-70b-instruct" to
            "Nemotron 70B aligne RLHF. Tres bien classe ArenaHard. Pour chat de qualite et assistants conversationnels enterprise.",
        "nvidia/llama-3.1-nemotron-51b-instruct" to
            "Nemotron 51B instruction. Pour conversation et taches generales avec un bon equilibre puissance/cout.",
        "nvidia/llama-3.1-nemotron-nano-8b-v1" to
            "Mini Nemotron 8B pour usage local. Tourne sur GPU RTX consumer. Pour assistants RAG et chat efficaces sans cloud.",
        "nvidia/llama-3.1-nemotron-nano-vl-8b-v1" to
            "Mini Nemotron vision 8B. Analyse documents et images en local. Pour applications vision sur petite infrastructure.",
        "nvidia/llama-3.3-nemotron-super-49b-v1" to
            "Nemotron Super 49B. Equilibre coding/math/chat efficace sur un seul GPU. Pour applications enterprise polyvalentes.",
        "nvidia/llama-3.3-nemotron-super-49b-v1.5" to
            "Nemotron Super v1.5 amelioree. Tool calling et RAG avance avec contexte 131K. Pour agents enterprise modernes.",
        "nvidia/nemotron-4-340b-instruct" to
            "Nemotron 4 gigantesque 340B. Pour generation de donnees synthetiques d'entrainement et alignment de modeles.",
        "nvidia/nemotron-4-340b-reward" to
            "Modele de scoring NVIDIA. Evalue la qualite des reponses (utilite, exactitude, coherence) pour fine-tuning RLHF.",
        "nvidia/nemotron-3-super-120b-a12b" to
            "Nemotron 3 Super MoE 120B. Reasoning et agents multi-step avec memoire de 1 million de mots.",
        "nvidia/nemotron-3-nano-30b-a3b" to
            "Nemotron 3 Nano MoE 30B. Pour agents et chat avec longue memoire en restant economique.",
        "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning" to
            "Nemotron Omni : texte, image, video, audio unifies. Agents multimodaux avec raisonnement. Pour applications IA omnicanales.",
        "nvidia/nemotron-mini-4b-instruct" to
            "Mini Nemotron 4B pour roleplay et chat. Tourne sur GPU 2GB. Pour assistants embarques et applications mobiles.",
        "nvidia/nemotron-nano-12b-v2-vl" to
            "Nemotron Nano vision 12B. Specialise documents multi-images et resumes visuels.",
        "nvidia/nemotron-nano-3-30b-a3b" to
            "Nemotron Nano 3 hybride. Agents et RAG avec contexte de 1 million de mots a cout reduit.",
        "nvidia/nvidia-nemotron-nano-9b-v2" to
            "Nemotron Nano 9B chat-vision. Raisonnement texte+image avec memoire 131K. Pour assistants multimodaux compacts.",
        "nvidia/nemotron-parse" to
            "Outil NVIDIA pour extraire texte, tableaux et images depuis PDFs et documents complexes. Pour automatisation paperasse.",
        "nvidia/nemoretriever-parse" to
            "NemoRetriever specialise extraction documents. OCR, tableaux et images depuis PDFs scannes. Pour numerisation documentaire.",
        "nvidia/mistral-nemo-minitron-8b-8k-instruct" to
            "Mistral NeMo Minitron distille 8B. Instruction-following efficace pour chat compact en remplacement de Mistral 7B.",
        "nv-mistralai/mistral-nemo-12b-instruct" to
            "Mistral NeMo co-developpe avec NVIDIA. 12B optimise pour langues europeennes. Pour chatbots multilingues enterprise.",
        "nvidia/cosmos-reason2-8b" to
            "Cosmos NVIDIA pour la robotique. Raisonne sur la physique reelle et les agents vision. Pour applications robotique avancees.",

        // ═══ NVIDIA SAFETY/MODERATION ═══════════════════════════════════════
        "nvidia/llama-3.1-nemoguard-8b-content-safety" to
            "NemoGuard : detecte contenus inappropries dans les conversations IA (23 categories de risques). Pour moderation automatique de chatbots.",
        "nvidia/llama-3.1-nemoguard-8b-topic-control" to
            "NemoGuard Topic Control : restreint les sujets autorises dans une conversation. Pour chatbots specialises qui ne doivent pas devier.",
        "nvidia/llama-3.1-nemotron-safety-guard-8b-v3" to
            "Safety Guard 8B multilingue (9 langues). Filtre contenus problematiques en texte et image. Pour plateformes internationales.",
        "nvidia/nemotron-3-content-safety" to
            "Content Safety compact multimodal. Modere texte et images en 12 langues. Pour plateformes communautaires multilingues.",
        "nvidia/nemotron-content-safety-reasoning-4b" to
            "Safety avec raisonnement personnalisable. Politiques de moderation adaptables au contexte. Pour deploiement enterprise sur mesure.",
        "nvidia/gliner-pii" to
            "Detecteur de donnees personnelles sensibles (noms, emails, telephones, IDs). Pour redaction automatique RGPD et conformite.",
        "nvidia/ai-synthetic-video-detector" to
            "Detecte les videos generees par IA (deepfakes). Pour verification d'authenticite et lutte contre la desinformation multimedia.",

        // ═══ NVIDIA EMBEDDINGS — Pour recherche intelligente ═══════════════
        "nvidia/embed-qa-4" to
            "Convertit textes en empreintes numeriques pour recherche question-reponse. Pour chatbots enterprise avec base de connaissances.",
        "nvidia/nv-embed-v1" to
            "Le meilleur embedding NVIDIA (top MTEB). Pour moteurs de recherche, classification et regroupement de documents.",
        "nvidia/nv-embedqa-mistral-7b-v2" to
            "Embedding Mistral-base pour Q&A. Memoire 4K mots, classe #1 MTEB. Pour recherche RAG enterprise haute qualite.",
        "nvidia/nv-embedqa-e5-v5" to
            "Embedding Q&A compact 512 tokens. Pour applications RAG simples et rapides avec recherche dense.",
        "nvidia/nv-embedcode-7b-v1" to
            "Embedding specialise code. Cherche du code par description ou similarite. Pour outils developpeur de recherche dans codebase.",
        "nvidia/llama-nemotron-embed-1b-v2" to
            "Mini embedding Nemotron 1B multilingue (26 langues). Pour recherche RAG sur documents internationaux longs.",
        "nvidia/llama-nemotron-embed-vl-1b-v2" to
            "Embedding multimodal 1B texte+image. Pour recherche cross-modale dans catalogues d'images ou documents avec illustrations.",
        "nvidia/llama-3.2-nv-embedqa-1b-v1" to
            "Embedding Llama 3.2 pour Q&A. Petit (1B) et multilingue (26 langues). Pour chatbots RAG economiques.",
        "nvidia/llama-3.2-nemoretriever-1b-vlm-embed-v1" to
            "NemoRetriever embedding multimodal. Cherche dans texte+image+document. Pour applications cross-modal RAG enterprise.",

        // ═══ NVIDIA TRANSLATION & VISION ════════════════════════════════════
        "nvidia/riva-translate-4b-instruct" to
            "Traducteur NVIDIA 4B 12 langues. Pour applications multilingues temps reel (chat support, sous-titres en direct).",
        "nvidia/riva-translate-4b-instruct-v1.1" to
            "Riva Translate v1.1 amelioree. Benchmark FLORES en 12 langues. Pour applications de traduction enterprise.",
        "nvidia/neva-22b" to
            "NEVA 22B vision-langage. Comprend images et discute avec instructions. Pour assistants visuels enterprise.",
        "nvidia/vila" to
            "VILA NVIDIA edge/cloud. Multi-images et videos avec optimisations Jetson. Pour applications IA embarquees robotique.",
        "nvidia/nvclip" to
            "NVCLIP NVIDIA pour recherche image-texte. Classification zero-shot et moderation visuelle. Pour catalogues e-commerce.",
        "nvidia/llama3-chatqa-1.5-70b" to
            "Chat-QA NVIDIA 70B. Specialise dialogue RAG et conversations questions-reponses dans documents.",
        "nvidia/ising-calibration-1-35b-a3b" to
            "Modele NVIDIA pour calibration quantique. Pour applications quantum computing et correction d'erreurs (cas tres specialise).",

        // ═══ GITHUB MODELS — Specifiques ════════════════════════════════════
        "openai/gpt-4o" to
            "GPT-4o sur GitHub Models. Multimodal complet (texte/audio/image/video). Pour tester l'omnimodalite OpenAI gratuitement avec PAT GitHub.",
        "openai/gpt-4o-mini" to
            "GPT-4o-mini sur GitHub Models. Economique et multimodal. Tester gratuitement via PAT GitHub avant production.",
        "openai/gpt-4.1" to
            "GPT-4.1 sur GitHub Models. Tres long contexte (1M mots) pour tests sans frais OpenAI directs. Authentification PAT GitHub.",
        "openai/gpt-4.1-mini" to
            "GPT-4.1-mini gratuit via GitHub Models. Long contexte (1M mots) abordable. Pour tests de prototypes long-document.",
        "openai/gpt-4.1-nano" to
            "GPT-4.1-nano sur GitHub Models. Ultra-rapide et tres economique pour classification a haut volume.",

        // ═══ AUTRES PROVIDERS NVIDIA ═══════════════════════════════════════
        "01-ai/yi-large" to
            "Modele conversationnel bilingue chinois/anglais. Pour applications visant les marches asiatiques avec traduction et chat de qualite.",
        "abacusai/dracarys-llama-3.1-70b-instruct" to
            "Variante de Llama 3.1 affinee pour le code par AbacusAI. Pour developpeurs cherchant un assistant programmation alternatif.",
        "adept/fuyu-8b" to
            "Petit modele multimodal sans encodeur vision dedie. Pour analyser screenshots et documents simples avec efficacite.",
        "aisingapore/sea-lion-7b-instruct" to
            "Specialise Asie du Sud-Est : thai, vietnamien, indonesien, malais, etc. (11 langues). Pour applications regionales asiatiques.",
        "bigcode/starcoder2-15b" to
            "StarCoder 2 open-source pour code. Maitrise 600+ langages de programmation. Pour assistants developpeur self-hosted libre de droits.",
        "bytedance/seed-oss-36b-instruct" to
            "Modele ByteDance 36B avec memoire enorme (512K mots). Reasoning configurable. Pour analyses de tres longs documents.",
        "databricks/dbrx-instruct" to
            "DBRX architecture MoE Databricks. Pour applications data warehouse et analytics avec integration Databricks.",
        "minimaxai/minimax-m2.7" to
            "MiniMax M2.7 pour genie logiciel complexe. Memoire 196K mots. Pour developpement et productivite ambitieux.",
        "moonshotai/kimi-k2.6" to
            "Kimi K2.6 de Moonshot. Reasoning agentic multimodal avec memoire 262K. Pour orchestration d'agents et applications IA chinoises.",
        "sarvamai/sarvam-m" to
            "Modele Indien specialise. Hindi et 11 autres langues indiennes. Math +21.6% sur problemes locaux. Pour marche indien.",
        "snowflake/arctic-embed-l" to
            "Embedding Snowflake pour data warehouse. Recherche dense de qualite avec integration Snowflake natif.",
        "stepfun-ai/step-3.5-flash" to
            "StepFun Flash specialise code ultra-rapide. 100-350 mots/sec. Pour applications coding temps reel intensives.",
        "stockmark/stockmark-2-100b-instruct" to
            "Modele japonais business 100B bilingue. Pour documents complexes en japonais et marches japonais.",
        "upstage/solar-10.7b-instruct" to
            "SOLAR coreen avec architecture innovante. Depasse Mixtral 8x7B malgre sa taille modeste. Pour applications self-hosted polyvalentes.",
        "writer/palmyra-creative-122b" to
            "Palmyra Creative : ecriture creative, poesie, scripts et dialogues. Pour applications editoriales et generation de contenu.",
        "writer/palmyra-fin-70b-32k" to
            "Palmyra Finance specialise. 73% au CFA niveau III ! Pour assistants financiers professionnels et analyse de rapports.",
        "writer/palmyra-med-70b" to
            "Palmyra Medical biomedical. 85% sur benchmarks medicaux. Pour EHR, notes cliniques et applications sante.",
        "writer/palmyra-med-70b-32k" to
            "Palmyra Med long contexte. Resumes de discharge medicaux complets et analyses de dossiers patients longs.",
        "z-ai/glm-5.1" to
            "GLM 5.1 chinois architecture hybride. Agents qui peuvent travailler 8 heures en autonomie. Pour automatisation enterprise avancee.",
        "zyphra/zamba2-7b-instruct" to
            "Zamba2 architecture hybride efficace. 25% plus rapide qu'un Transformer 7B. Pour applications self-hosted economiques.",
        "baai/bge-m3" to
            "BGE-M3 embedding open-source multi-fonction. 100+ langues. Pour recherche multilingue et applications RAG cross-langue.",
        "ibm/granite-3.0-3b-a800m-instruct" to
            "Granite 3.0 mini MoE 3B. Multilingue 12 langues. Pour applications enterprise IBM avec compliance Apache 2.0.",
        "ibm/granite-3.0-8b-instruct" to
            "Granite 3.0 dense 8B multilingue. Pour applications enterprise IBM polyvalentes (resumes, Q&A).",
        "ibm/granite-34b-code-instruct" to
            "Granite Code 34B IBM. 116 langages, specialise gestion Git et refactoring. Pour outils developpeur enterprise.",
        "ibm/granite-8b-code-instruct" to
            "Granite Code 8B compact. Pour assistants developpeur enterprise avec longue memoire (128K).",
        "microsoft/kosmos-2" to
            "Kosmos-2 Microsoft. Comprend les images avec localisation spatiale precise. Pour applications de detection visuelle annotee.",

        // ═══ POLLINATIONS — Generation image gratuite ══════════════════════
        "flux" to
            "Generation d'images photorealistes via Pollinations, gratuit et sans inscription. Pour prototypes visuels, illustrations marketing, brainstorming creatif sans contrainte budgetaire.",
        "turbo" to
            "Generation d'images rapide via Pollinations. Pour iterations creatives rapides, mood boards et prototypage visuel en haut volume sans cout.",
        "kontext" to
            "Edition d'images existantes par instructions textuelles via Pollinations. Pour transformer photos, retoucher illustrations ou appliquer des styles avec une simple description.",
        "gptimage" to
            "Generation premium Pollinations avec controle fin (dimensions custom, seed, multi-images). Pour illustrations professionnelles necessitant precision et reproductibilite.",

        // ═══ CLOUDFLARE AI — Generation image Workers ═══════════════════════
        "@cf/black-forest-labs/flux-1-schnell" to
            "FLUX 1 Schnell sur Cloudflare. Generation d'images photorealistes haute fidelite (1024px) avec faible latence. Pour applications web temps reel.",
        "@cf/black-forest-labs/flux-2-klein-9b" to
            "FLUX 2 Klein ultra-rapide sur Cloudflare. Generation et edition multi-images en moins de 0.5 seconde. Pour applications interactives reactives.",
        "@cf/bytedance/stable-diffusion-xl-lightning" to
            "SDXL Lightning sur Cloudflare. Generation d'images en seulement 2 etapes pour latence minimale. Pour applications instantanees.",
        "@cf/stabilityai/stable-diffusion-xl-base-1.0" to
            "Stable Diffusion XL classique sur Cloudflare. Photorealisme natif 1024x1024 avec couleurs vibrantes. Pour generation d'images polyvalente.",
        "@cf/lykon/dreamshaper-8-lcm" to
            "DreamShaper LCM photorealiste. 5-15 etapes optimisees pour production. Pour styles artistiques varies et portraits stylises.",
        "@cf/runwayml/stable-diffusion-v1-5-img2img" to
            "SD 1.5 img2img sur Cloudflare. Transforme une image existante via prompt textuel avec force de transformation ajustable.",
    )

    // ─────────────────────────────────────────────────────────────────────
    // FALLBACK : pour les modeles non listes ci-dessus (nouveautes futures)
    // ─────────────────────────────────────────────────────────────────────

    private fun patternFallback(lower: String, pub: String?): String? = when {
        lower.contains("nemotron-ultra") -> "Modele NVIDIA Nemotron Ultra. Pour applications enterprise les plus exigeantes avec raisonnement complexe."
        lower.contains("nemotron-super") -> "Modele NVIDIA Nemotron Super. Pour chat et reasoning enterprise haut de gamme."
        lower.contains("nemotron-nano") -> "Mini Nemotron pour usage edge ou ressources limitees."
        lower.contains("nemotron") -> "Famille NVIDIA Nemotron pour applications enterprise sur GPU NVIDIA."
        lower.contains("nemoguard") -> "NVIDIA NemoGuard pour moderation et securite de contenus IA."
        lower.contains("magpie") -> "NVIDIA Magpie : synthese vocale multilingue pour voice agents."
        lower.contains("parakeet") -> "NVIDIA Parakeet : transcription audio (speech-to-text) precise."
        lower.contains("canary") -> "NVIDIA Canary : reconnaissance vocale multilingue avec traduction."
        lower.contains("alphafold") || lower.contains("rfdiffusion") || lower.contains("proteinmpnn") ->
            "Modele scientifique NVIDIA pour biologie computationnelle (proteines, molecules)."
        lower.contains("diffdock") || lower.contains("molmim") || lower.contains("genmol") ->
            "Modele NVIDIA pour decouverte de medicaments et chimie computationnelle."
        lower.contains("cuopt") -> "NVIDIA cuOpt pour optimisation logistique et planification."
        lower.contains("fourcastnet") || lower.contains("corrdiff") ->
            "Modele NVIDIA de prediction meteo/climat haute resolution."

        lower.contains("llama-4") -> "Meta Llama 4 nouvelle generation multimodale pour chat et agents avances."
        lower.contains("llama-3") -> "Meta Llama 3 famille open-source pour chat polyvalent multilingue."
        lower.contains("gemini") -> "Google Gemini multimodal avec memoire enorme et raisonnement avance."
        lower.contains("gemma") -> "Google Gemma open-source performant et multilingue."
        lower.contains("claude") -> "Anthropic Claude : assistant IA equilibre, securise et excellent en code."
        lower.contains("mistral") || lower.contains("mixtral") ->
            "Mistral AI : open-source europeen multilingue avec function calling."
        lower.contains("qwen") -> "Alibaba Qwen : multilingue (29 langues) performant pour code et math."
        lower.contains("phi") -> "Microsoft Phi : petit modele excellent en raisonnement scientifique."

        pub == "meta" || pub == "meta-llama" -> "Modele Meta Llama open-source pour chat polyvalent multilingue."
        pub == "openai" -> "Modele OpenAI : reference du marche pour assistant IA generaliste."
        pub == "anthropic" -> "Modele Anthropic Claude : assistant IA safety-first avec reasoning excellent."
        pub == "google" -> "Modele Google multimodal de la famille Gemini/Gemma."
        pub == "microsoft" -> "Modele Microsoft Phi : petit mais excellent en raisonnement."
        pub == "mistralai" || pub == "mistral-ai" -> "Modele Mistral AI : open-source europeen multilingue."
        pub == "deepseek-ai" || pub == "deepseek" -> "Modele DeepSeek : open-source chinois performant en code et reasoning."
        pub == "cohere" -> "Modele Cohere : enterprise RAG-natif avec citations sources."
        pub == "nvidia" || pub == "nv-mistralai" -> "Modele NVIDIA optimise pour GPU NVIDIA enterprise."
        pub == "ibm" -> "Modele IBM Granite enterprise Apache 2.0 avec compliance."
        pub == "xai" -> "Modele xAI Grok avec acces temps reel et style irreverencieux."
        pub == "ai21labs" || pub == "ai21-labs" -> "AI21 Jamba : architecture hybride pour tres long contexte."
        pub == "01-ai" -> "01.AI Yi : bilingue chinois/anglais open-source."
        pub == "moonshotai" -> "Moonshot Kimi : open-source chinois pour reasoning long contexte."
        pub == "minimaxai" -> "MiniMax : open-source chinois multimodal."
        pub == "z-ai" -> "Z.ai (GLM/Zhipu) : open-source chinois pour reasoning et agents."
        pub == "stepfun-ai" -> "StepFun : open-source chinois pour applications multimodales."
        pub == "writer" -> "Writer Palmyra : specialise contenu professionnel."
        pub == "snowflake" -> "Snowflake : enterprise data warehouse-natif."
        pub == "databricks" -> "Databricks DBRX : open-source MoE enterprise."
        pub == "huggingface" -> "Modele HuggingFace community open-source."
        pub == "abacusai" -> "AbacusAI : platform enterprise AI."
        pub == "aisingapore" -> "AI Singapore SEA-LION : langues d'Asie du Sud-Est."
        pub == "tiiuae" -> "TII (UAE) Falcon : open-source arabe/anglais."
        pub == "bigcode" -> "BigCode : open-source dedie generation de code."
        pub == "adept" -> "Adept : multimodal et agent navigation web."
        pub == "stockmark" -> "Stockmark : japonais bilingue business."
        pub == "upstage" -> "Upstage : coreen avec architecture innovante."
        pub == "sarvamai" -> "Sarvam AI : specialise langues indiennes (Hindi et 10 autres)."
        pub == "zyphra" -> "Zyphra Zamba : hybride SSM-attention efficace."
        pub == "bytedance" -> "ByteDance : open-source chinois pour reasoning long contexte."
        pub == "baai" -> "BAAI : embeddings et retrieval haute qualite multilingue."
        pub == "black-forest-labs" -> "Black Forest Labs FLUX : generation d'images haute qualite open-weights."
        pub == "stabilityai" -> "Stability AI Stable Diffusion : generation d'images open-source."

        lower.contains("embed") -> "Modele d'embeddings pour recherche semantique et bases de connaissances."
        lower.contains("rerank") -> "Modele reranker pour ameliorer les resultats de recherche."
        lower.contains("whisper") || lower.contains("asr") -> "Modele speech-to-text pour transcription audio."
        lower.contains("tts") -> "Modele text-to-speech pour synthese vocale."
        lower.contains("code") || lower.contains("starcoder") ->
            "Modele specialise dans la generation et l'analyse de code."
        lower.contains("vision") || lower.contains("-vl-") || lower.contains("vlm") ->
            "Modele Vision-Language pour analyser des images et discuter en multimodal."
        lower.contains("guard") || lower.contains("safety") ->
            "Modele de moderation pour pipelines IA securises."
        lower.contains("flux") || lower.contains("diffusion") || lower.contains("sdxl") ->
            "Modele de generation d'images a partir de descriptions textuelles."

        else -> null
    }
}

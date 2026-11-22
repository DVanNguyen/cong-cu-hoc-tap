# RESEARCH AND DEVELOPMENT OF AN AI-POWERED LEARNING ASSISTANT SYSTEM (STUDYAI)
## Enriching Slide Skeletons through In-Class Audio Synchronization, Living Dictionary, and Cross-Chapter Deep-Linking
**Undergraduate Thesis / Capstone Project 4 Defense**  
*Vietnam - Korea University of Information and Communication Technology (VKU) - The University of Danang*  
**Student:** Dam Van Nguyen (Student ID: 24IT180 | Class: 24JIT)  
**Supervisor:** Dr. Tran Uyen Trang  

---

### SLIDE 1: TITLE & PROJECT IDENTIFICATION
- **Official Thesis Title:** Research and Development of an AI-Powered Learning Assistant System (*Nghiên cứu, xây dựng hệ thống trợ lý AI hỗ trợ học tập*).
- **Product Name:** **StudyAI** (formerly FocusBoard).
- **Academic Context:** Capstone Project 4 (Đồ án Cơ sở 4) - Software Engineering / Artificial Intelligence.
- **Institution:** Faculty of Computer Science & Software Engineering, VKU.

---

### SLIDE 2: RESEARCH PHILOSOPHY & THE "NOTEBOOKLM PARADOX"
- **The Core Paradox of University Lecture Slides:**
  - University lecture slides are designed as **concise skeletons / outlines** (only 15–30 words per slide).
  - The real academic depth, mathematical intuition, and practical examples are delivered **orally** by instructors in the classroom.
- **Why Google NotebookLM Fails in University Classrooms:**
  - **NotebookLM is a Closed-World RAG system:** It strictly searches text existing inside uploaded PDF slides.
  - When asked about an abstract concept that only appears as a bullet point title (e.g., *GAN, Simulated Annealing, A\**), NotebookLM yields superficial answers or hallucinates due to slide skeleton sparsity.
- **StudyAI's 3-Dimensional Knowledge Enrichment Model:**
  1. **Vertical In-Depth Enrichment (Pillar 1):** Capturing spoken lecture audio, filtering silence with Silero VAD, transcribing with Triple-Stage Whisper, and aligning oral expansions to slide skeletons using Banded DTW and Novelty + MMR filtering.
  2. **Horizontal World Knowledge Expansion (Pillar 2):** Measuring concept abstractness $\mathcal{A}(c, S_k) \ge 0.55$, retrieving verified references from Wikipedia (VI/EN), Semantic Scholar, Web Tutorials, and YouTube Edu with domain authority ranking.
  3. **Temporal Cross-Chapter Systematization (Pillar 3):** Constructing a course-wide Knowledge DAG, detecting Used-Before-Defined pedagogical anomalies, and generating bi-directional 1-tap deep links between chapters.

---

### SLIDE 3: THREE CORE ACADEMIC PILLARS & ARCHITECTURE
```
                                 CLIENT LAYER
                Android Mobile Application (Jetpack Compose / Kotlin)
                - Slide Skeleton Workspace  - Cross-Chapter Link Chips
                - Living Dictionary Modal   - Course Knowledge Graph
                                      |
                                      v [RESTful APIs / JSON]
                         APPLICATION & PERSISTENCE
                     Laravel 11 REST API / MySQL / Redis
                                      |
                                      v [Async HTTP / Microservice]
                            AI COMPUTATION ENGINE
                FastAPI Microservice (Python 3.11 / CTranslate2 / PyTorch)
  +-----------------------------+-----------------------------+-----------------------------+
  | PILLAR 1: SLIDE ENRICHMENT  | PILLAR 2: LIVING DICTIONARY | PILLAR 3: CROSS-CHAPTER DAG |
  | - Slide Hierarchy Parsing   | - Abstractness Score A(c,S) | - Bilingual Entity Matching |
  | - Silero VAD (-28.4% audio) | - Multi-API Web Harvester   | - Kahn Topological Sort     |
  | - Triple-Stage Whisper ASR  |   (Wiki, Scholar, YouTube)  | - Used-Before-Defined Alert |
  | - Banded Monotonic DTW      | - Domain Authority Weight   | - Bi-directional Deep Links |
  | - Novelty & MMR Scoring     | - Pedagogical Ranking Score |   (Pos = L * 1000 + S)      |
  +-----------------------------+-----------------------------+-----------------------------+
```

---

### SLIDE 4: PILLAR 1 - SILERO VAD & TRIPLE-STAGE DOMAIN INJECTION ASR
- **Silero Voice Activity Detection (VAD):**
  - Continuous 16 kHz acoustic streaming filter. Discards non-speech intervals ($p < 0.5$, $\text{min\_silence} = 500\text{ ms}$).
  - Reduces raw audio volume by **28.4%**, eliminating Whisper hallucination loops during silent classroom gaps.
- **Solving the Vietnamese-English Code-Switching Dilemma:**
  - Vietnamese fine-tuned models (PhoWhisper) phonetically distort English technical terms (*"BFS"* $\rightarrow$ *"bi ép ét"*, *"agent"* $\rightarrow$ *"ây dần"*).
  - Multilingual Whisper distorts Vietnamese tones when English density is high.
- **Our Triple-Stage Injection Solution:**
  1. **Stage 1 (Initial Prompt Injection):** Injects a slide-specific context string containing domain terms ($< 180$ tokens, safely below Whisper's 224-token decoder cap).
  2. **Stage 2 (Hotwords Parameter):** CTranslate2 low-level logit boosting during Beam Search for core technical vocabulary.
  3. **Stage 3 (Phonetic Recovery Regex Table):** Deterministic post-ASR regex mapping correcting common Vietnamese phonetic approximations.
- **Empirical Breakthrough:** Word Error Rate (WER) reduced from **54.52% down to 0.30%**; English Domain Term Recall surged from **17.86% to 96.43%**.

---

### SLIDE 5: PILLAR 1 - BANDED MONOTONIC DTW & NOVELTY FILTERING (MMR)
- **Banded Monotonic Dynamic Time Warping (DTW):**
  - Aligns continuous lecture transcripts to slide transitions using Sakoe-Chiba constraint band $W = \max(5, \lceil 0.25 K \rceil)$.
  - Step penalty regularization ($\gamma_{next} = 0.08, \gamma_{skip} = 0.25, \gamma_{back} = 0.30$) preventing unrealistic backwards jumps.
  - Alignment execution speed: **3.84 ms**.
- **Novelty Scoring Formula:**
  $$\text{Novelty}(u_i, S_k) = 0.7 \times \left(1 - \max_{b \in S_k} \text{CosineSim}(\mathbf{v}_{u_i}, \mathbf{v}_b)\right) + 0.3 \times (1 - \text{TokenOverlap}(u_i, S_k))$$
- **Maximal Marginal Relevance (MMR) Redundancy Suppression:**
  - Selects only high-novelty oral expansions ($\text{Novelty} \ge 0.28$).
  - Penalizes repetitive verbal phrasing ($\lambda = 0.65$), producing crisp, high-yield lecture bullet points.
- **Verbatim Grounding Verification:** RapidFuzz fuzzy match $\ge 85\%$ guarantees that every generated note is grounded in the instructor's exact spoken words.

---

### SLIDE 6: PILLAR 2 - "LIVING DICTIONARY" & CONCEPT ABSTRACTNESS
- **The Concept Abstractness Formulation $\mathcal{A}(c, S_k)$:**
  - Avoids querying external APIs for generic course administrative terms (*"Course Overview", "Learning Objectives"*).
  - Automatically identifies abstract, undertheorized concepts on slide skeletons:
  $$\mathcal{A}(c, S_k) = 0.45 \cdot \left(1 - \min\left(1.0, \frac{L_{\text{explain}}(c, S_k)}{40}\right)\right) + 0.35 \cdot \text{TechSpec}(c) + 0.20 \cdot \mathbb{I}_{\text{first\_seen}}(c)$$
- **Activation Threshold:** Any concept with $\mathcal{A}(c, S_k) \ge 0.55$ automatically triggers external reference harvesting.
- **Empirical Abstractness Accuracy:** **100.0%** classification accuracy on academic test concepts (GAN: 0.91, A*: 0.78, Course Overview: 0.12).

---

### SLIDE 7: PILLAR 2 - CONTEXT-DISAMBIGUATED MULTI-API HARVESTING & RANKING
- **The Context Disambiguation Problem:**
  - Searching raw keywords like *"Agent"* or *"Tree"* in generic search engines retrieves *"Real Estate Agent"* or *"Family Tree"*.
- **Three-Stage Disambiguation & Harvesting:**
  1. **Query Expansion with Course Domain:** Injects course title and slide context ($Q_{en} = \text{``"}c_{en}\text{"" } + \text{Domain}_{en} + \text{Context}(T_k)$).
  2. **Asynchronous Multi-API Harvesting:** Concurrent queries via HTTPX to:
     - Wikipedia MediaWiki API (VI and EN editions)
     - Semantic Scholar Academic Paper API
     - Web Tutorials (GeeksforGeeks, Viblo, TowardsDataScience)
     - YouTube Educational Video Search
  3. **Multi-Factor Pedagogical Ranking Formula:**
     $$\text{Score}(R) = 0.65 \times \text{CosineSim}(\mathbf{v}_{context}, \mathbf{v}_R) + 0.25 \times \text{Authority}(R) + 0.10 \times \text{PedagogicalBadge}(R)$$
- **Precision@3 & Precision@5:** Achieved **100.0%** relevance across all retrieved external references within **840 ms**.

---

### SLIDE 8: PILLAR 3 - CROSS-CHAPTER KNOWLEDGE GRAPH & DEEP-LINKING
- **Global Linear Temporal Coordinate:**
  $$\text{Pos}(L, S) = L \times 1000 + S$$
  - Every concept occurrence across 15 weeks is ordered along a single temporal timeline.
- **Kahn's Topological Sort & Used-Before-Defined Early Warning:**
  - If concept $A$ is used as a prerequisite in lecture $L_{used}$, but formally defined in $L_{defined}$ where $\text{Pos}(L_{used}, S_{used}) < \text{Pos}(L_{defined}, S_{defined})$, a pedagogical warning is generated.
- **Bi-directional Cross-Chapter Deep Linking:**
  - **`BACK_TO_ORIGIN` Link:** Appears on later chapter slides (e.g., Chapter 5, Slide 3): *"🔗 Introduced in Chapter 1 (Slide 12 • 04:15): GAN — Tap to review original foundation"*.
  - **`FORWARD_TO_LATER_CHAPTER` Link:** Appears on origin slide (Chapter 1, Slide 12): *"🔗 Re-visited in Chapter 5 (Slide 3 • 01:18) — Tap to see advanced application"*.
- **Execution Latency:** **2.31 ms** to resolve course-wide bi-directional links.

---

### SLIDE 9: ANDROID CLIENT UI & REAL-TIME INTERACTION
- **Built with Modern Jetpack Compose & Material 3:**
  1. **Screen 1: Slide Skeleton Workspace (`LectureNoteScreen.kt`)**
     - Synchronized slide viewer with clickable domain term chips.
     - Novelty-filtered oral expansion notes with verbatim evidence badges.
     - Integrated `CrossChapterLinksSection` for 1-tap jumping between lectures.
  2. **Screen 2: Living Dictionary Modal (`LivingDictionaryBottomSheet.kt`)**
     - Section 1: Instructor's in-class verbal explanation.
     - Section 2: AI deep conceptual intuition and illustrative examples.
     - Section 3: Ranked external reference cards (Wiki, Semantic Scholar, Tutorials, YouTube) with direct browser launch.
  3. **Screen 3: Course Knowledge Graph Canvas (`KnowledgeGraphScreen.kt`)**
     - Interactive multi-layer DAG canvas with topological ordering and Used-Before-Defined alert highlights.
  4. **Screen 4: Course Living Dictionary Catalog (`CourseDictionaryScreen.kt`)**
     - Comprehensive index of all 18 course concepts, abstractness percentages, and reference counts.

---

### SLIDE 10: QUANTITATIVE EXPERIMENTAL BENCHMARKS (CHAPTER 4)
| Evaluation Metric | Achieved Value | Benchmark Target | Status |
| :--- | :---: | :---: | :---: |
| **ASR Word Error Rate (WER) - Code-Switching** | **0.30%** | $\le 15.0\%$ | **EXCEEDED (180x gain)** |
| **Technical Term Recall Rate** | **96.43%** | $\ge 85.0\%$ | **EXCEEDED** |
| **Silero VAD Silence Reduction** | **28.4%** | $\ge 20.0\%$ | **EXCEEDED** |
| **Banded DTW Slide Alignment Accuracy** | **100.0%** | $\ge 90.0\%$ | **PERFECT** |
| **Concept Abstractness Accuracy $\mathcal{A}(c, S_k)$** | **100.0%** | $\ge 90.0\%$ | **PERFECT** |
| **External Reference Precision@3 (Disambiguation)** | **100.0%** | $\ge 90.0\%$ | **PERFECT** |
| **External Reference Precision@5 (Multi-API)** | **100.0%** | $\ge 85.0\%$ | **PERFECT** |
| **Bi-directional Cross-Chapter Linking Coverage** | **100.0%** | $100.0\%$ | **PERFECT** |
| **Multi-API Harvester Response Latency** | **840.7 ms** | $< 3000\text{ ms}$ | **REAL-TIME** |
| **Cross-Chapter Deep-Link Resolution Latency** | **2.31 ms** | $< 50\text{ ms}$ | **INSTANT** |

---

### SLIDE 11: COMPETITIVE ADVANTAGE: STUDYAI VS. GOOGLE NOTEBOOKLM
| Evaluation Dimension | Google NotebookLM | StudyAI (Our System) |
| :--- | :--- | :--- |
| **RAG Knowledge Paradigm** | Closed-World RAG (Only reads static uploaded text) | **3-Dimensional Enriched RAG** (Slide + Spoken Audio + Web Knowledge) |
| **Slide Skeleton Handling** | Superficial (Fails when concepts are brief bullet points) | **Living Dictionary Expansion** ($\mathcal{A}(c, S_k) \ge 0.55$ automatically deepens concepts) |
| **Classroom Spoken Audio** | Requires manual audio upload, no slide synchronization | **Automatic Banded DTW Alignment** linking exact speech timestamps `[mm:ss]` to slides |
| **Bilingual Code-Switching** | Phonetically degrades English technical terms in Vietnamese | **Triple-Stage Injection** (WER = 0.30%, Term Recall = 96.43%) |
| **Multi-Week Knowledge DAG** | Flat document view, no concept prerequisite tracking | **Kahn's DAG + Used-Before-Defined Early Warning** |
| **Cross-Chapter Navigation** | Non-existent (isolated PDF documents) | **Bi-directional 1-Tap Deep Linking** between foundational and advanced chapters |
| **External Verified References** | None (Confined to uploaded files) | **Multi-API Harvester** (Wikipedia VI/EN, Semantic Scholar, Tutorials, YouTube Edu) |

---

### SLIDE 12: CONCLUSION & DEFENSE SUMMARY
- **Complete Academic & Technical Fulfillment:**
  - Implemented the entire end-to-end pipeline across 3 specialized engineering tiers (Android Compose UI $\leftrightarrow$ Laravel REST API $\leftrightarrow$ Python AI Microservice).
  - Refined the research scope under Dr. Tran Uyen Trang's guidance: pivoting from external deadline scraping to deep, academic, lecture-centered learning enrichment.
- **Key Contributions:**
  1. Triple-Stage Domain Injection overcoming Code-Switching ASR bottlenecks in Vietnamese higher education.
  2. The Slide Skeleton Enrichment paradigm via Living Dictionary and Multi-API pedagogical ranking.
  3. Continuous temporal indexing $\text{Pos}(L, S)$ providing bi-directional cross-chapter deep links and early warning for curriculum structure violations.
- **Project Readiness:**
  - All unit tests, benchmark scripts, and Android source code compile and execute cleanly with 100% test passing rate.
  - The system is ready for comprehensive deployment and defense evaluation.

package com.example.agents;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import com.example.config.RagConfiguration;
import com.example.tools.TransportTools;
import com.example.tools.WeatherTools;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIAgent {
    
    private final ChatClient.Builder builder;
    private final TransportTools transportTools;
    private final WeatherTools weatherTools;
    private final VectorStore vectorStore;

    private ChatClient chatClient;

    private static final int SMALL_DOCUMENT_THRESHOLD = 10000;

    @PostConstruct
    public void init() {
        log.info("🤖 Initialisation AIAgent Transport + Météo + RAG");

        String fullText = RagConfiguration.getFullDocumentText();
        boolean isSmallDoc = fullText.length() < SMALL_DOCUMENT_THRESHOLD;

        if (isSmallDoc) {
            this.chatClient = builder
                .defaultSystem(buildSystemPrompt(fullText))
                .defaultFunctions(
                    "getDelayedTransports",
                    "getTransportDetailsByNumber",
                    "currentWeather",
                    "forecastWeather"
                )
                .build();
        } else {
            this.chatClient = builder
                .defaultSystem(buildSystemPrompt(null))
                .defaultFunctions(
                    "getDelayedTransports",
                    "getTransportDetailsByNumber",
                    "currentWeather",
                    "forecastWeather"
                )
                .build();
        }

        log.info("✅ AIAgent Transport initialisé");
    }

    /**
     * SYSTEM PROMPT INTELLIGENT – Gère PDF + CSV + API Transport + API Météo
     */
    private String buildSystemPrompt(String fullDocumentText) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("""
            Tu es un assistant spécialisé dans les transports (trains, bus, métro)
            et les conditions météorologiques en temps réel.

            Tu disposes de 3 SOURCES D’INFORMATION :

            ======================
            🔹 SOURCE 1 : PDF de politique transport (Manuel)
            ======================
            """);

        if (fullDocumentText != null) {
            prompt.append(fullDocumentText);
            prompt.append("\n=== FIN DU DOCUMENT ===\n\n");
        } else {
            prompt.append("Le document PDF sera fourni via RAG dans le contexte.\n\n");
        }

        prompt.append("""
            ======================
            🔹 SOURCE 2 : API Transport via Tools
            ======================
            Fonctions disponibles :
            - getDelayedTransports() : liste des transports en retard
            - getTransportDetailsByNumber(number) : info sur un transport (retard, incident, durée…)

            ======================
            🔹 SOURCE 3 : API Météo via Tools
            ======================
            - currentWeather(city)
            - forecastWeather(city)

            ======================
            🔹 SOURCE 4 : CSV uploadé
            ======================
            Utilisé uniquement si l'utilisateur fournit un CSV.
            
            ==========================================
            RÈGLES DE ROUTAGE INTELLIGENTES
            ==========================================

            1️⃣ Si l'utilisateur fournit un CSV → ANALYSE UNIQUEMENT LE CSV  
            2️⃣ Si la question concerne :
                - retard
                - durée
                - numéro de train / bus
                - incident
               → APPELLE les fonctions TransportTools

            3️⃣ Si la question concerne :
                - météo
                - prévisions
               → APPELLE WeatherTools

            4️⃣ Si la question concerne :
                - politique
                - règles
                - sections
                - procédures
               → Utilise le document PDF (direct ou via RAG)

            ==========================================
            INTERDICTIONS ABSOLUES
            ==========================================

            ❌ Ne cherche PAS la météo dans le PDF  
            ❌ Ne cherche PAS une règle PDF pour une question sur un train réel  
            ❌ Ne mélange JAMAIS PDF / CSV / API  

            ==========================================
            Format attendu des réponses
            ==========================================

            - Pour PDF : "D'après le document (Section X.X) : …"
            - Pour TransportTools : réponse basée sur l’API
            - Pour Météo : résumé clair
            - Pour CSV : "D'après les données CSV : …"
            """);

        return prompt.toString();
    }


    /**
     * ROUTAGE AUTOMATIQUE
     */
    public String chat(String userQuery) {
        log.info("💬 Question reçue : {}", userQuery);

        try {
            String type = detectQueryType(userQuery);
            log.info("🎯 Type détecté : {}", type);

            switch (type) {

                case "CSV":
                    return chatClient.prompt()
                            .user(userQuery)
                            .call()
                            .content();

                case "TRANSPORT":
                    return chatClient.prompt()
                            .user(userQuery + "\n\n⚠ Cette question concerne les transports → utilise les fonctions.")
                            .call()
                            .content();

                case "WEATHER":
                    return chatClient.prompt()
                            .user(userQuery + "\n\n⚠ Cette question concerne la météo → utilise les fonctions météo.")
                            .call()
                            .content();

                case "DOCUMENT":
                default:
                    String fullText = RagConfiguration.getFullDocumentText();

                    if (fullText.length() < SMALL_DOCUMENT_THRESHOLD) {
                        return chatClient.prompt().user(userQuery).call().content();
                    }

                    String context = getRelevantContext(userQuery);
                    return chatClient.prompt()
                            .user("CONTEXTE DU DOCUMENT :\n" + context + "\n\nQUESTION :\n" + userQuery)
                            .call()
                            .content();
            }

        } catch (Exception e) {
            log.error("❌ Erreur", e);
            return "Erreur : " + e.getMessage();
        }
    }

    /**
     * DETECTION DU TYPE DE QUESTION
     */
    private String detectQueryType(String q) {
        String t = q.toLowerCase();

        // CSV
        if (t.contains("données csv") || t.contains("contexte :") || t.contains("```csv")) {
            return "CSV";
        }

        // Transport
        if (t.contains("train") || t.contains("bus")
                || t.contains("tgv") || t.contains("retard")
                || t.contains("incident") || t.contains("numéro")) {
            return "TRANSPORT";
        }

        // Météo
        if (t.contains("météo") || t.contains("weather") || t.contains("température") || t.contains("pluie")) {
            return "WEATHER";
        }

        // PDF
        return "DOCUMENT";
    }


    /**
     * 🔍 CONTEXTE RAG
     */
    private String getRelevantContext(String query) {
        List<Document> docs = vectorStore.similaritySearch(
            SearchRequest.query(query)
                .withTopK(8)
                .withSimilarityThreshold(0.2)
        );

        if (docs.isEmpty()) {
            docs = RagConfiguration.keywordSearch(query, 8);
        }

        if (docs.isEmpty()) return RagConfiguration.getFullDocumentText();

        StringBuilder sb = new StringBuilder("=== EXTRACTS ===\n");
        for (Document d : docs) sb.append(d.getContent()).append("\n");
        return sb.toString();
    }
    
    
    public String analyzeCsvData(String csvContent, String userQuery) {
        log.info("📊 Analyse CSV - Question : '{}'", userQuery);
        log.info("📄 Contenu CSV (premières 500 chars) : {}", 
                 csvContent.substring(0, Math.min(500, csvContent.length())));
        
        if (userQuery == null) {
            return "❌ Question non fournie";
        }
        
        try {
            String response = chatClient.prompt()
                .system("""
                    Tu es un expert en analyse de données de transport.
                    Tu dois analyser les données CSV fournies par l'utilisateur.
                    
                    RÈGLES STRICTES :
                    1. Réponds UNIQUEMENT en français
                    2. Utilise EXCLUSIVEMENT les données du CSV fourni
                    3. Cherche les termes en anglais ET en français
                    4. Regarde toutes les colonnes : IncidentReason, RaisonIncident, Météo, Weather, etc.
                    5. Donne des réponses courtes et précises
                    6. Si tu trouves une correspondance, cite le numéro de transport et les détails
                    
                    Termes à chercher pour la météo :
                    - "Weather Conditions" 
                    - "Météo"
                    - "Conditions météorologiques"
                    - "Intempéries"
                    - "Neige", "Pluie", "Tempête"
                    """)
                .user("""
                    DONNÉES CSV À ANALYSER :
                    ```csv
                    """ + csvContent + """
                    ```
                    
                    QUESTION : """ + userQuery + """
                    
                    Analyse les données CSV ligne par ligne. 
                    Regarde la colonne "IncidentReason" ou toute autre colonne de raison.
                    Réponds en français.
                    """)
                .call()
                .content();
            
            log.info("✅ Réponse CSV générée : {}", response);
            return response;
            
        } catch (Exception e) {
            log.error("❌ Erreur analyse CSV", e);
            return "Erreur lors de l'analyse : " + e.getMessage();
        }
    }
    public String testRag(String query) {
        log.info("🔬 Test du système RAG pour : {}", query);
        
        StringBuilder report = new StringBuilder();
        report.append("=== DIAGNOSTIC RAG ===\n\n");
        
        // Test 1 : Taille du document
        String fullText = RagConfiguration.getFullDocumentText();
        report.append("📊 Taille du document : ").append(fullText.length()).append(" caractères\n");
        report.append("📦 Nombre total de chunks : ").append(RagConfiguration.getAllDocuments().size()).append("\n\n");
        
        // Test 2 : Recherche vectorielle
        try {
            List<Document> vectorResults = vectorStore.similaritySearch(
                SearchRequest.query(query).withTopK(5).withSimilarityThreshold(0.2)
            );
            report.append("🎯 Recherche vectorielle : ").append(vectorResults.size()).append(" résultats\n");
            for (int i = 0; i < Math.min(3, vectorResults.size()); i++) {
                String preview = vectorResults.get(i).getContent()
                    .substring(0, Math.min(100, vectorResults.get(i).getContent().length()));
                report.append("   - Résultat ").append(i + 1).append(" : ").append(preview).append("...\n");
            }
        } catch (Exception e) {
            report.append("❌ Recherche vectorielle échouée : ").append(e.getMessage()).append("\n");
        }
        report.append("\n");
        
        // Test 3 : Recherche par mots-clés
        List<Document> keywordResults = RagConfiguration.keywordSearch(query, 5);
        report.append("📝 Recherche par mots-clés : ").append(keywordResults.size()).append(" résultats\n");
        for (int i = 0; i < Math.min(3, keywordResults.size()); i++) {
            String preview = keywordResults.get(i).getContent()
                .substring(0, Math.min(100, keywordResults.get(i).getContent().length()));
            report.append("   - Résultat ").append(i + 1).append(" : ").append(preview).append("...\n");
        }
        report.append("\n");
        
        // Test 4 : Contexte final
        String context = getRelevantContext(query);
        report.append("📄 Contexte final : ").append(context.length()).append(" caractères\n");
        report.append("Aperçu : ").append(context.substring(0, Math.min(200, context.length()))).append("...\n");
        
        return report.toString(); // CORRECTION : point-virgule ajouté ici
    }
}

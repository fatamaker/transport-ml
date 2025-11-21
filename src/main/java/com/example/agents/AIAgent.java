package com.example.agents;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import com.example.config.RagConfiguration;
import com.example.tools.TransportTools;

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
    private final TransportTools transportTools; // Renommé pour cohérence
    private final VectorStore vectorStore;
    private ChatClient chatClient;
    
    // Seuil de tokens pour décider si on envoie tout le document
    private static final int SMALL_DOCUMENT_THRESHOLD = 10000; // ~3 pages

    @PostConstruct
    public void init() {
        log.info("🤖 Initialisation de l'AIAgent UNIVERSEL avec RAG Hybride");
        
        // Vérifier si le document est assez petit pour être envoyé en entier
        String fullText = RagConfiguration.getFullDocumentText();
        boolean isSmallDocument = fullText.length() < SMALL_DOCUMENT_THRESHOLD;
        
        if (isSmallDocument) {
            log.info("📄 Document petit ({} caractères) - Mode contexte complet activé", fullText.length());
            
            // MODE 1 : Document complet dans le system prompt
            this.chatClient = builder
                .defaultSystem("""
                    Tu es un assistant expert qui répond UNIQUEMENT à partir du document suivant.
                    
                    === DOCUMENT COMPLET ===
                    """ + fullText + """
                    
                    === FIN DU DOCUMENT ===
                    
                    INSTRUCTIONS :
                    1. Lis ATTENTIVEMENT tout le document ci-dessus
                    2. Réponds UNIQUEMENT avec les informations du document
                    3. Cite toujours la section et les valeurs exactes
                    4. Format : "D'après le document (Section X.X) : [détails]"
                    5. Si l'info n'est pas dans le document : dis "Information non trouvée dans le document"
                    
                    Ne donne JAMAIS d'informations générales ou inventées.
                    """)
                .defaultFunctions("getDelayedTransports", "getTransportDetailsByNumber") // Corrigé pour TransportTools
                .build();
        } else {
            log.info("📚 Document volumineux ({} caractères) - Mode RAG hybride activé", fullText.length());
            
            // MODE 2 : RAG classique pour documents volumineux
            this.chatClient = builder
                .defaultSystem("""
                    Tu es un assistant expert qui répond à partir des extraits de documents fournis.
                    
                    INSTRUCTIONS :
                    1. Le contexte ci-dessous contient les passages pertinents du document
                    2. Lis ATTENTIVEMENT tous les extraits fournis
                    3. Réponds en citant les sections et valeurs exactes
                    4. Format : "D'après le document (Section X.X) : [détails]"
                    5. Si l'info n'est pas dans le contexte : dis "Information non trouvée dans les extraits fournis"
                    
                    Ne donne JAMAIS d'informations générales ou inventées.
                    """)
                .defaultFunctions("getDelayedTransports", "getTransportDetailsByNumber") // Corrigé pour TransportTools
                .build();
        }
        
        log.info("✅ AIAgent UNIVERSEL initialisé avec succès");
    }

    public String chat(String userQuery) {
        log.info("💬 Question reçue : {}", userQuery);
        
        try {
            // Récupérer le contexte pertinent
            String context = getRelevantContext(userQuery);
            
            // Si document petit, le contexte est déjà dans le system prompt
            if (RagConfiguration.getFullDocumentText().length() < SMALL_DOCUMENT_THRESHOLD) {
                log.info("📄 Utilisation du contexte complet du system prompt");
                String response = chatClient.prompt()
                        .user(userQuery)
                        .call()
                        .content();
                
                log.info("✅ Réponse générée ({} caractères)", response.length());
                return response;
            } else {
                // Pour documents volumineux, ajouter le contexte à la requête
                String enrichedQuery = """
                    CONTEXTE DU DOCUMENT :
                    """ + context + """
                    
                    QUESTION :
                    """ + userQuery;
                
                log.info("📚 Contexte ajouté ({} caractères)", context.length());
                
                String response = chatClient.prompt()
                        .user(enrichedQuery)
                        .call()
                        .content();
                
                log.info("✅ Réponse générée ({} caractères)", response.length());
                return response;
            }
            
        } catch (Exception e) {
            log.error("❌ Erreur lors du traitement de la requête", e);
            return "Désolé, une erreur s'est produite : " + e.getMessage();
        }
    }
    
    /**
     * Récupère le contexte pertinent avec stratégie hybride améliorée
     */
    private String getRelevantContext(String query) {
        log.info("🔍 Recherche de contexte pertinent pour : {}", query);
        
        List<Document> relevantDocs = new ArrayList<>();
        
        // STRATÉGIE AMÉLIORÉE : Recherche avec variations
        List<String> searchVariations = generateSearchVariations(query);
        
        for (String searchTerm : searchVariations) {
            if (!relevantDocs.isEmpty()) break;
            
            try {
                relevantDocs = vectorStore.similaritySearch(
                    SearchRequest.query(searchTerm)
                        .withTopK(8)
                        .withSimilarityThreshold(0.15)
                );
                log.info("🎯 Recherche avec '{}' : {} documents trouvés", searchTerm, relevantDocs.size());
            } catch (Exception e) {
                log.warn("⚠ Recherche échouée pour '{}' : {}", searchTerm, e.getMessage());
            }
        }
        
        // STRATÉGIE 2 : Si vectorielle échoue, recherche par mots-clés
        if (relevantDocs.isEmpty()) {
            log.info("🔄 Passage à la recherche par mots-clés...");
            for (String searchTerm : searchVariations) {
                relevantDocs = RagConfiguration.keywordSearch(searchTerm, 8);
                if (!relevantDocs.isEmpty()) {
                    log.info("📝 Recherche par mots-clés avec '{}' : {} documents", searchTerm, relevantDocs.size());
                    break;
                }
            }
        }
        
        // STRATÉGIE 3 : Recherche manuelle pour termes spécifiques
        if (relevantDocs.isEmpty() && query.toLowerCase().contains("retard")) {
            log.info("🔎 Recherche manuelle des sections sur les retards...");
            relevantDocs = searchManualSections();
        }
        
        // STRATÉGIE 4 : Si tout échoue, retourner tout le document
        if (relevantDocs.isEmpty()) {
            log.warn("⚠ Aucun résultat - Utilisation du document complet");
            return RagConfiguration.getFullDocumentText();
        }
        
        return buildContextFromDocuments(relevantDocs);
    }
    
    /**
     * Génère des variations de recherche pour améliorer les résultats
     */
    private List<String> generateSearchVariations(String originalQuery) {
        List<String> variations = new ArrayList<>();
        variations.add(originalQuery);
        
        String lowerQuery = originalQuery.toLowerCase();
        
        if (lowerQuery.contains("retard")) {
            variations.add("retard mineur 0 à 15 minutes");
            variations.add("retard important 15 à 60 minutes");
            variations.add("retard critique 60 minutes");
            variations.add("CHAPITRE 1 POLITIQUE GESTION RETARDS");
            variations.add("dédommagement retard");
            variations.add("procédure retard train");
        }
        
        if (lowerQuery.contains("tgv") || lowerQuery.contains("train")) {
            variations.add("train procédure");
            variations.add("transport ferroviaire");
        }
        
        if (lowerQuery.contains("manuel")) {
            variations.add("MANUEL D'EXPLOITATION");
            variations.add("CHAPITRE");
            variations.add("section procédure");
        }
        
        return variations;
    }
    
    /**
     * Recherche manuelle des sections sur les retards
     */
    private List<Document> searchManualSections() {
        List<Document> allDocs = RagConfiguration.getAllDocuments();
        List<Document> retardDocs = new ArrayList<>();
        
        for (Document doc : allDocs) {
            String content = doc.getContent().toLowerCase();
            if (content.contains("retard") || 
                content.contains("chapître 1") || 
                content.contains("politique") ||
                content.contains("dédommagement") ||
                content.contains("minutes")) {
                retardDocs.add(doc);
                if (retardDocs.size() >= 5) break;
            }
        }
        
        log.info("🔎 Recherche manuelle : {} documents sur les retards trouvés", retardDocs.size());
        return retardDocs;
    }
    
    private String buildContextFromDocuments(List<Document> documents) {
        StringBuilder context = new StringBuilder();
        context.append("=== INFORMATIONS PERTINENTES DU MANUEL ===\n\n");
        
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            context.append("--- Extrait ").append(i + 1).append(" ---\n");
            context.append(doc.getContent()).append("\n\n");
            
            log.info("📄 Extrait {} : {}...", 
                     i + 1, 
                     doc.getContent().substring(0, Math.min(100, doc.getContent().length())));
        }
        
        context.append("=== FIN DES INFORMATIONS ===\n\n");
        return context.toString();
    }
    
    /**
     * Méthode de test pour diagnostiquer le RAG
     */
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
}
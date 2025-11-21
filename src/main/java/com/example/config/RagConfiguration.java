package com.example.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;



@Configuration
public class RagConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RagConfiguration.class);

    @Value("classpath:pdfs/manuel.pdf")
    private Resource pdfResource;
    
    // Store pour garder TOUS les documents en mémoire (backup)
    private static final List<Document> ALL_DOCUMENTS = new ArrayList<>();
    private static String FULL_DOCUMENT_TEXT = "";

    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        SimpleVectorStore simpleVectorStore = new SimpleVectorStore(embeddingModel);

        if (pdfResource.exists()) {
            try {
                log.info("📖 Chargement du PDF : {}", pdfResource.getFilename());

                // 1. Lecture du PDF
                PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(pdfResource);
                List<Document> documents = pdfReader.get();
                log.info("✅ Nombre de pages lues : {}", documents.size());

                // 2. Construire le texte complet pour backup
                StringBuilder fullText = new StringBuilder();
                for (Document doc : documents) {
                    fullText.append(doc.getContent()).append("\n\n");
                }
                FULL_DOCUMENT_TEXT = fullText.toString();
                log.info("📝 Texte complet sauvegardé : {} caractères", FULL_DOCUMENT_TEXT.length());

                // 3. Découpage intelligent en chunks
                TokenTextSplitter splitter = new TokenTextSplitter(
                    300,   // Taille moyenne des chunks
                    100,   // Overlap généreux pour ne rien perdre
                    10,    // Minimum 10 caractères
                    500,   // Maximum 500 chunks
                    true   // Garder les séparateurs
                );

                List<Document> splitDocuments = splitter.apply(documents);
                log.info("✂️ Nombre de chunks créés : {}", splitDocuments.size());

                // 4. Sauvegarder TOUS les chunks en mémoire (backup)
                ALL_DOCUMENTS.clear();
                ALL_DOCUMENTS.addAll(splitDocuments);

                // 5. Afficher les chunks pour debug
                for (int i = 0; i < Math.min(10, splitDocuments.size()); i++) {
                    String content = splitDocuments.get(i).getContent();
                    log.info("📦 Chunk {} (150 premiers chars) : {}", 
                             i, content.substring(0, Math.min(150, content.length())));
                }

                // 6. Indexation dans le VectorStore
                simpleVectorStore.add(splitDocuments);
                log.info("✅ {} chunks ingérés dans le VectorStore !", splitDocuments.size());

            } catch (Exception e) {
                log.error("❌ Erreur lors du chargement du PDF", e);
                e.printStackTrace();
            }
        } else {
            log.warn("⚠️ Fichier politique.pdf introuvable dans resources/docs/");
        }

        return simpleVectorStore;
    }

    // Méthode pour récupérer TOUS les documents (backup)
    public static List<Document> getAllDocuments() {
        return new ArrayList<>(ALL_DOCUMENTS);
    }

    // Méthode pour récupérer le texte complet (backup)
    public static String getFullDocumentText() {
        return FULL_DOCUMENT_TEXT;
    }
    
    // Méthode de recherche par mots-clés (backup si vectorielle échoue)
    public static List<Document> keywordSearch(String query, int maxResults) {
        List<Document> results = new ArrayList<>();
        String[] keywords = query.toLowerCase()
            .replaceAll("[^a-zA-Z0-9àâäéèêëïîôùûüÿçœæ ]", " ")
            .split("\\s+");
        
        log.info("🔍 Recherche par mots-clés : {}", String.join(", ", keywords));
        
        for (Document doc : ALL_DOCUMENTS) {
            String content = doc.getContent().toLowerCase();
            int matchCount = 0;
            
            for (String keyword : keywords) {
                if (keyword.length() > 2 && content.contains(keyword)) {
                    matchCount++;
                }
            }
            
            if (matchCount > 0) {
                // Ajouter un score de pertinence
                Map<String, Object> metadata = new HashMap<>(doc.getMetadata());
                metadata.put("keyword_match_score", matchCount);
                Document scoredDoc = new Document(doc.getId(), doc.getContent(), metadata);
                results.add(scoredDoc);
            }
        }
        
        // Trier par score de pertinence
        results.sort((d1, d2) -> {
            int score1 = (int) d1.getMetadata().getOrDefault("keyword_match_score", 0);
            int score2 = (int) d2.getMetadata().getOrDefault("keyword_match_score", 0);
            return Integer.compare(score2, score1);
        });
        
        log.info("📊 Recherche par mots-clés : {} résultats trouvés", results.size());
        return results.subList(0, Math.min(maxResults, results.size()));
    }
}
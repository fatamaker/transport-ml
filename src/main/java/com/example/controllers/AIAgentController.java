package com.example.controllers;


import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.agents.AIAgent;
import com.example.services.CsvService;

import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.RequiredArgsConstructor;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
public class AIAgentController {

    private final AIAgent agent;
    
    private final CsvService csvService; 
    
    
    
 
    /**
     * Endpoint pour interroger l'agent IA via HTTP
     * Exemple :
     * http://localhost:8081/chat?query=Quel est le statut du train TGV88 ?
     */
    @GetMapping("/chat")
    public String chat(@RequestParam("query") String query) {
        return agent.chat(query);
    }
    
    
    
    
    @PostMapping("/debug/upload")
    public String debugUpload(@RequestParam("file") MultipartFile file) {
        try {
            String csvContent = new String(file.getBytes());
            
            StringBuilder debugInfo = new StringBuilder();
            debugInfo.append("📊 DEBUG DU FICHIER CSV\n\n");
            debugInfo.append("📏 Taille : ").append(csvContent.length()).append(" caractères\n");
            debugInfo.append("📋 Nombre de lignes : ").append(csvContent.lines().count()).append("\n\n");
            
            // Afficher les premières lignes
            debugInfo.append("📄 PREMIÈRES LIGNES :\n");
            csvContent.lines().limit(10).forEach(line -> debugInfo.append(line).append("\n"));
            
            debugInfo.append("\n🔍 RECHERCHE DE TERMES MÉTÉO :\n");
            String[] weatherTerms = {"Weather", "Météo", "météo", "weather", "Conditions", "conditions"};
            boolean found = false;
            
            for (String term : weatherTerms) {
                if (csvContent.contains(term)) {
                    debugInfo.append("✅ Trouvé : '").append(term).append("'\n");
                    found = true;
                    
                    // Afficher les lignes contenant ce terme
                    csvContent.lines()
                        .filter(line -> line.toLowerCase().contains(term.toLowerCase()))
                        .forEach(line -> debugInfo.append("   → ").append(line).append("\n"));
                }
            }
            
            if (!found) {
                debugInfo.append("❌ Aucun terme météo trouvé dans le CSV\n");
            }
            
            debugInfo.append("\n📋 TOUTES LES RAISONS D'INCIDENT UNIQUES :\n");
            csvContent.lines()
                .skip(1) // sauter l'en-tête
                .map(line -> {
                    String[] parts = line.split(",");
                    return parts.length > 7 ? parts[7] : "N/A"; // colonne IncidentReason
                })
                .distinct()
                .forEach(reason -> debugInfo.append("   - ").append(reason).append("\n"));
            
            return debugInfo.toString();
            
        } catch (Exception e) {
            return "❌ Erreur : " + e.getMessage();
        }
    }
    // Endpoint avec upload de fichier
    @PostMapping(value = "/chat/csv", consumes = "multipart/form-data")
    public String chatWithCsv(@RequestParam("file") MultipartFile file, 
                             @RequestParam("query") String query) {
        try {
            if (file.isEmpty()) {
                return "❌ Le fichier est vide";
            }
            
            if (query == null || query.trim().isEmpty()) {
                return "❌ Veuillez fournir une question";
            }
            
            String fileName = file.getOriginalFilename();
            if (fileName == null || !fileName.toLowerCase().endsWith(".csv")) {
                return "❌ Veuillez uploader un fichier CSV";
            }
            
            String csvContent = new String(file.getBytes());
            log.info("📁 Fichier CSV chargé : {} lignes", csvContent.lines().count());
            
            return agent.analyzeCsvData(csvContent, query);
            
        } catch (Exception e) {
            log.error("❌ Erreur CSV", e);
            return "❌ Erreur lors du traitement du CSV: " + e.getMessage();
        }
    }
    @GetMapping("/test-rag")
    public String testRag(@RequestParam String query) {
        return agent.testRag(query);
    }
}
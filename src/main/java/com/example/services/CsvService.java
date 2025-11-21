package com.example.services;



import com.opencsv.CSVReader;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;

@Service
public class CsvService {

	public String analyzeCsvContent(MultipartFile file) {
        try (Reader reader = new InputStreamReader(file.getInputStream());
             CSVReader csvReader = new CSVReader(reader)) {

            List<String[]> rows = csvReader.readAll();
            StringBuilder content = new StringBuilder();
            
            // On limite à 50 lignes pour ne pas saturer la mémoire du petit modèle 3B
            int limit = Math.min(rows.size(), 50); 
            
            content.append("Voici les données du fichier CSV (50 premières lignes) :\n");
            for (int i = 0; i < limit; i++) {
                content.append(String.join(" | ", rows.get(i))).append("\n");
            }
            
            return content.toString();

        } catch (Exception e) {
            return "Erreur lors de la lecture du fichier CSV : " + e.getMessage();
        }
    }
}
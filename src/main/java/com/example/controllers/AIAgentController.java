package com.example.controllers;


import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.agents.AIAgent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
public class AIAgentController {

    private final AIAgent agent;

    public AIAgentController(AIAgent agent) {
        this.agent = agent;
    }

 
    /**
     * Endpoint pour interroger l'agent IA via HTTP
     * Exemple :
     * http://localhost:8081/chat?query=Quel est le statut du train TGV88 ?
     */
    @GetMapping("/chat")
    public String chat(@RequestParam("query") String query) {
        return agent.chat(query);
    }
    

    
    
    @GetMapping("/test-rag")
    public String testRag(@RequestParam String query) {
        return agent.testRag(query);
    }
}
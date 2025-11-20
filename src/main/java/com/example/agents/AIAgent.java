package com.example.agents;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import com.example.tools.TransportTools;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AIAgent {

    private final ChatClient.Builder builder;
    private final TransportTools transportTools;
    private ChatClient chatClient;

    @PostConstruct
    public void init() {
        this.chatClient = builder
                .defaultSystem("""
                    Tu es un agent IA expert en gestion de transports (vols et trains).
                    1. Utilise tes outils pour vérifier les horaires et retards.
                    2. Fournis les détails précis du transport demandé.
                    3. Sois concis et clair.
                    """)
                .defaultFunctions("getDelayedTransports", "getTransportDetailsByNumber")
                .build();
    }

    public String chat(String userQuery) {
        return chatClient.prompt()
                .user(userQuery)
                .call()
                .content();
    }
}

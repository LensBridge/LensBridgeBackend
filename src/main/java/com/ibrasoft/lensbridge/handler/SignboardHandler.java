package com.ibrasoft.lensbridge.handler;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SignboardHandler extends TextWebSocketHandler {
    
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        System.out.println("Signboard connected: " + session.getId());
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        System.out.println("Signboard disconnected: " + session.getId());
    }
    
    public void sendRefreshCommand() {
        sessions.removeIf(session -> !session.isOpen());
        sessions.forEach(session -> {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage("REFRESH"));
                    System.out.println("Sent REFRESH to session: " + session.getId());
                } catch (IOException e) {
                    System.err.println("Failed to send to session " + session.getId() + ": " + e.getMessage());
                }
            }
        });
    }
}
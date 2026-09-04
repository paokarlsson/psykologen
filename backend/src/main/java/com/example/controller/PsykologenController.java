package com.example.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.example.service.PsykologenService;
import com.example.service.UserSessionRegistry;
import com.example.session.ChatMessage;
import com.example.storage.HistoryEntry;

/**
 * Samtliga endpoints kräver inloggning (se {@link com.example.auth.SecurityConfig})
 * och arbetar mot den inloggade användarens egen {@link PsykologenService},
 * utdelad av {@link UserSessionRegistry}. Ingen endpoint tar emot ett
 * användarnamn från klienten - identiteten kommer alltid från sessionen, så
 * en användare kan inte be om någon annans samtal.
 *
 * Ingen {@code @CrossOrigin} här: frontend nås via Angulars dev-proxy och
 * ligger därmed på samma origin. Ett CORS-jokertecken hade dessutom varit
 * oförenligt med den cookie inloggningen bygger på.
 */
@RestController
@RequestMapping("/api/psykologen")
public class PsykologenController {

    private final UserSessionRegistry registry;

    public PsykologenController(UserSessionRegistry registry) {
        this.registry = registry;
    }

    private PsykologenService serviceFor(Authentication auth) {
        return registry.forUser(auth.getName());
    }

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startConversation(Authentication auth) {
        Map<String, Object> response = new HashMap<>();
        try {
            String opening = serviceFor(auth).startConversation();
            response.put("success", true);
            response.put("message", opening);
            response.put("role", "erik");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/message")
    public ResponseEntity<Map<String, Object>> sendMessage(Authentication auth,
            @RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            String userInput = request.get("message");
            if (userInput == null || userInput.trim().isEmpty()) {
                response.put("success", false);
                response.put("error", "Message cannot be empty");
                return ResponseEntity.badRequest().body(response);
            }

            String erikResponse = serviceFor(auth).processMessage(userInput);
            response.put("success", true);
            response.put("message", erikResponse);
            response.put("role", "erik");

            if (erikResponse.contains("KLAR FÖR SKRIVNING")) {
                response.put("sessionComplete", true);
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/conversation")
    public ResponseEntity<Map<String, Object>> getConversation(Authentication auth) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<ChatMessage> conversation = serviceFor(auth).getConversation();
            response.put("success", true);
            response.put("conversation", conversation);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/profile")
    public ResponseEntity<Map<String, Object>> getProfile(Authentication auth) {
        Map<String, Object> response = new HashMap<>();
        try {
            String profile = serviceFor(auth).getProfile();
            response.put("success", true);
            response.put("profile", profile);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/plan")
    public ResponseEntity<Map<String, Object>> getPlan(Authentication auth) {
        Map<String, Object> response = new HashMap<>();
        try {
            String plan = serviceFor(auth).getPlan();
            response.put("success", true);
            response.put("plan", plan);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /** Ändringshistoriken för profil och plan (vad som reviderades och när), nyast först. */
    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getHistory(Authentication auth) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<HistoryEntry> history = serviceFor(auth).getHistory();
            response.put("success", true);
            response.put("history", history);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /** Startar om samtalet helt blankt (ny samtalshistorik, tom profil/plan/ändringslogg). Rör inte promptinställningar. */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> resetSession(Authentication auth) {
        Map<String, Object> response = new HashMap<>();
        try {
            serviceFor(auth).resetSession();
            response.put("success", true);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /** Nuvarande promptvärden + standardvärden + på/av-läge, för redigering i GUI:t. */
    @GetMapping("/settings/prompts")
    public ResponseEntity<Map<String, Object>> getPromptSettings(Authentication auth) {
        Map<String, Object> response = new HashMap<>();
        try {
            response.putAll(serviceFor(auth).getPromptSettings());
            response.put("success", true);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /** Sparar egna texter för en eller flera promptnycklar (body: { "systemPrompt": "...", ... }). */
    @PutMapping("/settings/prompts")
    public ResponseEntity<Map<String, Object>> updatePrompts(Authentication auth,
            @RequestBody Map<String, String> updates) {
        Map<String, Object> response = new HashMap<>();
        try {
            serviceFor(auth).updatePrompts(updates);
            response.put("success", true);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /** Återställer en nyckel (body: {"key": "systemPrompt"}) eller samtliga (tomt/utelämnat body) till standard. */
    @PostMapping("/settings/prompts/reset")
    public ResponseEntity<Map<String, Object>> resetPrompts(Authentication auth,
            @RequestBody(required = false) Map<String, String> body) {
        Map<String, Object> response = new HashMap<>();
        String key = body == null ? null : body.get("key");
        if (key == null || key.isBlank()) {
            serviceFor(auth).resetAllPrompts();
        } else {
            serviceFor(auth).resetPrompt(key);
        }
        response.put("success", true);
        return ResponseEntity.ok(response);
    }

    /** Slår av/på om de sparade egna promptarna faktiskt används (body: {"enabled": true|false}). */
    @PutMapping("/settings/custom-prompts-enabled")
    public ResponseEntity<Map<String, Object>> setCustomPromptsEnabled(Authentication auth,
            @RequestBody Map<String, Boolean> body) {
        Map<String, Object> response = new HashMap<>();
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        serviceFor(auth).setCustomPromptsEnabled(enabled);
        response.put("success", true);
        response.put("enabled", enabled);
        return ResponseEntity.ok(response);
    }

    /** Sätter hur många minuter Erik ska planera samtalet mot (body: {"minutes": 45}). */
    @PutMapping("/settings/session-duration")
    public ResponseEntity<Map<String, Object>> setSessionDuration(Authentication auth,
            @RequestBody Map<String, Double> body) {
        Map<String, Object> response = new HashMap<>();
        try {
            Double minutes = body.get("minutes");
            if (minutes == null) {
                response.put("success", false);
                response.put("error", "Fältet 'minutes' saknas.");
                return ResponseEntity.badRequest().body(response);
            }
            serviceFor(auth).setSessionDurationMinutes(minutes);
            response.put("success", true);
            response.put("sessionDurationMinutes", minutes);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

}

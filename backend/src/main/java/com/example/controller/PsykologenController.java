package com.example.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.service.PsykologenService;
import com.example.session.ChatMessage;
import com.example.storage.HistoryEntry;

@RestController
@RequestMapping("/api/psykologen")
@CrossOrigin(origins = "*")
public class PsykologenController {

    private final PsykologenService psykologenService;

    public PsykologenController(PsykologenService psykologenService) {
        this.psykologenService = psykologenService;
    }

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startConversation() {
        Map<String, Object> response = new HashMap<>();
        try {
            String opening = psykologenService.startConversation();
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
    public ResponseEntity<Map<String, Object>> sendMessage(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            String userInput = request.get("message");
            if (userInput == null || userInput.trim().isEmpty()) {
                response.put("success", false);
                response.put("error", "Message cannot be empty");
                return ResponseEntity.badRequest().body(response);
            }

            String erikResponse = psykologenService.processMessage(userInput);
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
    public ResponseEntity<Map<String, Object>> getConversation() {
        Map<String, Object> response = new HashMap<>();
        try {
            List<ChatMessage> conversation = psykologenService.getConversation();
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
    public ResponseEntity<Map<String, Object>> getProfile() {
        Map<String, Object> response = new HashMap<>();
        try {
            String profile = psykologenService.getProfile();
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
    public ResponseEntity<Map<String, Object>> getPlan() {
        Map<String, Object> response = new HashMap<>();
        try {
            String plan = psykologenService.getPlan();
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
    public ResponseEntity<Map<String, Object>> getHistory() {
        Map<String, Object> response = new HashMap<>();
        try {
            List<HistoryEntry> history = psykologenService.getHistory();
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
    public ResponseEntity<Map<String, Object>> resetSession() {
        Map<String, Object> response = new HashMap<>();
        try {
            psykologenService.resetSession();
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
    public ResponseEntity<Map<String, Object>> getPromptSettings() {
        Map<String, Object> response = new HashMap<>();
        try {
            response.putAll(psykologenService.getPromptSettings());
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
    public ResponseEntity<Map<String, Object>> updatePrompts(@RequestBody Map<String, String> updates) {
        Map<String, Object> response = new HashMap<>();
        try {
            psykologenService.updatePrompts(updates);
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
    public ResponseEntity<Map<String, Object>> resetPrompts(@RequestBody(required = false) Map<String, String> body) {
        Map<String, Object> response = new HashMap<>();
        String key = body == null ? null : body.get("key");
        if (key == null || key.isBlank()) {
            psykologenService.resetAllPrompts();
        } else {
            psykologenService.resetPrompt(key);
        }
        response.put("success", true);
        return ResponseEntity.ok(response);
    }

    /** Slår av/på om de sparade egna promptarna faktiskt används (body: {"enabled": true|false}). */
    @PutMapping("/settings/custom-prompts-enabled")
    public ResponseEntity<Map<String, Object>> setCustomPromptsEnabled(@RequestBody Map<String, Boolean> body) {
        Map<String, Object> response = new HashMap<>();
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        psykologenService.setCustomPromptsEnabled(enabled);
        response.put("success", true);
        response.put("enabled", enabled);
        return ResponseEntity.ok(response);
    }

    /** Sätter hur många minuter Erik ska planera samtalet mot (body: {"minutes": 45}). */
    @PutMapping("/settings/session-duration")
    public ResponseEntity<Map<String, Object>> setSessionDuration(@RequestBody Map<String, Double> body) {
        Map<String, Object> response = new HashMap<>();
        try {
            Double minutes = body.get("minutes");
            if (minutes == null) {
                response.put("success", false);
                response.put("error", "Fältet 'minutes' saknas.");
                return ResponseEntity.badRequest().body(response);
            }
            psykologenService.setSessionDurationMinutes(minutes);
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

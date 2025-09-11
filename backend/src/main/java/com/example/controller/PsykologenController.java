package com.example.controller;

import com.example.service.PsykologenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/psykologen")
@CrossOrigin(origins = "*")
public class PsykologenController {
    
    @Autowired
    private PsykologenService psykologenService;
    
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
            
            // Check if session should end
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
            List<Map<String, Object>> conversation = psykologenService.getConversation();
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
    
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        Map<String, Object> response = new HashMap<>();
        try {
            Map<String, Object> stats = psykologenService.getStatistics();
            response.put("success", true);
            response.put("statistics", stats);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "OK");
        response.put("service", "Psykologen API");
        return ResponseEntity.ok(response);
    }
}
package com.example.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class UserSessionRegistry {

    private final Map<String, PsykologenService> services = new ConcurrentHashMap<>();
    private final Function<String, PsykologenService> factory;

    public UserSessionRegistry(Function<String, PsykologenService> factory) {
        this.factory = factory;
    }

    public PsykologenService forUser(String username) {
        return services.computeIfAbsent(username, factory);
    }
}

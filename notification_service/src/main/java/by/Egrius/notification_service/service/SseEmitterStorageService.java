package by.Egrius.notification_service.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SseEmitterStorageService {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public void put(String userId, SseEmitter emitter) {
        emitters.put(userId, emitter);
        log.debug("Emitter stored for user: {}", userId);
    }

    public SseEmitter get(String userId) {
        return emitters.get(userId);
    }

    public void remove(String userId) {
        SseEmitter removed = emitters.remove(userId);
        if (removed != null) {
            removed.complete();
            log.debug("Emitter removed for user: {}", userId);
        }
    }

    public boolean exists(String userId) {
        return emitters.containsKey(userId);
    }

    public boolean send(String userId, Object event) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) {
            log.debug("No active emitter for user: {}", userId);
            return false;
        }

        try {
            emitter.send(event);
            log.debug("Event sent to user: {}", userId);
            return true;
        } catch (IOException e) {
            emitters.remove(userId);
            log.warn("Failed to send event to user {}, removed emitter", userId);
            return false;
        }
    }

    public int size() {
        return emitters.size();
    }
}
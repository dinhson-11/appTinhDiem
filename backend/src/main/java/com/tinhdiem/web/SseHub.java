package com.tinhdiem.web;

import com.tinhdiem.model.Game;
import com.tinhdiem.store.GameStore;
import com.tinhdiem.store.NotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

/** Giữ các kết nối SSE theo từng bàn và phát trạng thái đầy đủ của bàn mỗi khi nó thay đổi. */
@Component
public class SseHub {

    private static final long RECONNECT_MILLIS = 2000;

    private final GameStore store;
    private final ObjectMapper mapper;
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseHub(GameStore store, ObjectMapper mapper) {
        this.store = store;
        this.mapper = mapper;
        store.addListener(this::broadcast);
        store.addDeleteListener(this::closeDeleted);
    }

    /** Đăng ký người nghe và gửi ngay trạng thái hiện tại; ném {@link NotFoundException} nếu không có bàn. */
    public SseEmitter subscribe(String gameId) {
        return store.locked(() -> {
            Game game = store.getGame(gameId).orElseThrow(() -> new NotFoundException("Không tìm thấy bàn."));
            SseEmitter emitter = new SseEmitter(0L);
            emitters.computeIfAbsent(gameId, id -> new CopyOnWriteArrayList<>()).add(emitter);
            emitter.onCompletion(() -> drop(gameId, emitter));
            emitter.onTimeout(() -> drop(gameId, emitter));
            emitter.onError(error -> drop(gameId, emitter));
            send(gameId, emitter, gameEvent(game).reconnectTime(RECONNECT_MILLIS));
            return emitter;
        });
    }

    public int sseClientCount() {
        return emitters.values().stream().mapToInt(List::size).sum();
    }

    /** Giữ kết nối khỏi bị proxy/trình duyệt cắt, đồng thời phát hiện máy khách đã biến mất. */
    @Scheduled(fixedRate = 25_000)
    void ping() {
        emitters.forEach((gameId, list) -> {
            for (SseEmitter emitter : list) {
                send(gameId, emitter, SseEmitter.event().comment("ping"));
            }
        });
    }

    private void broadcast(Game game) {
        List<SseEmitter> list = emitters.get(game.id());
        if (list == null) {
            return;
        }
        for (SseEmitter emitter : list) {
            send(game.id(), emitter, gameEvent(game));
        }
    }

    /** Báo cho mọi máy đang xem bàn đã bị xóa rồi đóng luồng của chúng, để trình duyệt không nối lại mãi. */
    private void closeDeleted(String gameId) {
        List<SseEmitter> list = emitters.remove(gameId);
        if (list == null) {
            return;
        }
        for (SseEmitter emitter : list) {
            send(gameId, emitter, SseEmitter.event().name("deleted").data(gameId));
            emitter.complete();
        }
    }

    private SseEmitter.SseEventBuilder gameEvent(Game game) {
        return SseEmitter.event().name("game").data(mapper.writeValueAsString(game));
    }

    private void send(String gameId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | RuntimeException e) {
            drop(gameId, emitter);
        }
    }

    private void drop(String gameId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(gameId);
        if (list != null) {
            list.remove(emitter);
        }
    }
}

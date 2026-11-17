package com.tinhdiem.web;

import com.tinhdiem.model.Game;
import com.tinhdiem.model.GameSummary;
import com.tinhdiem.store.GameStore;
import com.tinhdiem.store.NotFoundException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * API REST của bàn chơi. Các trường điểm/tên trong request có kiểu {@code Object} để {@code Rules} tự kiểm kiểu,
 * tránh việc Jackson ép {@code 1.5} thành {@code 1}.
 */
@RestController
@RequestMapping("/api/games")
public class GameController {

    public record CreateGameRequest(String name, Object players) {
    }

    public record ScoresRequest(Object scores) {
    }

    public record PlayersRequest(Object players) {
    }

    private final GameStore store;
    private final SseHub hub;

    public GameController(GameStore store, SseHub hub) {
        this.store = store;
        this.hub = hub;
    }

    @GetMapping
    public List<GameSummary> list() {
        return store.listGames();
    }

    @PostMapping
    public ResponseEntity<Game> create(@RequestBody CreateGameRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(store.createGame(body.name(), body.players()));
    }

    @GetMapping("/{id}")
    public Game get(@PathVariable String id) {
        return store.getGame(id).orElseThrow(() -> new NotFoundException("Không tìm thấy bàn."));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        store.deleteGame(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/rounds")
    public ResponseEntity<Game> addRound(@PathVariable String id, @RequestBody ScoresRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(store.addRound(id, body.scores()));
    }

    @PutMapping("/{id}/rounds/{roundId}")
    public Game updateRound(@PathVariable String id, @PathVariable String roundId,
            @RequestBody ScoresRequest body) {
        return store.updateRound(id, roundId, body.scores());
    }

    @DeleteMapping("/{id}/rounds/{roundId}")
    public Game deleteRound(@PathVariable String id, @PathVariable String roundId) {
        return store.deleteRound(id, roundId);
    }

    @PutMapping("/{id}/players")
    public Game renamePlayers(@PathVariable String id, @RequestBody PlayersRequest body) {
        return store.renamePlayers(id, body.players());
    }

    @GetMapping(value = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable String id) {
        return hub.subscribe(id);
    }
}

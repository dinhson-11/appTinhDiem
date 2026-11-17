package com.tinhdiem.store;

import com.tinhdiem.model.Game;
import com.tinhdiem.model.GameSummary;
import com.tinhdiem.model.Round;
import com.tinhdiem.rules.Rules;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Kho dữ liệu: giữ toàn bộ bàn trong bộ nhớ và ghi nguyên tử ra một file JSON sau mỗi thay đổi.
 * Mọi phương thức khóa tuần tự nên hai thiết bị gửi cùng lúc không làm hỏng dữ liệu. Tham số điểm/tên là
 * giá trị JSON thô và được kiểm bằng {@link Rules}.
 */
@Component
public class GameStore {

    static final int MAX_GAME_NAME_LENGTH = 40;

    /** Hình dạng của file dữ liệu: {"games":[...]}. */
    record Data(List<Game> games) {
    }

    private final Path file;
    private final ObjectMapper mapper;
    private final List<Consumer<Game>> listeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<String>> deleteListeners = new CopyOnWriteArrayList<>();
    private final SecureRandom random = new SecureRandom();
    private Map<String, Game> games = new LinkedHashMap<>();

    public GameStore(@Value("${tinhdiem.data-file}") Path file, ObjectMapper mapper) {
        this.file = file;
        this.mapper = mapper;
        load();
    }

    public synchronized List<GameSummary> listGames() {
        return games.values().stream()
                .sorted(Comparator.comparingLong(Game::updatedAt).reversed())
                .map(g -> new GameSummary(g.id(), g.name(), g.players().size(), g.rounds().size(), g.updatedAt()))
                .toList();
    }

    public synchronized Optional<Game> getGame(String id) {
        return Optional.ofNullable(games.get(id));
    }

    public synchronized Game createGame(String name, Object players) {
        Rules.validatePlayers(players).ifPresent(message -> {
            throw new ValidationException(message);
        });
        String gameName = name == null ? "" : name.strip();
        if (gameName.length() > MAX_GAME_NAME_LENGTH) {
            throw new ValidationException("Tên bàn tối đa " + MAX_GAME_NAME_LENGTH + " ký tự.");
        }
        if (gameName.isEmpty()) {
            gameName = "Bàn " + (games.size() + 1);
        }
        long now = System.currentTimeMillis();
        Game game = new Game(newGameId(), gameName, Rules.toNameList(players), List.of(), now, now);
        return replace(game);
    }

    /** Xóa cả bàn. Ghi file trước; chỉ khi ghi xong mới bỏ bàn khỏi bộ nhớ và báo delete listener. */
    public synchronized void deleteGame(String gameId) {
        require(gameId);
        Map<String, Game> next = new LinkedHashMap<>(games);
        next.remove(gameId);
        write(next);
        games = next;
        for (Consumer<String> listener : deleteListeners) {
            listener.accept(gameId);
        }
    }

    public synchronized Game addRound(String gameId, Object scores) {
        Game game = require(gameId);
        checkScores(scores, game);
        List<Round> rounds = new ArrayList<>(game.rounds());
        rounds.add(new Round(UUID.randomUUID().toString(), Rules.toScoreList(scores)));
        return replace(withRounds(game, rounds));
    }

    public synchronized Game updateRound(String gameId, String roundId, Object scores) {
        Game game = require(gameId);
        int index = indexOfRound(game, roundId);
        checkScores(scores, game);
        List<Round> rounds = new ArrayList<>(game.rounds());
        rounds.set(index, new Round(roundId, Rules.toScoreList(scores)));
        return replace(withRounds(game, rounds));
    }

    public synchronized Game deleteRound(String gameId, String roundId) {
        Game game = require(gameId);
        int index = indexOfRound(game, roundId);
        List<Round> rounds = new ArrayList<>(game.rounds());
        rounds.remove(index);
        return replace(withRounds(game, rounds));
    }

    public synchronized Game renamePlayers(String gameId, Object players) {
        Game game = require(gameId);
        Rules.validatePlayers(players).ifPresent(message -> {
            throw new ValidationException(message);
        });
        List<String> names = Rules.toNameList(players);
        if (names.size() != game.players().size()) {
            throw new ValidationException("Chỉ được đổi tên, không đổi số người chơi.");
        }
        return replace(new Game(game.id(), game.name(), names, game.rounds(), game.createdAt(),
                System.currentTimeMillis()));
    }

    /**
     * Chạy action khi đang giữ khóa của kho, nên không thay đổi nào (và không lần phát listener nào) chen vào
     * giữa. Dùng để đăng ký người nghe SSE cùng lúc với việc lấy trạng thái hiện tại mà không bỏ lỡ cập nhật.
     */
    public synchronized <T> T locked(Supplier<T> action) {
        return action.get();
    }

    /** Gọi sau mỗi thay đổi thành công. Trả hàm để gỡ listener. */
    public Runnable addListener(Consumer<Game> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    /** Gọi sau khi một bàn bị xóa thành công, với id của bàn. Trả hàm để gỡ listener. */
    public Runnable addDeleteListener(Consumer<String> listener) {
        deleteListeners.add(listener);
        return () -> deleteListeners.remove(listener);
    }

    // ---- nội bộ ----

    private Game require(String gameId) {
        Game game = games.get(gameId);
        if (game == null) {
            throw new NotFoundException("Không tìm thấy bàn.");
        }
        return game;
    }

    private static int indexOfRound(Game game, String roundId) {
        for (int i = 0; i < game.rounds().size(); i++) {
            if (game.rounds().get(i).id().equals(roundId)) {
                return i;
            }
        }
        throw new NotFoundException("Không tìm thấy ván.");
    }

    private static void checkScores(Object scores, Game game) {
        Rules.validateScores(scores, game.players().size()).ifPresent(message -> {
            throw new ValidationException(message);
        });
    }

    private static Game withRounds(Game game, List<Round> rounds) {
        return new Game(game.id(), game.name(), game.players(), List.copyOf(rounds), game.createdAt(),
                System.currentTimeMillis());
    }

    private String newGameId() {
        String id;
        do {
            byte[] bytes = new byte[8];
            random.nextBytes(bytes);
            id = HexFormat.of().formatHex(bytes);
        } while (games.containsKey(id));
        return id;
    }

    /** Ghi file trước; chỉ khi ghi xong mới đổi trạng thái trong bộ nhớ và báo listener. */
    private Game replace(Game updated) {
        Map<String, Game> next = new LinkedHashMap<>(games);
        next.put(updated.id(), updated);
        write(next);
        games = next;
        for (Consumer<Game> listener : listeners) {
            listener.accept(updated);
        }
        return updated;
    }

    private void write(Map<String, Game> data) {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.write(tmp, mapper.writeValueAsBytes(new Data(List.copyOf(data.values()))));
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // không làm lỗi gốc bị che
            }
            throw new UncheckedIOException(e);
        }
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        Data data;
        try {
            data = mapper.readValue(Files.readAllBytes(file), Data.class);
        } catch (IOException | JacksonException e) {
            throw refuse(e);
        }
        if (data == null || data.games() == null) {
            throw refuse(null);
        }
        Map<String, Game> loaded = new LinkedHashMap<>();
        for (Game game : data.games()) {
            if (!isStructurallyValid(game)) {
                throw refuse(null);
            }
            loaded.put(game.id(), game);
        }
        games = loaded;
    }

    private DataFileException refuse(Throwable cause) {
        return new DataFileException("Không đọc được file dữ liệu: " + file.toAbsolutePath()
                + ". File có thể bị hỏng; hãy sửa hoặc đổi tên file đó rồi chạy lại "
                + "(ứng dụng không tự ghi đè để tránh mất dữ liệu).", cause);
    }

    private static boolean isStructurallyValid(Game game) {
        if (game == null || game.id() == null || game.name() == null
                || game.players() == null || game.rounds() == null) {
            return false;
        }
        return game.rounds().stream().allMatch(r -> r != null && r.id() != null && r.scores() != null);
    }
}

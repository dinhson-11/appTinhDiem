package com.tinhdiem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tinhdiem.model.Game;
import com.tinhdiem.model.Round;
import com.tinhdiem.store.DataFileException;
import com.tinhdiem.store.GameStore;
import com.tinhdiem.store.NotFoundException;
import com.tinhdiem.store.ValidationException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class GameStoreTest {

    @TempDir
    Path dir;

    Path file;
    GameStore store;

    @BeforeEach
    void setUp() {
        file = dir.resolve("data").resolve("games.json");
        store = open();
    }

    private GameStore open() {
        return new GameStore(file, JsonMapper.builder().build());
    }

    private Game newGame() {
        return store.createGame("Bàn thử", List.of("An", "Bình", "Chi"));
    }

    // ---- createGame ----

    @Test
    void createGameTrimsNameAssignsHexIdAndStartsWithoutRounds() {
        Game game = store.createGame("  Bàn A  ", List.of("An", "Bình"));

        assertThat(game.name()).isEqualTo("Bàn A");
        assertThat(game.id()).matches("[0-9a-f]{16}");
        assertThat(game.rounds()).isEmpty();
        assertThat(game.players()).containsExactly("An", "Bình");
        assertThat(game.createdAt()).isPositive().isEqualTo(game.updatedAt());
    }

    @Test
    void createGameDefaultsBlankNameToSequentialNumber() {
        assertThat(store.createGame("   ", List.of("An", "Bình")).name()).isEqualTo("Bàn 1");
        assertThat(store.createGame(null, List.of("An", "Bình")).name()).isEqualTo("Bàn 2");
    }

    @Test
    void playerNamesAreStoredVerbatim() {
        Game game = store.createGame("x", List.of("<script>alert(1)</script>", "Bình"));

        assertThat(game.players().get(0)).isEqualTo("<script>alert(1)</script>");
    }

    @Test
    void createGameRejectsInvalidInput() {
        assertThatThrownBy(() -> store.createGame("x", List.of("An"))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> store.createGame("x", names(13))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> store.createGame("x", List.of("An", ""))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> store.createGame("x", List.of("An", "   "))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> store.createGame("a".repeat(41), List.of("An", "Bình")))
                .isInstanceOf(ValidationException.class);
        assertThat(store.listGames()).isEmpty();
    }

    @Test
    void listGamesReturnsMostRecentlyUpdatedFirst() throws InterruptedException {
        Game first = store.createGame("một", List.of("An", "Bình"));
        Thread.sleep(5);
        Game second = store.createGame("hai", List.of("An", "Bình"));
        assertThat(store.listGames()).extracting("id").containsExactly(second.id(), first.id());

        Thread.sleep(5);
        store.addRound(first.id(), List.of(1, -1));
        assertThat(store.listGames()).extracting("id").containsExactly(first.id(), second.id());
        assertThat(store.listGames().get(0).roundCount()).isEqualTo(1);
        assertThat(store.listGames().get(0).playerCount()).isEqualTo(2);
    }

    // ---- deleteGame ----

    @Test
    void deleteGameRemovesItFromTheListAndFromDisk() {
        Game keep = store.createGame("giữ", List.of("An", "Bình"));
        Game drop = newGame();

        store.deleteGame(drop.id());

        assertThat(store.getGame(drop.id())).isEmpty();
        assertThat(store.listGames()).extracting("id").containsExactly(keep.id());
        GameStore reopened = open();
        assertThat(reopened.getGame(drop.id())).isEmpty();
        assertThat(reopened.getGame(keep.id())).contains(keep);
    }

    @Test
    void deleteGameNotifiesDeleteListenersWithTheIdAndNotTheUpdateListeners() {
        List<String> deleted = new ArrayList<>();
        List<Game> updated = new ArrayList<>();
        Runnable remove = store.addDeleteListener(deleted::add);
        store.addListener(updated::add);
        Game first = newGame();
        Game second = newGame();
        updated.clear();

        store.deleteGame(first.id());
        assertThat(deleted).containsExactly(first.id());
        assertThat(updated).isEmpty();

        remove.run();
        store.deleteGame(second.id());
        assertThat(deleted).containsExactly(first.id());
    }

    @Test
    void deleteUnknownGameIsNotFoundAndKeepsTheOthers() {
        newGame();

        assertThatThrownBy(() -> store.deleteGame("nope")).isInstanceOf(NotFoundException.class);

        assertThat(store.listGames()).hasSize(1);
    }

    @Test
    void failedWriteOnDeleteKeepsTheGameAndDoesNotNotify() throws IOException {
        Game game = newGame();
        List<String> deleted = new ArrayList<>();
        store.addDeleteListener(deleted::add);
        // Một thư mục nằm đúng chỗ file tạm khiến mọi lần ghi sau đó đều lỗi.
        Files.createDirectory(file.resolveSibling("games.json.tmp"));

        assertThatThrownBy(() -> store.deleteGame(game.id())).isInstanceOf(UncheckedIOException.class);

        assertThat(store.getGame(game.id())).contains(game);
        assertThat(deleted).isEmpty();
    }

    // ---- rounds ----

    @Test
    void addRoundStoresTheRoundWithAGeneratedId() {
        Game game = newGame();

        Game updated = store.addRound(game.id(), List.of(10, -5, -5));

        assertThat(updated.rounds()).hasSize(1);
        assertThat(updated.rounds().get(0).scores()).containsExactly(10, -5, -5);
        assertThat(updated.rounds().get(0).id()).isNotBlank();
        assertThat(store.getGame(game.id())).contains(updated);
    }

    @Test
    void addRoundRejectsBadScoresAndLeavesEverythingUntouched() throws IOException {
        Game game = newGame();
        List<Game> notified = new ArrayList<>();
        store.addListener(notified::add);
        byte[] fileBefore = Files.readAllBytes(file);
        Game before = store.getGame(game.id()).orElseThrow();

        assertThatThrownBy(() -> store.addRound(game.id(), List.of(1, -1))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> store.addRound(game.id(), List.of(1, 2, 3))).isInstanceOf(ValidationException.class);

        assertThat(store.getGame(game.id())).contains(before);
        assertThat(notified).isEmpty();
        assertThat(Files.readAllBytes(file)).isEqualTo(fileBefore);
    }

    @Test
    void updateRoundReplacesTheScores() {
        Game game = newGame();
        Round round = store.addRound(game.id(), List.of(10, -5, -5)).rounds().get(0);

        Game updated = store.updateRound(game.id(), round.id(), List.of(4, -4, 0));

        assertThat(updated.rounds()).hasSize(1);
        assertThat(updated.rounds().get(0).id()).isEqualTo(round.id());
        assertThat(updated.rounds().get(0).scores()).containsExactly(4, -4, 0);
    }

    @Test
    void updateRoundRejectsBadScores() {
        Game game = newGame();
        Round round = store.addRound(game.id(), List.of(10, -5, -5)).rounds().get(0);

        assertThatThrownBy(() -> store.updateRound(game.id(), round.id(), List.of(1, 1, 1)))
                .isInstanceOf(ValidationException.class);
        assertThat(store.getGame(game.id()).orElseThrow().rounds().get(0).scores()).containsExactly(10, -5, -5);
    }

    @Test
    void deleteRoundRemovesIt() {
        Game game = newGame();
        Round r1 = store.addRound(game.id(), List.of(10, -5, -5)).rounds().get(0);
        store.addRound(game.id(), List.of(1, 1, -2));

        Game updated = store.deleteRound(game.id(), r1.id());

        assertThat(updated.rounds()).hasSize(1);
        assertThat(updated.rounds().get(0).scores()).containsExactly(1, 1, -2);
    }

    @Test
    void unknownGameOrRoundIsNotFound() {
        Game game = newGame();

        assertThatThrownBy(() -> store.addRound("nope", List.of(1, -1, 0))).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> store.updateRound("nope", "r", List.of(1, -1, 0)))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> store.updateRound(game.id(), "nope", List.of(1, -1, 0)))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> store.deleteRound("nope", "r")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> store.deleteRound(game.id(), "nope")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> store.renamePlayers("nope", List.of("A", "B", "C")))
                .isInstanceOf(NotFoundException.class);
        assertThat(store.getGame("nope")).isEmpty();
    }

    // ---- renamePlayers ----

    @Test
    void renamePlayersKeepsCountAndRejectsADifferentCount() {
        Game game = newGame();

        Game renamed = store.renamePlayers(game.id(), List.of("  Dũng ", "Em", "Giang"));
        assertThat(renamed.players()).containsExactly("Dũng", "Em", "Giang");

        assertThatThrownBy(() -> store.renamePlayers(game.id(), List.of("A", "B"))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> store.renamePlayers(game.id(), List.of("A", "B", ""))).isInstanceOf(ValidationException.class);
        assertThat(store.getGame(game.id()).orElseThrow().players()).containsExactly("Dũng", "Em", "Giang");
    }

    // ---- persistence ----

    @Test
    void dataSurvivesReopeningAndLeavesNoTempFile() throws IOException {
        Game game = newGame();
        store.addRound(game.id(), List.of(10, -5, -5));
        store.addRound(game.id(), List.of(-3, 1, 2));

        GameStore reopened = open();

        assertThat(reopened.getGame(game.id())).contains(store.getGame(game.id()).orElseThrow());
        assertThat(reopened.getGame(game.id()).orElseThrow().rounds()).hasSize(2);
        try (var files = Files.list(file.getParent())) {
            assertThat(files.map(p -> p.getFileName().toString())).containsExactly("games.json");
        }
    }

    @Test
    void concurrentAddRoundKeepsEveryRoundOnDisk() throws Exception {
        Game game = newGame();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> futures = new ArrayList<>();
            IntStream.range(0, 50).forEach(i ->
                    futures.add(pool.submit(() -> store.addRound(game.id(), List.of(i, -i, 0)))));
            for (Future<?> f : futures) {
                f.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        Game reopened = open().getGame(game.id()).orElseThrow();
        assertThat(reopened.rounds()).hasSize(50);
        assertThat(reopened.rounds()).allSatisfy(r -> assertThat(r.scores().stream().mapToInt(Integer::intValue).sum()).isZero());
    }

    @Test
    void missingFileMeansEmptyStore() {
        assertThat(Files.exists(file)).isFalse();
        assertThat(store.listGames()).isEmpty();
    }

    @Test
    void corruptFileIsRefusedAndNotOverwritten() throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{\"broken");

        assertThatThrownBy(this::open)
                .isInstanceOf(DataFileException.class)
                .hasMessageContaining("games.json");
        assertThat(Files.readString(file)).isEqualTo("{\"broken");
    }

    @Test
    void emptyFileAndWrongStructureAreRefused() throws IOException {
        Files.createDirectories(file.getParent());

        Files.writeString(file, "");
        assertThatThrownBy(this::open).isInstanceOf(DataFileException.class);

        Files.writeString(file, "{}");
        assertThatThrownBy(this::open).isInstanceOf(DataFileException.class);

        Files.writeString(file, "{\"games\":[{\"id\":null}]}");
        assertThatThrownBy(this::open).isInstanceOf(DataFileException.class);
        assertThat(Files.readString(file)).isEqualTo("{\"games\":[{\"id\":null}]}");
    }

    @Test
    void failedWriteLeavesMemoryUnchangedAndDoesNotNotify() throws IOException {
        Path blocker = Files.writeString(dir.resolve("blocker"), "i am a file, not a directory");
        file = blocker.resolve("games.json");
        store = open();
        List<Game> notified = new ArrayList<>();
        store.addListener(notified::add);

        assertThatThrownBy(() -> store.createGame("x", List.of("An", "Bình"))).isInstanceOf(UncheckedIOException.class);

        assertThat(store.listGames()).isEmpty();
        assertThat(notified).isEmpty();
    }

    // ---- listeners ----

    @Test
    void listenersReceiveTheUpdatedGameAndCanBeRemoved() {
        List<Game> notified = new ArrayList<>();
        Runnable remove = store.addListener(notified::add);

        Game game = newGame();
        Game afterRound = store.addRound(game.id(), List.of(1, -1, 0));
        assertThat(notified).containsExactly(game, afterRound);

        remove.run();
        store.addRound(game.id(), List.of(1, -1, 0));
        assertThat(notified).hasSize(2);
    }

    private static List<Object> names(int count) {
        return IntStream.rangeClosed(1, count).<Object>mapToObj(i -> "Người " + i).toList();
    }
}

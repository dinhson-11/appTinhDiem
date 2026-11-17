package com.tinhdiem;

import static org.assertj.core.api.Assertions.assertThat;

import com.tinhdiem.model.Game;
import com.tinhdiem.model.GameSummary;
import com.tinhdiem.web.SseHub;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.FileSystemUtils;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GameApiTest {

    private static final Path DATA_DIR = createTempDir();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("tinhdiem.data-file", () -> DATA_DIR.resolve("games.json").toString());
    }

    @AfterAll
    static void cleanUp() {
        FileSystemUtils.deleteRecursively(DATA_DIR.toFile());
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    SseHub hub;

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();

    // ---- games ----

    @Test
    void createGameReturns201AndAppearsInTheList() throws Exception {
        HttpResponse<String> created = post("/api/games", "{\"name\":\"Tối nay\",\"players\":[\"An\",\"Bình\",\"Chi\"]}");

        assertThat(created.statusCode()).isEqualTo(201);
        Game game = json.readValue(created.body(), Game.class);
        assertThat(game.name()).isEqualTo("Tối nay");

        GameSummary[] list = json.readValue(get("/api/games").body(), GameSummary[].class);
        assertThat(list).anySatisfy(s -> {
            assertThat(s.id()).isEqualTo(game.id());
            assertThat(s.playerCount()).isEqualTo(3);
            assertThat(s.roundCount()).isZero();
        });
    }

    @Test
    void getUnknownGameIs404WithAnError() throws Exception {
        HttpResponse<String> response = get("/api/games/doesnotexist");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(errorOf(response)).isNotBlank();
    }

    @Test
    void createGameRejectsMissingOrTooFewPlayers() throws Exception {
        assertThat(post("/api/games", "{\"name\":\"x\"}").statusCode()).isEqualTo(400);
        assertThat(post("/api/games", "{\"players\":[\"An\"]}").statusCode()).isEqualTo(400);
        assertThat(post("/api/games", "{\"players\":[\"An\",\"  \"]}").statusCode()).isEqualTo(400);
    }

    @Test
    void deleteGameReturns204AndItIsGone() throws Exception {
        Game game = newGame();
        String path = "/api/games/" + game.id();

        assertThat(delete(path).statusCode()).isEqualTo(204);

        assertThat(get(path).statusCode()).isEqualTo(404);
        GameSummary[] list = json.readValue(get("/api/games").body(), GameSummary[].class);
        assertThat(list).noneSatisfy(s -> assertThat(s.id()).isEqualTo(game.id()));
        assertThat(delete(path).statusCode()).isEqualTo(404);
    }

    // ---- rounds ----

    @Test
    void addRoundReturns201AndStoresIt() throws Exception {
        Game game = newGame();

        HttpResponse<String> response = post("/api/games/" + game.id() + "/rounds", "{\"scores\":[10,-5,-5]}");

        assertThat(response.statusCode()).isEqualTo(201);
        Game updated = json.readValue(response.body(), Game.class);
        assertThat(updated.rounds()).hasSize(1);
        assertThat(updated.rounds().get(0).scores()).containsExactly(10, -5, -5);
    }

    @Test
    void invalidRoundBodiesAreRejectedWith400AndNothingIsSaved() throws Exception {
        Game game = newGame();
        String path = "/api/games/" + game.id() + "/rounds";
        List<String> badBodies = List.of(
                "{\"scores\":[1,2,3]}",
                "{\"scores\":[1.5,-1.5,0]}",
                "{\"scores\":[\"5\",-5,0]}",
                "{\"scores\":[5,null,-5]}",
                "{\"scores\":[1,-1]}",
                "{}",
                "{\"scores\":",
                "not json at all");

        for (String body : badBodies) {
            HttpResponse<String> response = post(path, body);
            assertThat(response.statusCode()).as(body).isEqualTo(400);
            assertThat(errorOf(response)).as(body).isNotBlank();
        }

        assertThat(json.readValue(get("/api/games/" + game.id()).body(), Game.class).rounds()).isEmpty();
    }

    @Test
    void updateAndDeleteRound() throws Exception {
        Game game = newGame();
        Game withRound = json.readValue(
                post("/api/games/" + game.id() + "/rounds", "{\"scores\":[10,-5,-5]}").body(), Game.class);
        String roundPath = "/api/games/" + game.id() + "/rounds/" + withRound.rounds().get(0).id();

        HttpResponse<String> updated = put(roundPath, "{\"scores\":[4,-4,0]}");
        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(json.readValue(updated.body(), Game.class).rounds().get(0).scores()).containsExactly(4, -4, 0);

        HttpResponse<String> deleted = delete(roundPath);
        assertThat(deleted.statusCode()).isEqualTo(200);
        assertThat(json.readValue(deleted.body(), Game.class).rounds()).isEmpty();

        assertThat(put(roundPath, "{\"scores\":[1,-1,0]}").statusCode()).isEqualTo(404);
        assertThat(delete(roundPath).statusCode()).isEqualTo(404);
    }

    @Test
    void roundOfUnknownGameIs404() throws Exception {
        assertThat(post("/api/games/nope/rounds", "{\"scores\":[1,-1]}").statusCode()).isEqualTo(404);
    }

    // ---- players ----

    @Test
    void renamePlayersKeepsTheCount() throws Exception {
        Game game = newGame();
        String path = "/api/games/" + game.id() + "/players";

        HttpResponse<String> ok = put(path, "{\"players\":[\"X\",\"Y\",\"Z\"]}");
        assertThat(ok.statusCode()).isEqualTo(200);
        assertThat(json.readValue(ok.body(), Game.class).players()).containsExactly("X", "Y", "Z");

        assertThat(put(path, "{\"players\":[\"X\",\"Y\"]}").statusCode()).isEqualTo(400);
    }

    // ---- SPA ----

    @Test
    void gamePageForwardsToIndexHtml() throws Exception {
        HttpResponse<String> response = get("/g/abc123");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("text/html");
        assertThat(response.body()).contains("<title>test</title>");
    }

    // ---- SSE ----

    @Test
    void sseSendsCurrentStateThenEveryChange() throws Exception {
        Game game = newGame();

        try (SseClient sse = new SseClient(port, "/api/games/" + game.id() + "/events")) {
            assertThat(sse.statusLine).contains("200");
            Game first = json.readValue(sse.nextData(), Game.class);
            assertThat(first.id()).isEqualTo(game.id());
            assertThat(first.rounds()).isEmpty();

            post("/api/games/" + game.id() + "/rounds", "{\"scores\":[10,-5,-5]}");

            Game second = json.readValue(sse.nextData(), Game.class);
            assertThat(second.rounds()).hasSize(1);
        }
    }

    @Test
    void sseClientIsForgottenAfterItDisconnects() throws Exception {
        Game game = newGame();
        int before = hub.sseClientCount();

        SseClient sse = new SseClient(port, "/api/games/" + game.id() + "/events");
        sse.nextData();
        assertThat(hub.sseClientCount()).isEqualTo(before + 1);

        sse.close();
        // TCP chấp nhận lần ghi đầu tiên sau khi bên kia đóng (rồi mới nhận RST); chỉ lần ghi thứ hai mới lỗi.
        // Server phát hiện máy khách biến mất khi ghi thất bại (phát cập nhật hoặc ping định kỳ).
        post("/api/games/" + game.id() + "/rounds", "{\"scores\":[1,-1,0]}");
        Thread.sleep(200);
        post("/api/games/" + game.id() + "/rounds", "{\"scores\":[1,-1,0]}");

        long deadline = System.currentTimeMillis() + 2000;
        while (hub.sseClientCount() != before && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertThat(hub.sseClientCount()).isEqualTo(before);
    }

    @Test
    void sseClientIsToldWhenItsGameIsDeletedAndThenForgotten() throws Exception {
        Game game = newGame();
        int before = hub.sseClientCount();

        try (SseClient sse = new SseClient(port, "/api/games/" + game.id() + "/events")) {
            sse.nextData();
            assertThat(hub.sseClientCount()).isEqualTo(before + 1);

            assertThat(delete("/api/games/" + game.id()).statusCode()).isEqualTo(204);

            assertThat(sse.nextEventName()).isEqualTo("deleted");
            long deadline = System.currentTimeMillis() + 2000;
            while (hub.sseClientCount() > before && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }
            assertThat(hub.sseClientCount()).isLessThanOrEqualTo(before);
        }
    }

    @Test
    void sseForUnknownGameIs404() throws Exception {
        try (SseClient sse = new SseClient(port, "/api/games/doesnotexist/events")) {
            assertThat(sse.statusLine).contains("404");
        }
    }

    // ---- concurrency ----

    @Test
    void tenSimultaneousRoundsAreAllKept() throws Exception {
        Game game = newGame();
        String path = "/api/games/" + game.id() + "/rounds";

        List<CompletableFuture<HttpResponse<String>>> futures = new ArrayList<>();
        IntStream.range(0, 10).forEach(i -> futures.add(CompletableFuture.supplyAsync(() -> {
            try {
                return post(path, "{\"scores\":[" + i + "," + (-i) + ",0]}");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        })));
        for (CompletableFuture<HttpResponse<String>> f : futures) {
            assertThat(f.get().statusCode()).isEqualTo(201);
        }

        assertThat(json.readValue(get("/api/games/" + game.id()).body(), Game.class).rounds()).hasSize(10);
    }

    // ---- helpers ----

    private Game newGame() throws Exception {
        HttpResponse<String> response = post("/api/games", "{\"name\":\"t\",\"players\":[\"An\",\"Bình\",\"Chi\"]}");
        return json.readValue(response.body(), Game.class);
    }

    private String errorOf(HttpResponse<String> response) throws Exception {
        Map<?, ?> body = json.readValue(response.body(), Map.class);
        return String.valueOf(body.get("error"));
    }

    private HttpResponse<String> get(String path) throws Exception {
        return send(request(path).GET().build());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return send(request(path).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build());
    }

    private HttpResponse<String> put(String path, String body) throws Exception {
        return send(request(path).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build());
    }

    private HttpResponse<String> delete(String path) throws Exception {
        return send(request(path).DELETE().build());
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static Path createTempDir() {
        try {
            return Files.createTempDirectory("tinhdiem-api-test");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Máy khách SSE tối giản bằng socket thô để kiểm soát chính xác lúc đóng kết nối. */
    private static final class SseClient implements AutoCloseable {
        private final Socket socket;
        private final BufferedReader in;
        final String statusLine;

        SseClient(int port, String path) throws IOException {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(5000);
            OutputStream out = socket.getOutputStream();
            out.write(("GET " + path + " HTTP/1.1\r\nHost: 127.0.0.1:" + port
                    + "\r\nAccept: text/event-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            statusLine = in.readLine();
        }

        String nextData() throws IOException {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("data:")) {
                    return line.substring("data:".length());
                }
            }
            throw new EOFException("SSE stream ended");
        }

        String nextEventName() throws IOException {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("event:")) {
                    return line.substring("event:".length()).strip();
                }
            }
            throw new EOFException("SSE stream ended");
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}

package com.tinhdiem.rules;

import java.util.List;
import java.util.Optional;

/**
 * Luật tính điểm và kiểm tra đầu vào. Các tham số là giá trị JSON "thô"
 * (List, String, Integer, Long, Double, null...) nên phải tự kiểm kiểu,
 * không để Jackson tự ép kiểu (ví dụ 1.5 thành 1).
 */
public final class Rules {

    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 12;
    public static final int MAX_NAME_LENGTH = 30;
    public static final int MAX_ABS_SCORE = 999_999_999;

    private Rules() {
    }

    /** Trả thông báo lỗi tiếng Việt, hoặc rỗng nếu hợp lệ. */
    public static Optional<String> validateScores(Object scores, int playerCount) {
        if (!(scores instanceof List<?> list)) {
            return Optional.of("Điểm phải là một danh sách số.");
        }
        if (list.size() != playerCount) {
            return Optional.of("Cần đúng " + playerCount + " điểm, nhận được " + list.size() + ".");
        }
        long sum = 0;
        for (Object value : list) {
            if (!(value instanceof Integer || value instanceof Long)) {
                return Optional.of("Điểm phải là số nguyên.");
            }
            long n = ((Number) value).longValue();
            if (n > MAX_ABS_SCORE || n < -MAX_ABS_SCORE) {
                return Optional.of("Mỗi điểm chỉ được từ -" + MAX_ABS_SCORE + " đến " + MAX_ABS_SCORE + ".");
            }
            sum += n;
        }
        if (sum != 0) {
            return Optional.of("Tổng điểm của ván phải bằng 0 (hiện là " + sum + ").");
        }
        return Optional.empty();
    }

    /** Trả thông báo lỗi tiếng Việt, hoặc rỗng nếu hợp lệ. */
    public static Optional<String> validatePlayers(Object players) {
        if (!(players instanceof List<?> list)) {
            return Optional.of("Danh sách người chơi không hợp lệ.");
        }
        if (list.size() < MIN_PLAYERS || list.size() > MAX_PLAYERS) {
            return Optional.of("Số người chơi phải từ " + MIN_PLAYERS + " đến " + MAX_PLAYERS + ".");
        }
        for (Object player : list) {
            if (!(player instanceof String name)) {
                return Optional.of("Tên người chơi phải là chữ.");
            }
            String trimmed = name.strip();
            if (trimmed.isEmpty()) {
                return Optional.of("Tên người chơi không được để trống.");
            }
            if (trimmed.length() > MAX_NAME_LENGTH) {
                return Optional.of("Tên người chơi tối đa " + MAX_NAME_LENGTH + " ký tự.");
            }
        }
        return Optional.empty();
    }

    /** Chỉ gọi sau khi {@link #validateScores} trả về rỗng. */
    public static List<Integer> toScoreList(Object validatedScores) {
        return ((List<?>) validatedScores).stream()
                .map(value -> ((Number) value).intValue())
                .toList();
    }

    /** Chỉ gọi sau khi {@link #validatePlayers} trả về rỗng. */
    public static List<String> toNameList(Object validatedPlayers) {
        return ((List<?>) validatedPlayers).stream()
                .map(name -> ((String) name).strip())
                .toList();
    }
}

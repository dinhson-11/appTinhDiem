package com.tinhdiem;

import static org.assertj.core.api.Assertions.assertThat;

import com.tinhdiem.rules.Rules;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class RulesTest {

    // ---- validateScores ----

    @Test
    void validScoresThatSumToZeroAreAccepted() {
        assertThat(Rules.validateScores(List.of(5, -5, 0), 3)).isEmpty();
    }

    @Test
    void scoresAtTheMaximumAbsoluteValueAreAccepted() {
        assertThat(Rules.validateScores(List.of(999999999, -999999999, 0), 3)).isEmpty();
    }

    @Test
    void scoresWithWrongLengthAreRejected() {
        assertThat(Rules.validateScores(List.of(5, -5), 3)).isPresent();
        assertThat(Rules.validateScores(List.of(5, -5, 0, 0), 3)).isPresent();
    }

    @Test
    void decimalScoresAreRejectedNotCoerced() {
        assertThat(Rules.validateScores(List.of(1.5, -1.5, 0), 3)).isPresent();
    }

    @Test
    void stringScoresAreRejected() {
        assertThat(Rules.validateScores(List.of("5", -5, 0), 3)).isPresent();
    }

    @Test
    void nullScoreIsRejected() {
        assertThat(Rules.validateScores(Arrays.asList(5, null, -5), 3)).isPresent();
    }

    @Test
    void scoresBeyondTheMaximumAreRejected() {
        assertThat(Rules.validateScores(List.of(1000000000, -1000000000, 0), 3)).isPresent();
    }

    @Test
    void scoresBeyondJavascriptSafeIntegerAreRejected() {
        assertThat(Rules.validateScores(List.of(9007199254740992L, -9007199254740992L, 0), 3)).isPresent();
    }

    @Test
    void longMinValueIsRejected() {
        assertThat(Rules.validateScores(List.of(Long.MIN_VALUE, Long.MAX_VALUE, 0), 3)).isPresent();
    }

    @Test
    void scoresBeyondLongAreRejected() {
        assertThat(Rules.validateScores(List.of(new BigInteger("99999999999999999999"), -1, 0), 3)).isPresent();
    }

    @Test
    void scoresNotSummingToZeroAreRejected() {
        assertThat(Rules.validateScores(List.of(1, 2, 3), 3)).isPresent();
    }

    @Test
    void nonListScoresAreRejected() {
        assertThat(Rules.validateScores("x", 3)).isPresent();
        assertThat(Rules.validateScores(null, 3)).isPresent();
    }

    // ---- validatePlayers ----

    @Test
    void twoAndTwelvePlayersAreAccepted() {
        assertThat(Rules.validatePlayers(names(2))).isEmpty();
        assertThat(Rules.validatePlayers(names(12))).isEmpty();
    }

    @Test
    void oneAndThirteenPlayersAreRejected() {
        assertThat(Rules.validatePlayers(names(1))).isPresent();
        assertThat(Rules.validatePlayers(names(13))).isPresent();
    }

    @Test
    void emptyAndBlankNamesAreRejected() {
        assertThat(Rules.validatePlayers(List.of("An", ""))).isPresent();
        assertThat(Rules.validatePlayers(List.of("An", "   "))).isPresent();
    }

    @Test
    void nameOfExactlyThirtyCharactersIsAcceptedEvenWithPadding() {
        String thirty = "a".repeat(30);
        assertThat(Rules.validatePlayers(List.of(thirty, "B"))).isEmpty();
        assertThat(Rules.validatePlayers(List.of("  " + thirty + "  ", "B"))).isEmpty();
    }

    @Test
    void nameOfThirtyOneCharactersIsRejected() {
        assertThat(Rules.validatePlayers(List.of("a".repeat(31), "B"))).isPresent();
    }

    @Test
    void nonStringNameIsRejected() {
        assertThat(Rules.validatePlayers(List.of("An", 5))).isPresent();
    }

    @Test
    void nonListPlayersAreRejected() {
        assertThat(Rules.validatePlayers("An")).isPresent();
        assertThat(Rules.validatePlayers(null)).isPresent();
    }

    // ---- conversions ----

    @Test
    void toScoreListConvertsIntegersAndLongsToIntegers() {
        assertThat(Rules.toScoreList(List.of(5L, -5, 0))).containsExactly(5, -5, 0);
    }

    @Test
    void toNameListTrimsNames() {
        assertThat(Rules.toNameList(List.of("  An ", "Bình"))).containsExactly("An", "Bình");
    }

    private static List<Object> names(int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
                .<Object>mapToObj(i -> "Người " + i)
                .toList();
    }
}

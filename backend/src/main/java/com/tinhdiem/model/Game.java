package com.tinhdiem.model;

import java.util.List;

public record Game(
        String id,
        String name,
        List<String> players,
        List<Round> rounds,
        long createdAt,
        long updatedAt) {
}

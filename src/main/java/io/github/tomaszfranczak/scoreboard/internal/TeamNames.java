package io.github.tomaszfranczak.scoreboard.internal;

final class TeamNames {

    private TeamNames() {
    }

    static String normalize(String name) {
        return name.strip();
    }

    static boolean same(String first, String second) {
        return first.equalsIgnoreCase(second);
    }
}

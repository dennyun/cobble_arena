package cobblemon.arena.format;

public enum BattleMode {
    RANKED("Ranqueado", true),
    CASUAL("Casual", false);

    private final String displayName;
    private final boolean affectsElo;

    BattleMode(String displayName, boolean affectsElo) {
        this.displayName = displayName;
        this.affectsElo = affectsElo;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean affectsElo() {
        return affectsElo;
    }
}

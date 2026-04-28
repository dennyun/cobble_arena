package cobblemon.arena.format;

public enum BattleStructure {
    SINGLES("singles", "Singles", 1),
    DOUBLES("doubles", "Duplas", 2),
    TRIPLES("triples", "Triplas", 3),
    MONOTYPE("singles", "Monotype", 1); // Monotype usa singles estruturalmente

    private final String cobblemonId;
    private final String displayName;
    private final int activePokemon;

    BattleStructure(String cobblemonId, String displayName, int activePokemon) {
        this.cobblemonId = cobblemonId;
        this.displayName = displayName;
        this.activePokemon = activePokemon;
    }

    public String getCobblemonId() {
        return cobblemonId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getActivePokemon() {
        return activePokemon;
    }
}

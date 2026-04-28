package cobblemon.arena.quest;

public enum QuestType {
    PLAY_MATCHES("Jogar Partidas"),
    WIN_MATCHES("Vencer Partidas"),
    WIN_RANKED("Vencer no Ranqueado"),
    WIN_STREAK("Win Streak"),
    PLAY_RANKED("Jogar Ranqueado"),
    PLAY_CASUAL("Jogar Casual"),
    USE_TYPE("Usar Tipo de Pokémon");

    private final String displayName;

    QuestType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

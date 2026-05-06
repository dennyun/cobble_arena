package cobblemon.arena.quest;

public enum QuestType {
    PLAY_MATCHES("Jogar Partidas"),
    WIN_MATCHES("Vencer Partidas"),
    WIN_RANKED("Vencer no Ranqueado"),
    WIN_STREAK("Win Streak"),
    PLAY_RANKED("Jogar Ranqueado"),
    PLAY_CASUAL("Jogar Casual"),
    USE_TYPE("Usar Tipo de Pokémon"),
    WIN_WITH_ALIVE("Vencer Com Pokémon Vivos"),
    WIN_FAST("Vencer Rápido"),
    PLAY_LONG("Batalha Longa / Turnos"),
    WIN_BY_FORFEIT("Vencer Por Desistência"),
    PLAY_NO_FORFEIT("Sem Desistir"),
    PLAY_MONOTYPE("Jogar Monotype"),
    KNOCKOUT_TOTAL("Nocautes Totais");

    private final String displayName;

    QuestType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

package cobblemon.arena.stats;

/**
 * ELO rating calculator with dynamic K-factor support.
 *
 * <p>The K-factor controls how many rating points are at stake in a single match.
 * A higher K causes ratings to move faster, which is desirable for new players
 * who need to reach their true skill level quickly. Veteran players receive a
 * lower K so their ratings are more stable.
 *
 * <p>Three tiers are defined:
 * <ul>
 *   <li><b>Placement</b> (K=32) — fewer than 30 games played. Ratings settle
 *       quickly during early play.</li>
 *   <li><b>Standard</b> (K=24) — 30 to 99 games played. A balanced rate of
 *       change for developing players.</li>
 *   <li><b>Veteran</b> (K=16) — 100 or more games played. Ratings are more
 *       resistant to single-match swings for established players.</li>
 * </ul>
 *
 * <p>All public methods are static; this class is not instantiable.
 */
public final class EloCalculator {

    /** K-factor applied during placement (fewer than {@value #PLACEMENT_THRESHOLD} games). */
    private static final int K_PLACEMENT = 32;

    /** K-factor applied during the standard bracket ({@value #PLACEMENT_THRESHOLD}–{@value #VETERAN_THRESHOLD} games). */
    private static final int K_STANDARD = 24;

    /** K-factor applied once a player has {@value #VETERAN_THRESHOLD} or more games. */
    private static final int K_VETERAN = 16;

    /** Minimum number of games required to leave the placement bracket. */
    private static final int PLACEMENT_THRESHOLD = 30;

    /** Minimum number of games required to enter the veteran bracket. */
    private static final int VETERAN_THRESHOLD = 100;

    private EloCalculator() {}

    // -------------------------------------------------------------------------
    // Core formulas
    // -------------------------------------------------------------------------

    /**
     * Returns the expected score (probability of winning) for a player against
     * an opponent using the standard ELO formula.
     *
     * @param playerRating   the player's current rating
     * @param opponentRating the opponent's current rating
     * @return a value in the range [0.0, 1.0]
     */
    public static double getExpectedScore(
        int playerRating,
        int opponentRating
    ) {
        return (
            1.0 /
            (1.0 + Math.pow(10.0, (opponentRating - playerRating) / 400.0))
        );
    }

    /**
     * Returns the K-factor appropriate for a player based on how many ranked
     * games they have played in total (across all seasons and ladders).
     *
     * @param totalGamesPlayed total ranked games played by the player
     * @return {@link #K_PLACEMENT}, {@link #K_STANDARD}, or {@link #K_VETERAN}
     */
    public static int getKFactor(int totalGamesPlayed) {
        if (totalGamesPlayed < PLACEMENT_THRESHOLD) return K_PLACEMENT;
        if (totalGamesPlayed < VETERAN_THRESHOLD) return K_STANDARD;
        return K_VETERAN;
    }

    // -------------------------------------------------------------------------
    // Rating calculation — dynamic K (preferred)
    // -------------------------------------------------------------------------

    /**
     * Calculates a player's new rating after a match using a dynamic K-factor
     * derived from their total games played.
     *
     * @param currentRating    the player's rating before the match
     * @param opponentRating   the opponent's rating before the match
     * @param actualScore      1.0 for a win, 0.0 for a loss, 0.5 for a draw
     * @param totalGamesPlayed total ranked games the player has played (used to
     *                         determine the K-factor tier)
     * @return the player's new rating after applying the ELO formula
     */
    public static int calculateNewRating(
        int currentRating,
        int opponentRating,
        double actualScore,
        int totalGamesPlayed
    ) {
        int k = getKFactor(totalGamesPlayed);
        double expected = getExpectedScore(currentRating, opponentRating);
        double change = k * (actualScore - expected);
        return currentRating + (int) Math.round(change);
    }

    /**
     * Calculates the signed rating change for a player after a match using a
     * dynamic K-factor.
     *
     * @param playerRating     the player's rating before the match
     * @param opponentRating   the opponent's rating before the match
     * @param won              {@code true} if the player won, {@code false} if lost
     * @param totalGamesPlayed total ranked games played (determines K-factor)
     * @return the signed rating delta (positive = gained, negative = lost)
     */
    public static int calculateRatingChange(
        int playerRating,
        int opponentRating,
        boolean won,
        int totalGamesPlayed
    ) {
        double actualScore = won ? 1.0 : 0.0;
        int newRating = calculateNewRating(
            playerRating,
            opponentRating,
            actualScore,
            totalGamesPlayed
        );
        return newRating - playerRating;
    }

    // -------------------------------------------------------------------------
    // Rating calculation — legacy overloads (fixed K=32, kept for compatibility)
    // -------------------------------------------------------------------------

    /**
     * Calculates a player's new rating using a fixed K=32.
     *
     * @deprecated Prefer {@link #calculateNewRating(int, int, double, int)} so
     *             the K-factor scales with the player's experience. This overload
     *             is retained for call-sites that do not yet track total games.
     */
    @Deprecated
    public static int calculateNewRating(
        int currentRating,
        int opponentRating,
        double actualScore
    ) {
        // Delegate to the dynamic version with 0 games, which resolves to K_PLACEMENT (32).
        return calculateNewRating(
            currentRating,
            opponentRating,
            actualScore,
            0
        );
    }

    /**
     * Calculates the signed rating change for a player after a match using a
     * fixed K=32.
     *
     * @deprecated Prefer {@link #calculateRatingChange(int, int, boolean, int)}
     *             so the K-factor scales with the player's experience.
     */
    @Deprecated
    public static int calculateRatingChange(
        int playerRating,
        int opponentRating,
        boolean won
    ) {
        // Delegate to the dynamic version with 0 games, which resolves to K_PLACEMENT (32).
        return calculateRatingChange(playerRating, opponentRating, won, 0);
    }

    // -------------------------------------------------------------------------
    // Formatting
    // -------------------------------------------------------------------------

    /**
     * Formats a signed rating change as a coloured string for display in chat.
     *
     * <ul>
     *   <li>Positive changes are rendered in §a (green) with a leading {@code +}.</li>
     *   <li>Negative changes are rendered in §c (red).</li>
     *   <li>Zero is rendered in §7 (grey).</li>
     * </ul>
     *
     * @param change the signed rating delta
     * @return a Minecraft-formatted string
     */
    public static String formatRatingChange(int change) {
        if (change > 0) return "§a+" + change;
        if (change < 0) return "§c" + change;
        return "§70";
    }
}

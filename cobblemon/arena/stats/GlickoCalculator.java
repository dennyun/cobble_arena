package cobblemon.arena.stats;

public final class GlickoCalculator {

    private static final double TAU = 0.5;
    private static final double SCALE = 173.7178;
    private static final double EPSILON = 0.000001;

    public static class GlickoResult {
        public final double newRating;
        public final double newRD;
        public final double newVolatility;

        public GlickoResult(double newRating, double newRD, double newVolatility) {
            this.newRating = newRating;
            this.newRD = newRD;
            this.newVolatility = newVolatility;
        }
    }

    public static class RPResult {
        public final int rpChange;
        public final int newRP;
        public final GlickoResult glicko;

        public RPResult(int rpChange, int newRP, GlickoResult glicko) {
            this.rpChange = rpChange;
            this.newRP = newRP;
            this.glicko = glicko;
        }
    }

    private GlickoCalculator() {}

    public static GlickoResult calculateGlicko(
            double rating, double rd, double vol,
            double oppRating, double oppRd,
            boolean win) {

        double mu = (rating - 1500) / SCALE;
        double phi = rd / SCALE;

        double mu_j = (oppRating - 1500) / SCALE;
        double phi_j = oppRd / SCALE;

        double score = win ? 1.0 : 0.0;

        double g = 1.0 / Math.sqrt(1.0 + 3.0 * phi_j * phi_j / (Math.PI * Math.PI));
        double E = 1.0 / (1.0 + Math.exp(-g * (mu - mu_j)));

        double v = 1.0 / (g * g * E * (1.0 - E));
        double delta = v * g * (score - E);

        double a = Math.log(vol * vol);
        double A = a;
        double B;
        if (delta * delta > phi * phi + v) {
            B = Math.log(delta * delta - phi * phi - v);
        } else {
            double k = 1;
            while (f(a - k * TAU, delta, phi, v, a) < 0) {
                k++;
            }
            B = a - k * TAU;
        }

        double fA = f(A, delta, phi, v, a);
        double fB = f(B, delta, phi, v, a);

        while (Math.abs(B - A) > EPSILON) {
            double C = A + (A - B) * fA / (fB - fA);
            double fC = f(C, delta, phi, v, a);
            if (fC * fB <= 0) {
                A = B;
                fA = fB;
            } else {
                fA = fA / 2.0;
            }
            B = C;
            fB = fC;
        }

        double newVol = Math.exp(A / 2.0);
        double phiStar = Math.sqrt(phi * phi + newVol * newVol);
        double newPhi = 1.0 / Math.sqrt(1.0 / (phiStar * phiStar) + 1.0 / v);
        double newMu = mu + newPhi * newPhi * g * (score - E);

        double finalRating = newMu * SCALE + 1500;
        double finalRD = newPhi * SCALE;

        return new GlickoResult(finalRating, finalRD, newVol);
    }

    private static double f(double x, double delta, double phi, double v, double a) {
        double ex = Math.exp(x);
        double num = ex * (delta * delta - phi * phi - v - ex);
        double den = 2.0 * Math.pow(phi * phi + v + ex, 2.0);
        return (num / den) - ((x - a) / (TAU * TAU));
    }

    public static RPResult calculateRP(
            PlayerStats.RankedLadderStats player,
            PlayerStats.RankedLadderStats opponent,
            boolean win) {

        GlickoResult glicko = calculateGlicko(
                player.getRating(), player.getRd(), player.getVolatility(),
                opponent.getRating(), opponent.getRd(),
                win
        );

        double deltaRating = glicko.newRating - player.getRating();
        double multiplier = getTierMultiplier(player.getRp());
        double rdModifier = getRDModifier(player.getRd());
        double streakModifier = getStreakModifier(player, win);

        int rpChange = (int) Math.round(deltaRating * multiplier * rdModifier * streakModifier);

        if (!win) {
            // Loss Streak Protection
            if (player.getRankedLossStreak() >= 3) {
                rpChange = (int) Math.round(rpChange * 0.8); // -20% loss
            }
            
            // Rank Protection
            if (player.getProtectionMatches() > 0 && rpChange < 0) {
                int currentTierFloor = getTierFloor(player.getRp());
                if (player.getRp() + rpChange < currentTierFloor) {
                    rpChange = currentTierFloor - player.getRp();
                }
            }
        }

        int newRP = Math.max(0, player.getRp() + rpChange);

        return new RPResult(rpChange, newRP, glicko);
    }

    private static double getTierMultiplier(int rp) {
        if (rp < 400) return 1.4; // Iniciante
        if (rp < 800) return 1.2; // Bronze
        if (rp < 1200) return 1.0; // Prata
        if (rp < 1600) return 1.0; // Ouro
        if (rp < 2000) return 0.95; // Platina
        if (rp < 2400) return 0.9; // Diamante
        return 1.0; // Mestre+
    }

    private static double getRDModifier(double rd) {
        if (rd > 200) return 1.3;
        if (rd >= 100) return 1.1;
        return 0.9;
    }

    private static double getStreakModifier(PlayerStats.RankedLadderStats p, boolean win) {
        if (win && p.getRp() < 1600 && p.getRankedStreak() >= 3) { // <= Ouro and streak >= 3
            return 1.2; // +20% RP
        }
        return 1.0;
    }

    public static int getTierFloor(int rp) {
        if (rp >= 2400) return 2400;
        if (rp >= 2000) return 2000;
        if (rp >= 1600) return 1600;
        if (rp >= 1200) return 1200;
        if (rp >= 800) return 800;
        if (rp >= 400) return 400;
        return 0;
    }
}

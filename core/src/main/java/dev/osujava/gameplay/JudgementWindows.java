package dev.osujava.gameplay;

public record JudgementWindows(double hit300Ms, double hit100Ms, double hit50Ms) {
    public JudgementWindows {
        if (hit300Ms < 0 || hit100Ms < hit300Ms || hit50Ms < hit100Ms) {
            throw new IllegalArgumentException("Judgement windows must be non-negative and ordered");
        }
    }

    public static JudgementWindows fromOverallDifficulty(double overallDifficulty) {
        double od = Math.max(0, Math.min(10, overallDifficulty));
        return new JudgementWindows( Math.max(0, 79.5 - 6 * od),
                Math.max(0, 139.5 - 8 * od), Math.max(0, 199.5 - 10 * od));
    }

    public Judgement judge(double absoluteOffsetMs) {
        if (absoluteOffsetMs <= hit300Ms) return Judgement.HIT300;
        if (absoluteOffsetMs <= hit100Ms) return Judgement.HIT100;
        if (absoluteOffsetMs <= hit50Ms) return Judgement.HIT50;
        return Judgement.MISS;
    }
}

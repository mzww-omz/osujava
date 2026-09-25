package dev.osujava.gameplay;

public enum Judgement {
    HIT300(300),
    HIT100(100),
    HIT50(50),
    MISS(0);

    private final int scoreValue;

    Judgement(int scoreValue) {
        this.scoreValue = scoreValue;
    }

    public int scoreValue() {
        return scoreValue;
    }
}

package dev.vfyjxf.cirrus.api.planning.context;

/**
 * Four physical inventory access channels (M2.2) with default opening friction beta_l (s_e)
 * and linear extraction seconds c_l.
 */
public enum InventoryTier {
    hand(0.4, 0.0, 0.50),
    network(0.8, 0.02, 0.45),
    nearby(2.0, 0.05, 0.55),
    remote(5.0, 0.12, 0.70);

    private final double defaultOpenFrictionSeconds;
    private final double defaultLinearSecondsPerUnit;
    private final double defaultAlphaOpp;

    InventoryTier(double defaultOpenFrictionSeconds, double defaultLinearSecondsPerUnit, double defaultAlphaOpp) {
        this.defaultOpenFrictionSeconds = defaultOpenFrictionSeconds;
        this.defaultLinearSecondsPerUnit = defaultLinearSecondsPerUnit;
        this.defaultAlphaOpp = defaultAlphaOpp;
    }

    public double defaultOpenFrictionSeconds() {
        return defaultOpenFrictionSeconds;
    }

    public double defaultLinearSecondsPerUnit() {
        return defaultLinearSecondsPerUnit;
    }

    public double defaultAlphaOpp() {
        return defaultAlphaOpp;
    }
}

package com.personalink.server.model;

import java.math.BigDecimal;

/**
 * 单个维度的实际分与理论边界。
 */
public class ScoreAccumulator {

    private BigDecimal raw = BigDecimal.ZERO;
    private BigDecimal minimum = BigDecimal.ZERO;
    private BigDecimal maximum = BigDecimal.ZERO;

    public BigDecimal getRaw() { return this.raw; }
    public BigDecimal getMinimum() { return this.minimum; }
    public BigDecimal getMaximum() { return this.maximum; }
    public void addRaw(BigDecimal value) { this.raw = this.raw.add(value); }
    public void addMinimum(BigDecimal value) { this.minimum = this.minimum.add(value); }
    public void addMaximum(BigDecimal value) { this.maximum = this.maximum.add(value); }
}

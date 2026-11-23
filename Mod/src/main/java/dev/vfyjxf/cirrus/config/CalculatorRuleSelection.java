package dev.vfyjxf.cirrus.config;

/**
 * Persisted calculator rule preset choice, kept as a stable config value until the planning bridge
 * exposes presets again.
 */
public enum CalculatorRuleSelection {
    preview,
    active,
    execution,
    strict,
    assured;

    // TODO(planning-bridge): 规则档案/规则集切换随旧路由删除，待 bridge 提供新预设后再接回（旧路由已删除，待 bridge 接线）
}

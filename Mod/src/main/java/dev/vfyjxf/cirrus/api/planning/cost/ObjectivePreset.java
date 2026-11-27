package dev.vfyjxf.cirrus.api.planning.cost;

/**
 * Six human-intuitive planning presets (Planning v3 M5):
 * autoSmart (default), stayAtBase (factory/trip-averse), quickHandcraft (minimal new machines),
 * factoryScale (mass production/automation), frugalMaterials (resource efficiency),
 * and lowCognitive (minimal steps & family switching).
 */
public enum ObjectivePreset {
    autoSmart,
    stayAtBase,
    quickHandcraft,
    factoryScale,
    frugalMaterials,
    lowCognitive
}

package com.particuletoy.core;

/**
 * High-level phase for elements.
 *
 * Note: Some elements in a cellular automaton are powders (granular solids) which are still
 * modeled as {@link #SOLID} from a thermodynamic perspective.
 */
public enum Phase {
    SOLID,
    LIQUID,
    GAS
}

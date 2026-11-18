package app.porchlight.game

/**
 * The plain board most rule tests use: six pairs, a 30-second bulb and a 2-second peek, the
 * numbers the rules were first written against.
 */
val CLASSIC = Level(number = 4, chapter = 1, pairs = 6, roundMs = 30_000, peekMs = 2_000)

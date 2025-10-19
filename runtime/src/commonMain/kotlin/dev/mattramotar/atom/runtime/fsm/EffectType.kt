package dev.mattramotar.atom.runtime.fsm

/**
 * Categories for [SideEffect] classification.
 *
 * [EffectType] enables filtering, metrics, and prioritization of side effects:
 *
 * - **NETWORK**: HTTP requests, WebSocket messages
 * - **DB**: Database reads/writes
 * - **COMPUTE**: CPU-intensive operations
 * - **RENDER**: UI updates, navigation
 * - **REDUCE**: Triggering additional reducer cycles
 *
 * ## Usage
 *
 * Specify effect type in implementations:
 *
 * ```kotlin
 * data object LoadTodos : TodoEffect {
 *     override val effectType = EffectType.NETWORK
 * }
 * ```
 */
enum class EffectType {
    NETWORK, DB, COMPUTE, RENDER, REDUCE
}

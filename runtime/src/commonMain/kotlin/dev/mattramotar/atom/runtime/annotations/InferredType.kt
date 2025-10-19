package dev.mattramotar.atom.runtime.annotations

/**
 * Marker object indicating that a type should be inferred from the atom class structure.
 *
 * [InferredType] is used as the default value for [AutoAtom.state] and [AutoAtom.params],
 * enabling type inference from:
 * - The `Atom<S, I, E, F>` supertype (for state type `S`)
 * - The `@InitialState` function signature (for params type `P`)
 *
 * This allows developers to write minimal annotations while the KSP processor performs
 * compile-time type extraction, reducing boilerplate and ensuring type consistency.
 *
 * ## How Inference Works
 *
 * ### State Type Inference
 *
 * The KSP processor extracts the state type `S` from the `Atom<S, I, E, F>` supertype:
 *
 * ```kotlin
 * @AutoAtom  // state = InferredType::class (default)
 * class TodoAtom(
 *     scope: CoroutineScope,
 *     handle: StateHandle<TodoState>,  // TodoState appears here too, but...
 *     params: TodoAtomParams
 * ) : Atom<TodoState, TodoIntent, TodoEvent, TodoEffect>(scope, handle) {
 *     //       ^^^^^^^^^ Extracted from here
 * }
 * ```
 *
 * ### Params Type Inference
 *
 * The processor extracts the params type `P` from the `@InitialState` function:
 *
 * ```kotlin
 * companion object {
 *     @InitialState
 *     fun initial(params: TodoAtomParams): TodoState
 *     //                  ^^^^^^^^^^^^^^ Extracted from here
 * }
 * ```
 *
 * ## When to Use Explicit Types
 *
 * While inference is recommended, explicit types are useful in these scenarios:
 *
 * ### Documentation
 * ```kotlin
 * // Make types prominent in the annotation for API documentation
 * @AutoAtom(state = ComplexState::class, params = ComplexParams::class)
 * class ComplexAtom(...) : Atom<ComplexState, ...>
 * ```
 *
 * ### Validation
 * ```kotlin
 * // Catch mismatches early with compile-time validation
 * @AutoAtom(state = TodoState::class, params = TodoAtomParams::class)
 * class TodoAtom(...) : Atom<TodoState, ...> {
 *     companion object {
 *         @InitialState
 *         fun initial(params: TodoAtomParams): TodoState  // Validated against annotation
 *     }
 * }
 * ```
 *
 * ### Ambiguous Generics
 * ```kotlin
 * // When using complex generic hierarchies that confuse the processor
 * abstract class BaseAtom<S : BaseState>(...) : Atom<S, ...>
 *
 * @AutoAtom(state = ConcreteState::class)  // Explicit type resolves ambiguity
 * class ConcreteAtom(...) : BaseAtom<ConcreteState>(...)
 * ```
 *
 * ## Implementation Details
 *
 * [InferredType] is a stateless marker object used only at compile time. The KSP processor
 * detects `InferredType::class` and triggers its type extraction algorithms. At runtime,
 * [InferredType] is never instantiated or referenced.
 *
 * ## Inference Failures
 *
 * Inference fails with a compilation error if:
 * - The atom class doesn't extend `Atom<S, I, E, F>`
 * - The `@InitialState` function is missing or has an invalid signature
 * - Generic type parameters cannot be resolved to concrete types
 *
 * In these cases, either fix the atom structure or specify explicit types in [AutoAtom].
 *
 * ## Thread Safety
 *
 * [InferredType] is a stateless marker object - no thread safety concerns.
 *
 * @see AutoAtom.state for state type configuration
 * @see AutoAtom.params for params type configuration
 * @see InferredScope for Metro scope inference
 */
object InferredType

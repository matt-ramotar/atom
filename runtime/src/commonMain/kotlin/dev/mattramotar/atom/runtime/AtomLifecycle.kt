package dev.mattramotar.atom.runtime

/**
 * Lifecycle callbacks for atom instances.
 *
 * [AtomLifecycle] defines optional hooks that atoms can implement to perform initialization,
 * cleanup, and resource management in response to lifecycle events. The Atom runtime invokes
 * these callbacks at appropriate times during the atom's lifecycle.
 *
 * ## Lifecycle Phases
 *
 * An atom progresses through these phases:
 *
 * 1. **Construction**: Atom instance created via factory
 * 2. **Start** ([onStart]): Called when atom is first acquired by a consumer
 * 3. **Active**: Atom processes intents and updates state
 * 4. **Stop** ([onStop]): Called when last consumer releases the atom
 * 5. **Dispose** ([onDispose]): Called for final cleanup before garbage collection
 *
 * ## Callback Order
 *
 * The runtime guarantees the following callback order:
 * - [onStart] is called exactly once when the atom is first acquired
 * - [onStop] is called exactly once when the atom's reference count reaches zero
 * - [onDispose] is called after [onStop] (if [onStop] was called)
 * - [onStart] may be called again if the atom is re-acquired after being stopped
 *
 * ## Usage
 *
 * Atoms automatically implement [AtomLifecycle] by extending the `Atom` base class:
 *
 * ```kotlin
 * @AutoAtom
 * class TodoAtom(
 *     scope: CoroutineScope,
 *     handle: StateHandle<TodoState>,
 *     params: TodoAtomParams
 * ) : Atom<TodoState, TodoIntent, TodoEvent, TodoEffect>(scope, handle) {
 *
 *     private val repository = params.repository
 *
 *     override fun onStart() {
 *         // Initialize: subscribe to repository, start background tasks
 *         scope.launch {
 *             repository.observeTodos().collect { todos ->
 *                 dispatch(TodoEvent.TodosLoaded(todos))
 *             }
 *         }
 *     }
 *
 *     override fun onStop() {
 *         // Pause: cancel subscriptions (scope automatically cancelled)
 *         log.info("TodoAtom stopped")
 *     }
 *
 *     override fun onDispose() {
 *         // Cleanup: release resources, close connections
 *         repository.close()
 *     }
 * }
 * ```
 *
 * ## onStart(): Initialization
 *
 * [onStart] is called when the atom is first acquired. Use this for:
 * - Starting background coroutines (launched in the atom's `scope`)
 * - Subscribing to data sources (repositories, sensors, network)
 * - Initializing stateful resources
 * - Emitting initial side effects
 *
 * **Threading**: [onStart] is called on the main thread in Compose contexts. Perform
 * long-running initialization in coroutines launched in the atom's `scope`.
 *
 * ```kotlin
 * override fun onStart() {
 *     scope.launch {
 *         // Load initial data
 *         dispatch(TodoEvent.LoadTodos)
 *     }
 * }
 * ```
 *
 * ## onStop(): Pause
 *
 * [onStop] is called when the atom's reference count reaches zero (all consumers released).
 * Use this for:
 * - Pausing background work
 * - Unsubscribing from data sources
 * - Logging lifecycle events
 *
 * **Note**: Built-in owners (for example Compose `atom()` and [dev.mattramotar.atom.runtime.child.ChildAtomProvider])
 * cancel the atom's coroutine scope/job before [onStop] is invoked. If lifecycle callbacks are
 * invoked manually, cancellation sequencing is the caller's responsibility.
 *
 * ```kotlin
 * override fun onStop() {
 *     analytics.trackAtomStopped("TodoAtom")
 * }
 * ```
 *
 * ## onDispose(): Cleanup
 *
 * [onDispose] is called for final resource cleanup before garbage collection. Use this for:
 * - Closing database connections
 * - Releasing file handles
 * - Cleaning up native resources
 * - Cancelling external subscriptions
 *
 * **Error Handling**: [onDispose] is called even if the atom was in an error state. Ensure
 * cleanup logic is defensive and doesn't throw exceptions.
 *
 * ```kotlin
 * override fun onDispose() {
 *     runCatching { repository.close() }
 *     runCatching { cache.clear() }
 * }
 * ```
 *
 * ## Lifecycle in Compose
 *
 * When using atoms in Compose, lifecycle is tied to composition:
 *
 * ```kotlin
 * @Composable
 * fun TodoScreen() {
 *     val atom = atom<TodoAtom>()  // onStart() called on first composition
 *
 *     // ... use atom
 *
 *     // onStop() and onDispose() called when composition leaves
 * }
 * ```
 *
 * ## Lifecycle with Reference Counting
 *
 * The [AtomStore] maintains reference counts for atoms:
 * - **First acquisition**: `refs = 0 -> 1` → [onStart] called
 * - **Subsequent acquisitions**: `refs = 1 -> 2` → no callback
 * - **Release**: `refs = 2 -> 1` → no callback
 * - **Last release**: `refs = 1 -> 0` → entry returned for [onStop]/[onDispose] by the owner
 *
 * ## Thread Safety
 *
 * - [onStart], [onStop], and [onDispose] may be called from different threads.
 * - Built-in owners serialize lifecycle callbacks per atom instance.
 * - Callbacks are invoked outside AtomStore internal locks.
 *
 * ## Default Implementations
 *
 * All lifecycle callbacks have no-op default implementations. Override only the callbacks
 * you need - most atoms only override [onStart].
 *
 * @see Atom for the base atom implementation
 * @see dev.mattramotar.atom.runtime.store.AtomStore for reference-counted atom management
 */
interface AtomLifecycle {
    /**
     * Called when the atom is first acquired (reference count: 0 → 1).
     *
     * Use this to initialize the atom, start background work, and subscribe to data sources.
     * This method is called exactly once when the atom becomes active.
     *
     * **Threading**: Called on the thread that acquires the atom (typically main thread in Compose).
     * Launch coroutines in the atom's `scope` for asynchronous initialization.
     *
     * **Error Handling**: Exceptions thrown from [onStart] propagate to the caller. The runtime
     * removes the entry and cancels its job to avoid retaining a partially started atom.
     */
    fun onStart() {}

    /**
     * Called when the atom's reference count reaches zero (all consumers released).
     *
     * Use this to pause background work, unsubscribe from data sources, and log lifecycle events.
     * The atom's coroutine scope is cancelled before this method is called.
     *
     * **Threading**: Called on the thread that releases the last reference.
     *
     * **Error Handling**: Built-in owners typically catch and ignore [onStop] exceptions during
     * disposal paths. If callbacks are invoked manually, exceptions may propagate.
     */
    fun onStop() {}

    /**
     * Called for final cleanup before the atom is garbage collected.
     *
     * Use this to release native resources, close connections, and perform final cleanup.
     * This is the last callback before the atom instance becomes eligible for garbage collection.
     *
     * **Threading**: Called on the thread that released the last reference, after [onStop].
     *
     * **Error Handling**: Built-in owners typically catch and ignore [onDispose] exceptions during
     * disposal paths. If callbacks are invoked manually, exceptions may propagate.
     */
    fun onDispose() {}
}

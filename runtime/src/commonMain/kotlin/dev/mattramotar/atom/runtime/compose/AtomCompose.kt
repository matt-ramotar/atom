package dev.mattramotar.atom.runtime.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.mattramotar.atom.runtime.AtomKey
import dev.mattramotar.atom.runtime.AtomLifecycle
import kotlinx.coroutines.*

/**
 * Creates or retrieves an atom instance tied to the composition lifecycle.
 *
 * ## Usage
 *
 * ```kotlin
 * @Composable
 * fun TodoScreen() {
 *     val atom = atom<TodoAtom>()
 *     val state by atom.state.collectAsState()
 *
 *     TodoList(
 *         todos = state.todos,
 *         onAddTodo = { atom.intent(TodoIntent.AddTodo(it)) }
 *     )
 * }
 * ```
 *
 * ## Multi-Instance Atoms
 *
 * Use `key` to create multiple instances:
 *
 * ```kotlin
 * @Composable
 * fun TodoItem(todoId: String) {
 *     val atom = atom<TodoAtom>(key = todoId)
 *     // Different todoId → different atom instance
 * }
 * ```
 *
 * ## Params
 *
 * Pass initialization parameters:
 *
 * ```kotlin
 * val atom = atom<TodoAtom>(
 *     key = todoId,
 *     params = TodoAtomParams(userId = currentUser.id)
 * )
 * ```
 *
 * Params are part of the Compose identity for an atom instance. If `params` changes while
 * `key` stays the same, the previous instance is released and a new one is created. Keep
 * `params` stable to retain the same instance across recompositions.
 *
 * @param A The atom type
 * @param key Optional instance key for multi-instance atoms
 * @param params Initialization parameters (defaults to `Unit`)
 * @return The atom instance
 */
@Composable
inline fun <reified A : AtomLifecycle> atom(
    key: Any? = null,
    params: Any = Unit
): A {
    val type = A::class
    val storeOwner = LocalAtomStoreOwner.current
    val registry = LocalAtomFactories.current
    val handles = LocalStateHandleFactory.current
    val parentScope = rememberCoroutineScope()
    val atomKey = remember(type, key, params) {
        AtomKey(type, AtomParamsKey(instanceKey = key, params = params))
    }

    val instance = remember(atomKey) {
        val entry = registry.entryFor(type) ?: error("No AtomFactoryEntry for ${type.simpleName}.")

        val expectsUnitParams = entry.paramsClass == Unit::class
        require(
            entry.paramsClass.isInstance(params) ||
                    (expectsUnitParams && params == Unit)
        ) {
            "Expected params of type ${entry.paramsClass.simpleName} for ${type.simpleName} but got ${params::class.simpleName}"
        }

        val parentJob = checkNotNull(parentScope.coroutineContext[Job]) { "No Job in parent scope!" }

        storeOwner.atomStore.acquire(atomKey) {
            val scopeJob = SupervisorJob(parentJob)
            val keyName = key?.let { "[$it]" } ?: ""
            val scope = CoroutineScope(
                parentScope.coroutineContext + scopeJob + CoroutineName("Atom:${type.simpleName}$keyName")
            )

            @Suppress("UNCHECKED_CAST")
            val stateClass = entry.stateClass as kotlin.reflect.KClass<Any>

            val state = handles.create(
                key = atomKey,
                stateClass = stateClass,
                initial = { entry.initialAny(params) },
                serializer = entry.serializerAny
            )

            @Suppress("UNCHECKED_CAST")
            val a = entry.createAny(scope, state, params) as A
            // onStart() is called by AtomStore.acquire() after installation
            a to scopeJob
        }
    }

    DisposableEffect(atomKey) {
        onDispose {
            val managed = storeOwner.atomStore.release(atomKey)
            if (managed != null) {
                parentScope.launch {
                    withContext(NonCancellable) {
                        managed.job?.cancel()
                        runCatching { managed.lifecycle.onStop() }
                        runCatching { managed.lifecycle.onDispose() }
                    }
                }
            }
        }
    }

    return instance
}

@PublishedApi
internal data class AtomParamsKey(
    val instanceKey: Any?,
    val params: Any
)

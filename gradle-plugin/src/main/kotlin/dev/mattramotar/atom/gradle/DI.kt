package dev.mattramotar.atom.gradle

/**
 * Dependency injection framework integration options for Atom.
 */
enum class DI {
    /**
     * Metro DI framework integration. Generates Metro annotations for dependency injection. Requires
     * the Metro Gradle plugin to be applied.
     */
    METRO,

    /**
     * Manual dependency injection. No DI framework integration. Consumers manage Atom factories manually.
     */
    MANUAL;

    internal val value: String get() = name.lowercase()
}
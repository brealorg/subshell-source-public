package io.github.brealorg.subshell.data

/** Read by name from createFakeSubshellRepository in the main source set (ISSUE40). */
object DebugFixtureRepositoryProvider {
    @JvmStatic
    fun create(): SubshellRepository = FakeSubshellRepository()
}

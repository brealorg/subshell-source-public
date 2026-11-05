package io.github.brealorg.subshell.data

/**
 * ISSUE40_SAMPLE_DATA_IS_A_DEBUG_FIXTURE_V1: the sample posts and the fake repository live in the
 * debug source set (SampleData.kt, FakeSubshellRepository.kt), so the release app does not carry
 * them. Unit tests run against the debug variant and get the fake repository through this default;
 * the app itself always passes its persistent repository (rememberSubshellState), so a release
 * build never asks for one.
 */
internal fun createFakeSubshellRepository(): SubshellRepository {
    val provider = try {
        Class.forName(DEBUG_FIXTURE_REPOSITORY_PROVIDER)
    } catch (_: ClassNotFoundException) {
        error("The fixture repository exists only in debug builds; pass a repository.")
    }
    return provider.getMethod("create").invoke(null) as SubshellRepository
}

private const val DEBUG_FIXTURE_REPOSITORY_PROVIDER =
    "io.github.brealorg.subshell.data.DebugFixtureRepositoryProvider"

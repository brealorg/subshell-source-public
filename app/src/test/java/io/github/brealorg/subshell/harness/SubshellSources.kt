package io.github.brealorg.subshell.harness

/**
 * ISSUE41_ONE_SOURCE_ROOT_FOR_THE_TESTS_V1: the app's source package directory, in one place.
 * Source-as-text tests build their paths from it, so a package rename (#42) changes this line
 * (and tools/lib/source-paths.sh for the checkers) instead of every test's own path string.
 */
internal const val SUBSHELL_PACKAGE_DIR = "io/github/brealorg/subshell"

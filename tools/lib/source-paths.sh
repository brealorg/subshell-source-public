# ISSUE41_ONE_SOURCE_ROOT_FOR_THE_CHECKERS_V1: the app's source package directory, in one place.
# Checkers source this after ROOT is set and build every source path from it, so a package
# rename (#42) changes this line and the unit tests' SubshellSources object, not ~240 lines.
SUBSHELL_PKG_DIR="io/github/brealorg/subshell"

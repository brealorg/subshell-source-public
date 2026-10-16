package app.subshell.prototype.ui.screens

internal const val SUBSHELL_EGG01A_HIDDEN_TERMINAL_FOUNDATION_V1 = true
internal const val SUBSHELL_EGG01B_SUSPICIOUS_COMMAND_PACK_V1 = true
internal const val EASTER_EGG_UNLOCK_HOLD_MS = 1_900L
internal const val EASTER_EGG_MAX_SHELL_DEPTH = 5

internal data class EasterEggTerminalCommandResult(
    val output: List<String> = emptyList(),
    val clear: Boolean = false,
    val nextShellDepth: Int,
    val collapse: Boolean = false,
    val matrixMode: Boolean? = null,
)

internal fun easterEggPullStartsInUnlockZone(
    downY: Float,
    viewportHeight: Float,
    startZonePx: Float,
): Boolean = viewportHeight > 0f && downY >= viewportHeight - startZonePx

internal fun easterEggPullIsArmed(
    upwardPullPx: Float,
    thresholdPx: Float,
): Boolean = upwardPullPx >= thresholdPx

internal fun easterEggTerminalPrompt(shellDepth: Int): String =
    if (shellDepth <= 1) "subshell\$" else "subshell[$shellDepth]\$"

internal fun formatEasterEggUptime(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    val hours = safe / 3_600L
    val minutes = (safe % 3_600L) / 60L
    val remainder = safe % 60L
    return "%02d:%02d:%02d".format(hours, minutes, remainder)
}

private fun normalizedEasterEggCommand(command: String): String =
    command.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ").lowercase()

private fun easterEggEchoPayload(command: String): String =
    command.trim().substringAfter(' ', missingDelimiterValue = "")

private fun easterEggFortune(historySize: Int): String {
    val fortunes = listOf(
        "Every TODO eventually becomes architecture.",
        "The clean worktree is a temporary condition.",
        "If it works on the Pixel 6, document it before touching anything.",
        "There are two hard problems: cache invalidation, naming things, and lane integration.",
        "Never trust a command that says it only needs one tiny follow-up.",
    )
    return fortunes[historySize.mod(fortunes.size)]
}

internal fun executeEasterEggTerminalCommand(
    rawCommand: String,
    shellDepth: Int,
    versionName: String,
    uptimeSeconds: Long,
    history: List<String>,
): EasterEggTerminalCommandResult {
    val command = rawCommand.trim()
    val normalized = normalizedEasterEggCommand(command)
    val depth = shellDepth.coerceIn(1, EASTER_EGG_MAX_SHELL_DEPTH)

    if (command.isEmpty()) {
        return EasterEggTerminalCommandResult(nextShellDepth = depth)
    }

    return when {
        normalized == "help" -> EasterEggTerminalCommandResult(
            output = listOf(
                "commands: help clear whoami version uptime history exit",
                "some commands prefer not to be documented.",
            ),
            nextShellDepth = depth,
        )
        normalized == "help --all" || normalized == "help -a" -> EasterEggTerminalCommandResult(
            output = listOf("nice try."),
            nextShellDepth = depth,
        )
        normalized == "clear" -> EasterEggTerminalCommandResult(
            clear = true,
            nextShellDepth = depth,
        )
        normalized == "whoami" -> EasterEggTerminalCommandResult(
            output = listOf("shelluser"),
            nextShellDepth = depth,
        )
        normalized == "id" -> EasterEggTerminalCommandResult(
            output = listOf("uid=1000(shelluser) gid=1000(shelluser) groups=1000(shelluser),42(suspicious)"),
            nextShellDepth = depth,
        )
        normalized == "version" -> EasterEggTerminalCommandResult(
            output = listOf(versionName),
            nextShellDepth = depth,
        )
        normalized == "uptime" -> EasterEggTerminalCommandResult(
            output = listOf(formatEasterEggUptime(uptimeSeconds)),
            nextShellDepth = depth,
        )
        normalized == "history" -> EasterEggTerminalCommandResult(
            output = if (history.isEmpty()) {
                listOf("history is empty")
            } else {
                history.mapIndexed { index, entry -> "%3d  %s".format(index + 1, entry) }
            },
            nextShellDepth = depth,
        )
        normalized == "exit" && depth > 1 -> EasterEggTerminalCommandResult(
            output = listOf("logout"),
            nextShellDepth = depth - 1,
        )
        normalized == "exit" -> EasterEggTerminalCommandResult(
            output = listOf("There is no escape. Use the back button like a normal person."),
            nextShellDepth = depth,
        )
        normalized == "subshell" && depth < EASTER_EGG_MAX_SHELL_DEPTH ->
            EasterEggTerminalCommandResult(
                output = listOf("nested shell level ${depth + 1}"),
                nextShellDepth = depth + 1,
            )
        normalized == "subshell" -> EasterEggTerminalCommandResult(
            output = listOf("ENOSPC: too many shells"),
            nextShellDepth = depth,
            collapse = true,
        )

        normalized == "man subshell" || normalized == "man 1 subshell" -> EasterEggTerminalCommandResult(
            output = listOf(
                "SUBSHELL(1)               local mysteries               SUBSHELL(1)",
                "NAME",
                "    subshell - a terminal with questionable boundaries",
                "BUGS",
                "    yes",
                "SEE ALSO",
                "    sudo(8), coffee(1), matrix(1), snoo(1), touch-grass(1)",
            ),
            nextShellDepth = depth,
        )
        normalized == "man" -> EasterEggTerminalCommandResult(
            output = listOf("What manual page do you want? Try being suspicious."),
            nextShellDepth = depth,
        )
        normalized == "sudo" || normalized.startsWith("sudo ") -> EasterEggTerminalCommandResult(
            output = listOf(
                "shelluser is not in the sudoers file.",
                "This incident will be reported to absolutely nobody.",
            ),
            nextShellDepth = depth,
        )
        normalized == "please" || normalized.startsWith("please ") -> EasterEggTerminalCommandResult(
            output = listOf("Politeness noted. Privileges unchanged."),
            nextShellDepth = depth,
        )
        normalized == "rm" || normalized.startsWith("rm ") -> EasterEggTerminalCommandResult(
            output = listOf("rm: refusing to remove anything; reality is protected by plot armor"),
            nextShellDepth = depth,
        )
        normalized == "mkfs" || normalized.startsWith("mkfs ") ||
            normalized == "format" || normalized.startsWith("format ") ||
            normalized == "wipe" || normalized.startsWith("wipe ") ||
            normalized == "nuke" || normalized.startsWith("nuke ") -> EasterEggTerminalCommandResult(
            output = listOf("EPERM: destructive commands are disabled in this universe"),
            nextShellDepth = depth,
        )
        normalized == "reboot" || normalized == "shutdown" || normalized == "poweroff" ||
            normalized.startsWith("shutdown ") -> EasterEggTerminalCommandResult(
            output = listOf("operation not permitted: Android is watching"),
            nextShellDepth = depth,
        )
        normalized == "kill -9 1" || normalized == "kill -9 01" -> EasterEggTerminalCommandResult(
            output = listOf("kill: (1) - Operation not permitted", "PID 1 remains unimpressed."),
            nextShellDepth = depth,
        )
        normalized == "forkbomb" -> EasterEggTerminalCommandResult(
            output = listOf("EAGAIN: resource limit saved you from yourself"),
            nextShellDepth = depth,
        )

        normalized == "pacman" || normalized.startsWith("pacman ") -> EasterEggTerminalCommandResult(
            output = listOf(
                ":: Synchronizing package databases...",
                " there is nothing to do",
                " subshell is already emotionally up to date",
            ),
            nextShellDepth = depth,
        )
        normalized == "apt" || normalized.startsWith("apt ") ||
            normalized == "apt-get" || normalized.startsWith("apt-get ") -> EasterEggTerminalCommandResult(
            output = listOf("E: wrong distro. Nice try."),
            nextShellDepth = depth,
        )
        normalized == "uname" || normalized == "uname -a" -> EasterEggTerminalCommandResult(
            output = listOf("Linux subshell 6.6.6-egg01 #1 SMP PREEMPT_ANDROID suspicious/aarch64"),
            nextShellDepth = depth,
        )
        normalized == "pwd" -> EasterEggTerminalCommandResult(
            output = listOf("/data/local/tmp/subshell/why-are-you-here"),
            nextShellDepth = depth,
        )
        normalized == "ls" -> EasterEggTerminalCommandResult(
            output = listOf("README.DO_NOT_READ  snoo  definitely-not-a-terminal"),
            nextShellDepth = depth,
        )
        normalized == "ls -la" || normalized == "ls -al" -> EasterEggTerminalCommandResult(
            output = listOf(
                "drwx------ shelluser shelluser .",
                "drwx------ shelluser shelluser ..",
                "-rw------- shelluser shelluser .secrets",
                "-r--r--r-- root      root      README.DO_NOT_READ",
                "drwxr-xr-x snoo      snoo      snoo",
                "-rwx------ shelluser shelluser definitely-not-a-terminal",
            ),
            nextShellDepth = depth,
        )
        normalized == "cat readme.do_not_read" || normalized == "cat ./readme.do_not_read" -> EasterEggTerminalCommandResult(
            output = listOf("You were explicitly told not to read this."),
            nextShellDepth = depth,
        )
        normalized == "cat .secrets" || normalized == "cat ./.secrets" -> EasterEggTerminalCommandResult(
            output = listOf("nice try."),
            nextShellDepth = depth,
        )
        normalized == "cat /etc/passwd" -> EasterEggTerminalCommandResult(
            output = listOf(
                "root:x:0:0:definitely not you:/root:/bin/false",
                "shelluser:x:1000:1000:local suspect:/home/shelluser:/bin/subshell",
                "snoo:x:2001:2001:reddit-shaped process:/var/lib/snoo:/bin/false",
            ),
            nextShellDepth = depth,
        )
        normalized == "cat" || normalized.startsWith("cat ") -> EasterEggTerminalCommandResult(
            output = listOf("cat: no such interesting file"),
            nextShellDepth = depth,
        )
        normalized == "env" -> EasterEggTerminalCommandResult(
            output = listOf(
                "SHELL=/bin/subshell",
                "TERM=xterm-256color",
                "NETWORK=NO",
                "CHAOS=YES",
                "LANE=dev1",
            ),
            nextShellDepth = depth,
        )
        normalized == "ps" || normalized == "ps aux" -> EasterEggTerminalCommandResult(
            output = listOf(
                "PID   USER       COMMAND",
                "1     root       /init",
                "42    snoo       /system/bin/observe",
                "1337  shelluser  /bin/subshell",
            ),
            nextShellDepth = depth,
        )
        normalized == "top" -> EasterEggTerminalCommandResult(
            output = listOf(
                "Tasks: 3 total, 0 running, 3 suspicious",
                "CPU: 0% useful, 100% vibes",
                "MEM: enough",
                "PID  USER       %CPU  COMMAND",
                "1337 shelluser   0.0  subshell",
            ),
            nextShellDepth = depth,
        )
        normalized == "neofetch" -> EasterEggTerminalCommandResult(
            output = listOf(
                "      ______",
                "  ___/ / __ \\",
                " / __  / /_/ /   shelluser@subshell",
                "/_/ /_/\\____/    OS: subshell",
                "                 Shell: /bin/subshell",
                "                 Network: deliberately absent",
            ),
            nextShellDepth = depth,
        )
        normalized == "echo" || normalized.startsWith("echo ") -> EasterEggTerminalCommandResult(
            output = listOf(easterEggEchoPayload(command)),
            nextShellDepth = depth,
        )
        normalized == "yes" -> EasterEggTerminalCommandResult(
            output = List(8) { "yes" } + "SIGPIPE: terminal has standards.",
            nextShellDepth = depth,
        )

        normalized == "coffee" -> EasterEggTerminalCommandResult(
            output = listOf(
                "brewing...",
                "HTTP 418",
                "No coffee device found. Try the kitchen.",
            ),
            nextShellDepth = depth,
        )
        normalized == "fortune" -> EasterEggTerminalCommandResult(
            output = listOf(easterEggFortune(history.size)),
            nextShellDepth = depth,
        )
        normalized == "42" -> EasterEggTerminalCommandResult(
            output = listOf("The answer was easy. The question is still in another branch."),
            nextShellDepth = depth,
        )
        normalized == "reddit" -> EasterEggTerminalCommandResult(
            output = listOf("HTTP 429: touch grass before retrying"),
            nextShellDepth = depth,
        )
        normalized == "touch grass" || normalized == "touch-grass" -> EasterEggTerminalCommandResult(
            output = listOf("ENOENT: grass not found in current namespace. Try outside."),
            nextShellDepth = depth,
        )
        normalized == "snoo" -> EasterEggTerminalCommandResult(
            output = listOf(
                "   .-.",
                "  (o o)",
                "  | O |",
                "   \\_/",
                "snoo is watching the logs",
            ),
            nextShellDepth = depth,
        )
        normalized == "rickroll" -> EasterEggTerminalCommandResult(
            output = listOf("Network access is disabled. Hum the synth intro yourself."),
            nextShellDepth = depth,
        )
        normalized == "matrix" -> EasterEggTerminalCommandResult(
            output = listOf(
                "01110011 01110101 01100010 01110011 01101000 01100101 01101100 01101100",
                "visual channel acquired. type 'matrix off' to wake up.",
            ),
            nextShellDepth = depth,
            matrixMode = true,
        )
        normalized == "matrix off" -> EasterEggTerminalCommandResult(
            output = listOf("visual channel disconnected."),
            nextShellDepth = depth,
            matrixMode = false,
        )
        else -> EasterEggTerminalCommandResult(
            output = listOf("command not found: $command"),
            nextShellDepth = depth,
        )
    }
}

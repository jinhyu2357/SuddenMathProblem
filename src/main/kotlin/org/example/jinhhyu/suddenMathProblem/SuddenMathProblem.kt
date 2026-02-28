package org.example.jinhhyu.suddenMathProblem

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.ChatColor
import org.bukkit.Color
import org.bukkit.FireworkEffect
import org.bukkit.Sound
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.entity.Firework
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicReference

class SuddenMathProblem : JavaPlugin(), Listener {
    private val activeProblem = AtomicReference<MathProblem?>()
    private var randomProblemTask: BukkitTask? = null
    private var randomPausedByCommand = false

    override fun onEnable() {
        saveDefaultConfig()
        registerPaperCommands()
        server.pluginManager.registerEvents(this, this)
        startProblemTask()
    }

    override fun onDisable() {
        stopProblemTask()
        activeProblem.set(null)
        randomPausedByCommand = false
    }

    @EventHandler(ignoreCancelled = true)
    fun onChat(event: AsyncChatEvent) {
        val active = activeProblem.get() ?: return
        val message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim()
        val answer = message.toIntOrNull() ?: return
        if (answer != active.answer) {
            return
        }
        if (!activeProblem.compareAndSet(active, null)) {
            return
        }

        event.isCancelled = true
        val playerId = event.player.uniqueId
        val fallbackName = event.player.name
        server.scheduler.runTask(this, Runnable {
            val winner = server.getPlayer(playerId)
            val playerName = winner?.name ?: fallbackName
            val correctTemplate = config.getString(
                "messages.correct",
                "&a[Math] %player% answered correctly! (&f%answer%&a)"
            ) ?: "&a[Math] %player% answered correctly! (&f%answer%&a)"
            val correctMessage = colorize(
                correctTemplate
                    .replace("%player%", playerName)
                    .replace("%answer%", active.answer.toString())
            )
            server.broadcastMessage(correctMessage)
            if (winner != null) {
                playWinnerEffects(winner)
            }
            executeRewards(playerName, active.answer)
            if (active.source == ProblemSource.COMMAND) {
                resumeRandomProblemsAfterCommand()
            }
        })
    }

    private fun registerPaperCommands() {
        registerCommand(
            "mathproblem",
            "Generate a math problem immediately.",
            object : BasicCommand {
                override fun execute(commandSourceStack: CommandSourceStack, args: Array<String>) {
                    handleMathProblemCommand(commandSourceStack.sender)
                }
            }
        )
        registerCommand(
            "mathproblemreload",
            "Reload SuddenMathProblem config.",
            object : BasicCommand {
                override fun execute(commandSourceStack: CommandSourceStack, args: Array<String>) {
                    handleMathProblemReloadCommand(commandSourceStack.sender)
                }
            }
        )
        registerCommand(
            "mathpass",
            "Pass the current math problem.",
            object : BasicCommand {
                override fun execute(commandSourceStack: CommandSourceStack, args: Array<String>) {
                    handleMathPassCommand(commandSourceStack.sender)
                }
            }
        )
    }

    private fun handleMathProblemCommand(sender: CommandSender) {
        if (!hasOperatorAccess(sender)) {
            return
        }
        pauseRandomProblemsForCommand()
        postProblem(ProblemSource.COMMAND)
    }

    private fun handleMathProblemReloadCommand(sender: CommandSender) {
        if (!hasOperatorAccess(sender)) {
            return
        }
        reloadPluginConfig()

        val reloadTemplate = config.getString(
            "messages.reload",
            "&a[Math] Configuration reloaded."
        ) ?: "&a[Math] Configuration reloaded."
        sender.sendMessage(colorize(reloadTemplate))
    }

    private fun handleMathPassCommand(sender: CommandSender) {
        if (!hasOperatorAccess(sender)) {
            return
        }
        passActiveProblem(sender)
    }

    private fun hasOperatorAccess(sender: CommandSender): Boolean {
        if (sender is ConsoleCommandSender || sender.isOp) {
            return true
        }

        val noPermissionTemplate = config.getString(
            "messages.no-permission",
            "&c[Math] You must be an operator to use this command."
        ) ?: "&c[Math] You must be an operator to use this command."
        sender.sendMessage(colorize(noPermissionTemplate))
        return false
    }

    private fun passActiveProblem(sender: CommandSender) {
        val active = activeProblem.getAndSet(null)
        if (active == null) {
            val noneTemplate = config.getString(
                "messages.no-active-problem",
                "&c[Math] There is no active problem to pass."
            ) ?: "&c[Math] There is no active problem to pass."
            sender.sendMessage(colorize(noneTemplate))
            if (randomPausedByCommand) {
                resumeRandomProblemsAfterCommand()
            }
            return
        }

        val passTemplate = config.getString(
            "messages.passed",
            "&e[Math] %player% passed the current problem."
        ) ?: "&e[Math] %player% passed the current problem."
        val passedMessage = colorize(passTemplate.replace("%player%", sender.name))
        server.broadcastMessage(passedMessage)

        if (active.source == ProblemSource.COMMAND) {
            resumeRandomProblemsAfterCommand()
        }
    }

    private fun reloadPluginConfig() {
        reloadConfig()
        stopProblemTask()
        if (!randomPausedByCommand) {
            startProblemTask()
        }
    }

    private fun startProblemTask() {
        if (randomProblemTask != null) {
            return
        }
        val intervalSeconds = config.getLong("math.interval-seconds", 60L).coerceAtLeast(1L)
        val intervalTicks = intervalSeconds * 20L
        randomProblemTask = server.scheduler.runTaskTimer(
            this,
            Runnable { postRandomProblem() },
            intervalTicks,
            intervalTicks
        )
    }

    private fun stopProblemTask() {
        randomProblemTask?.cancel()
        randomProblemTask = null
    }

    private fun pauseRandomProblemsForCommand() {
        randomPausedByCommand = true
        stopProblemTask()
    }

    private fun resumeRandomProblemsAfterCommand() {
        if (!randomPausedByCommand) {
            return
        }
        randomPausedByCommand = false
        startProblemTask()
    }

    private fun postRandomProblem() {
        postProblem(ProblemSource.RANDOM)
    }

    private fun postProblem(source: ProblemSource) {
        val maxNumber = config.getInt("math.max-number", 10).coerceIn(1, 1_000_000)
        val operators = configuredOperators()
        val problem = generateProblem(maxNumber, operators, source)
        activeProblem.set(problem)

        val template = config.getString("messages.problem", "&e[Math] Solve: &b%problem%") ?: "&e[Math] Solve: &b%problem%"
        val message = colorize(template.replace("%problem%", problem.expression))
        server.broadcastMessage(message)
    }

    private fun configuredOperators(): List<String> {
        val supported = setOf("+", "-", "*")
        val configured = config.getStringList("math.operators")
            .map { it.trim() }
            .filter { it in supported }
        return if (configured.isEmpty()) listOf("+", "-", "*") else configured
    }

    private fun generateProblem(maxNumber: Int, operators: List<String>, source: ProblemSource): MathProblem {
        val random = ThreadLocalRandom.current()
        val operator = operators[random.nextInt(operators.size)]
        var left = random.nextInt(1, maxNumber + 1)
        var right = random.nextInt(1, maxNumber + 1)

        return when (operator) {
            "+" -> MathProblem("$left + $right", left + right, source)
            "-" -> {
                if (left < right) {
                    val temp = left
                    left = right
                    right = temp
                }
                MathProblem("$left - $right", left - right, source)
            }
            "*" -> MathProblem("$left x $right", left * right, source)
            else -> MathProblem("$left + $right", left + right, source)
        }
    }

    private fun executeRewards(playerName: String, answer: Int) {
        val commands = config.getStringList("rewards.commands")
        for (raw in commands) {
            val command = raw
                .replace("%player%", playerName)
                .replace("%answer%", answer.toString())
                .trim()
            if (command.isNotEmpty()) {
                server.dispatchCommand(server.consoleSender, command)
            }
        }
    }

    private fun playWinnerEffects(player: Player) {
        if (config.getBoolean("effects.sound-enabled", true)) {
            player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f)
        }

        if (config.getBoolean("effects.firework-enabled", true)) {
            val firework = player.world.spawn(player.location.clone().add(0.0, 1.0, 0.0), Firework::class.java)
            val fireworkMeta = firework.fireworkMeta
            fireworkMeta.clearEffects()
            fireworkMeta.addEffect(
                FireworkEffect.builder()
                    .with(FireworkEffect.Type.BALL_LARGE)
                    .withColor(Color.RED, Color.ORANGE, Color.YELLOW)
                    .flicker(true)
                    .trail(true)
                    .build()
            )
            fireworkMeta.power = 1
            firework.fireworkMeta = fireworkMeta

            server.scheduler.runTaskLater(this, Runnable {
                if (firework.isValid && !firework.isDead) {
                    firework.detonate()
                }
            }, 8L)
        }
    }

    private fun colorize(text: String): String {
        return ChatColor.translateAlternateColorCodes('&', text)
    }

    data class MathProblem(val expression: String, val answer: Int, val source: ProblemSource)

    enum class ProblemSource {
        RANDOM,
        COMMAND
    }
}

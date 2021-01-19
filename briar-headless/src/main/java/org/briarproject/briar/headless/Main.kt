package org.briarproject.briar.headless

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.counted
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import org.briarproject.bramble.BrambleCoreEagerSingletons
import org.briarproject.briar.BriarCoreEagerSingletons
import org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY
import org.spongycastle.util.encoders.Base64.toBase64String
import java.io.File
import java.io.File.separator
import java.io.IOException
import java.lang.System.getProperty
import java.lang.System.setProperty
import java.nio.file.Files.setPosixFilePermissions
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE
import java.nio.file.attribute.PosixFilePermission.OWNER_READ
import java.nio.file.attribute.PosixFilePermission.OWNER_WRITE
import java.security.SecureRandom
import java.util.logging.Level.ALL
import java.util.logging.Level.INFO
import java.util.logging.Level.WARNING
import java.util.logging.LogManager

private const val DEFAULT_PORT = 7000
private val DEFAULT_DATA_DIR = getProperty("user.home") + separator + ".briar"

private class Main : CliktCommand(
    name = "briar-headless",
    help = "A Briar peer without GUI that exposes a REST and Websocket API"
) {
    private val debug by option("--debug", "-d", help = "Enable printing of debug messages").flag(
        default = false
    )
    private val verbosity by option(
        "--verbose",
        "-v",
        help = "Print verbose log messages"
    ).counted()
    private val port by option(
        "--port",
        help = "Bind the server to this port. Default: $DEFAULT_PORT",
        metavar = "PORT",
        envvar = "BRIAR_PORT"
    ).int().default(DEFAULT_PORT)
    private val dataDir by option(
        "--data-dir",
        help = "The directory where Briar will store its files. Default: $DEFAULT_DATA_DIR",
        metavar = "PATH",
        envvar = "BRIAR_DATA_DIR"
    ).default(DEFAULT_DATA_DIR)

    override fun run() {
        // logging
        val levelSlf4j = if (debug) "DEBUG" else when (verbosity) {
            0 -> "WARN"
            1 -> "INFO"
            else -> "DEBUG"
        }
        val level = if (debug) ALL else when (verbosity) {
            0 -> WARNING
            1 -> INFO
            else -> ALL
        }
        setProperty(DEFAULT_LOG_LEVEL_KEY, levelSlf4j)
        LogManager.getLogManager().getLogger("").level = level

        val dataDir = getDataDir()
        val app =
            DaggerBriarHeadlessApp.builder().headlessModule(HeadlessModule(dataDir)).build()
        // We need to load the eager singletons directly after making the
        // dependency graphs
        BrambleCoreEagerSingletons.Helper.injectEagerSingletons(app)
        BriarCoreEagerSingletons.Helper.injectEagerSingletons(app)
        HeadlessEagerSingletons.Helper.injectEagerSingletons(app)

        val authToken = getOrCreateAuthToken(dataDir, app.getSecureRandom())

        app.getRouter().start(authToken, port, debug)
    }

    private fun getDataDir(): File {
        val file = File(dataDir)
        if (!file.exists() && !file.mkdirs()) {
            throw IOException("Could not create directory: ${file.absolutePath}")
        } else if (!file.isDirectory) {
            throw IOException("Data dir is not a directory: ${file.absolutePath}")
        }
        val perms = HashSet<PosixFilePermission>()
        perms.add(OWNER_READ)
        perms.add(OWNER_WRITE)
        perms.add(OWNER_EXECUTE)
        setPosixFilePermissions(file.toPath(), perms)
        return file
    }

    private fun getOrCreateAuthToken(dataDir: File, secureRandom: SecureRandom): String {
        val tokenFile = File(dataDir, "auth_token")
        return if (tokenFile.isFile) {
            tokenFile.readText()
        } else {
            val authToken = createAuthToken(secureRandom)
            tokenFile.writeText(authToken)
            authToken
        }
    }

    private fun createAuthToken(secureRandom: SecureRandom): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return toBase64String(bytes)
    }

}

fun main(args: Array<String>) = Main().main(args)

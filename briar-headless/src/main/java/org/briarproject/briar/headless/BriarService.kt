package org.briarproject.briar.headless

import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.output.TermUi.echo
import com.github.ajalt.clikt.output.TermUi.prompt
import org.briarproject.bramble.api.account.AccountManager
import org.briarproject.bramble.api.crypto.PasswordStrengthEstimator
import org.briarproject.bramble.api.crypto.PasswordStrengthEstimator.QUITE_WEAK
import org.briarproject.bramble.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH
import org.briarproject.bramble.api.lifecycle.LifecycleManager
import java.lang.System.exit
import javax.annotation.concurrent.Immutable
import javax.inject.Inject
import javax.inject.Singleton

@Immutable
@Singleton
internal class BriarService
@Inject
constructor(
    private val accountManager: AccountManager,
    private val lifecycleManager: LifecycleManager,
    private val passwordStrengthEstimator: PasswordStrengthEstimator
) {

    fun start() {
        if (!accountManager.accountExists()) {
            createAccount()
        } else {
            val password = prompt("Password", hideInput = true)
                ?: throw UsageError("Could not get password. Is STDIN connected?")
            if (!accountManager.signIn(password)) {
                echo("Error: Password invalid")
                exit(1)
            }
        }
        val dbKey = accountManager.databaseKey ?: throw AssertionError()
        lifecycleManager.startServices(dbKey)
    }

    fun stop() {
        lifecycleManager.stopServices()
        lifecycleManager.waitForShutdown()
    }

    private fun createAccount() {
        echo("No account found. Let's create one!\n\n")
        val nickname = prompt("Nickname") { nickname ->
            if (nickname.length > MAX_AUTHOR_NAME_LENGTH)
                throw UsageError("Please choose a shorter nickname!")
            nickname
        }
        val password =
            prompt("Password", hideInput = true, requireConfirmation = true) { password ->
                if (passwordStrengthEstimator.estimateStrength(password) < QUITE_WEAK)
                    throw UsageError("Please enter a stronger password!")
                password
            }
        if (nickname == null || password == null)
            throw UsageError("Could not get account information. Is STDIN connected?")
        accountManager.createAccount(nickname, password)
    }

}

package org.briarproject.briar.headless;

import org.briarproject.bramble.api.account.AccountManager;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;

import java.io.Console;
import java.util.Scanner;
import java.util.logging.Logger;

import javax.annotation.ParametersAreNonnullByDefault;
import javax.inject.Inject;

import static java.lang.System.console;
import static java.lang.System.err;
import static java.lang.System.exit;
import static java.lang.System.out;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;

@MethodsNotNullByDefault
@ParametersAreNonnullByDefault
public class BriarService {

	private final static Logger LOG = getLogger(BriarService.class.getName());

	private final AccountManager accountManager;
	private final LifecycleManager lifecycleManager;

	@Inject
	public BriarService(AccountManager accountManager,
			LifecycleManager lifecycleManager) {
		this.accountManager = accountManager;
		this.lifecycleManager = lifecycleManager;
	}

	public void start() {
		Console console = console();
		out.println("Welcome to Briar!\n");

		if (!accountManager.accountExists()) {
			if (console == null) {
				LOG.warning("No account found.");
				LOG.warning("Please start in terminal to set one up.");
				exit(1);
			}
			console.printf("No account found. Let's create one!\n\n");
			String nickname = createNickname(console);
			String password = createPassword(console);
			accountManager.createAccount(nickname, password);
		} else {
			out.print("Password: ");
			String password;
			if (console == null) {
				Scanner scanner = new Scanner(System.in);
				password = scanner.nextLine();
			} else {
				password = new String(console.readPassword());
			}
			if (!accountManager.signIn(password)) {
				err.println("Error: Password invalid");
				exit(1);
			}
		}
		assert accountManager.getDatabaseKey() != null;

		lifecycleManager.startServices(accountManager.getDatabaseKey());
	}

	private String createNickname(Console console) {
		String nickname;
		boolean error;
		do {
			nickname = console.readLine("Nickname: ");
			if (nickname.length() == 0) {
				console.printf("Please enter a nickname!\n");
				error = true;
			} else if (nickname.length() > MAX_AUTHOR_NAME_LENGTH) {
				console.printf("Please choose a shorter nickname!\n");
				error = true;
			} else {
				error = false;
			}
		} while (error);
		return nickname;
	}

	private String createPassword(Console console) {
		String password;
		boolean error;
		do {
			password = new String(console.readPassword("Password: "));
			if (password.length() < 4) {
				console.printf(
						"Please enter a password with at least 4 characters!\n");
				error = true;
				// TODO enforce stronger password
			} else {
				error = false;
			}
		} while (error);
		return password;
	}

}

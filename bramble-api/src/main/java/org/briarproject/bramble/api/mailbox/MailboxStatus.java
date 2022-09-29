package org.briarproject.bramble.api.mailbox;

import org.briarproject.nullsafety.NotNullByDefault;

import java.util.List;

import javax.annotation.concurrent.Immutable;

import static org.briarproject.bramble.api.mailbox.MailboxConstants.CLIENT_SUPPORTS;
import static org.briarproject.bramble.api.mailbox.MailboxConstants.PROBLEM_MS_SINCE_LAST_SUCCESS;
import static org.briarproject.bramble.api.mailbox.MailboxConstants.PROBLEM_NUM_CONNECTION_FAILURES;
import static org.briarproject.bramble.api.mailbox.MailboxHelper.getHighestCommonMajorVersion;

@Immutable
@NotNullByDefault
public class MailboxStatus {

	private final long lastAttempt, lastSuccess;
	private final int attemptsSinceSuccess;
	private final List<MailboxVersion> serverSupports;

	public MailboxStatus(long lastAttempt, long lastSuccess,
			int attemptsSinceSuccess,
			List<MailboxVersion> serverSupports) {
		this.lastAttempt = lastAttempt;
		this.lastSuccess = lastSuccess;
		this.attemptsSinceSuccess = attemptsSinceSuccess;
		this.serverSupports = serverSupports;
	}

	/**
	 * Returns the time of the last attempt to connect to the mailbox, in
	 * milliseconds since the Unix epoch, or -1 if no attempt has been made.
	 * <p>
	 * If an attempt is in progress and has not yet succeeded or failed then
	 * this method returns the time of the previous attempt, or -1 if the
	 * current attempt is the first.
	 */
	public long getTimeOfLastAttempt() {
		return lastAttempt;
	}

	/**
	 * Returns the time of the last successful attempt to connect to the
	 * mailbox, in milliseconds since the Unix epoch, or -1 if no attempt has
	 * succeeded.
	 * <p>
	 * If the last attempt was successful then this method returns the same
	 * value as {@link #getTimeOfLastAttempt()}. If an attempt is in progress
	 * and has not yet succeeded or failed then this method returns the time
	 * of the previous successful connection, or -1 if no attempt has
	 * succeeded.
	 */
	public long getTimeOfLastSuccess() {
		return lastSuccess;
	}

	/**
	 * Returns the number of attempts to connect to the mailbox that have
	 * failed since the last attempt succeeded, or the number of attempts that
	 * have been made, if no attempt has ever succeeded.
	 * <p>
	 * If an attempt is in progress and has not yet succeeded or failed then
	 * it is not included in this count.
	 */
	public int getAttemptsSinceSuccess() {
		return attemptsSinceSuccess;
	}

	/**
	 * Returns the mailbox's supported API versions.
	 */
	public List<MailboxVersion> getServerSupports() {
		return serverSupports;
	}

	/**
	 * @return true if this status indicates a problem with the mailbox.
	 */
	public boolean hasProblem(long now) {
		return attemptsSinceSuccess >= PROBLEM_NUM_CONNECTION_FAILURES &&
				(now - lastSuccess) >= PROBLEM_MS_SINCE_LAST_SUCCESS;
	}

	/**
	 * @return a positive integer if the mailbox is compatible. Same result as
	 * {@link MailboxHelper#getHighestCommonMajorVersion(List, List)}.
	 */
	public int getMailboxCompatibility() {
		return getHighestCommonMajorVersion(CLIENT_SUPPORTS, serverSupports);
	}

}

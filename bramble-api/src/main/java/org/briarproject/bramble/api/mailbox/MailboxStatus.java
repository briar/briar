package org.briarproject.bramble.api.mailbox;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class MailboxStatus {

	private final long lastAttempt, lastSuccess;
	private final int attemptsSinceSuccess;

	public MailboxStatus(long lastAttempt, long lastSuccess,
			int attemptsSinceSuccess) {
		this.lastAttempt = lastAttempt;
		this.lastSuccess = lastSuccess;
		this.attemptsSinceSuccess = attemptsSinceSuccess;
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
}

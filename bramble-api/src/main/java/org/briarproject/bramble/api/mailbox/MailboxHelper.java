package org.briarproject.bramble.api.mailbox;

import java.util.List;
import java.util.TreeSet;

import static org.briarproject.bramble.api.mailbox.MailboxConstants.API_CLIENT_TOO_OLD;
import static org.briarproject.bramble.api.mailbox.MailboxConstants.API_SERVER_TOO_OLD;

class MailboxHelper {

	/**
	 * Returns the highest major version that both client and server support
	 * or {@link MailboxConstants#API_SERVER_TOO_OLD} if the server is too old
	 * or {@link MailboxConstants#API_CLIENT_TOO_OLD} if the client is too old.
	 */
	static int getHighestCommonMajorVersion(
			List<MailboxVersion> client, List<MailboxVersion> server) {
		TreeSet<Integer> clientVersions = new TreeSet<>();
		for (MailboxVersion version : client) {
			clientVersions.add(version.getMajor());
		}
		TreeSet<Integer> serverVersions = new TreeSet<>();
		for (MailboxVersion version : server) {
			serverVersions.add(version.getMajor());
		}
		for (int clientVersion : clientVersions.descendingSet()) {
			if (serverVersions.contains(clientVersion)) return clientVersion;
		}
		if (clientVersions.last() < serverVersions.last()) {
			return API_CLIENT_TOO_OLD;
		}
		return API_SERVER_TOO_OLD;
	}

}

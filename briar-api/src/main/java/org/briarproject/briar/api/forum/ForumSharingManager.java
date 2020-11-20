package org.briarproject.briar.api.forum;

import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.briar.api.sharing.SharingManager;

public interface ForumSharingManager extends SharingManager<Forum> {

	/**
	 * The unique ID of the forum sharing client.
	 */
	ClientId CLIENT_ID = new ClientId("org.briarproject.briar.forum.sharing");

	/**
	 * The current major version of the forum sharing client.
	 */
	int MAJOR_VERSION = 0;

	/**
	 * The current minor version of the forum sharing client.
	 */
	int MINOR_VERSION = 1;
}

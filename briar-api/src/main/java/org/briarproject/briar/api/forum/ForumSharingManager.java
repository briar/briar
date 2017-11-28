package org.briarproject.briar.api.forum;

import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.briar.api.sharing.SharingManager;

public interface ForumSharingManager extends SharingManager<Forum> {

	/**
	 * The unique ID of the forum sharing client.
	 */
	ClientId CLIENT_ID = new ClientId("org.briarproject.briar.forum.sharing");

	/**
	 * The current version of the forum sharing client.
	 */
	int CLIENT_VERSION = 0;
}

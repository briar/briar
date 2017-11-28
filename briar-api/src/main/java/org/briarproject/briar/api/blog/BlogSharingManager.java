package org.briarproject.briar.api.blog;

import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.briar.api.sharing.SharingManager;

public interface BlogSharingManager extends SharingManager<Blog> {

	/**
	 * The unique ID of the blog sharing client.
	 */
	ClientId CLIENT_ID = new ClientId("org.briarproject.briar.blog.sharing");

	/**
	 * The current version of the blog sharing client.
	 */
	int CLIENT_VERSION = 0;
}

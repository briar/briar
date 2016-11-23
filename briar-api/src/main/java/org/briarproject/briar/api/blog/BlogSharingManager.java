package org.briarproject.briar.api.blog;

import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.briar.api.sharing.SharingManager;

public interface BlogSharingManager extends SharingManager<Blog> {

	ClientId CLIENT_ID = new ClientId("org.briarproject.briar.blog.sharing");

}

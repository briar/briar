package org.briarproject.api.blogs;

import org.briarproject.api.sharing.SharingManager;
import org.briarproject.api.sync.ClientId;

public interface BlogSharingManager extends SharingManager<Blog> {

	ClientId CLIENT_ID = new ClientId("org.briarproject.briar.blogs.sharing");

}

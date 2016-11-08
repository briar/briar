package org.briarproject.api.forum;

import org.briarproject.api.sharing.SharingManager;
import org.briarproject.api.sync.ClientId;

public interface ForumSharingManager extends SharingManager<Forum> {

	ClientId CLIENT_ID = new ClientId("org.briarproject.briar.forum.sharing");

}

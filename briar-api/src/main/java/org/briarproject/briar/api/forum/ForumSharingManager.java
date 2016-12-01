package org.briarproject.briar.api.forum;

import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.briar.api.sharing.SharingManager;

public interface ForumSharingManager extends SharingManager<Forum> {

	ClientId CLIENT_ID = new ClientId("org.briarproject.briar.forum.sharing");

}

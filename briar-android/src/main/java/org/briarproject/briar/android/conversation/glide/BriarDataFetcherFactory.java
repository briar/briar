package org.briarproject.briar.android.conversation.glide;

import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.android.conversation.AttachmentItem;
import org.briarproject.briar.api.messaging.MessagingManager;

import java.util.concurrent.Executor;

import javax.inject.Inject;

@NotNullByDefault
public class BriarDataFetcherFactory {

	private final MessagingManager messagingManager;
	@DatabaseExecutor
	private final Executor dbExecutor;

	@Inject
	public BriarDataFetcherFactory(MessagingManager messagingManager,
			@DatabaseExecutor Executor dbExecutor) {
		this.messagingManager = messagingManager;
		this.dbExecutor = dbExecutor;
	}

	BriarDataFetcher createBriarDataFetcher(AttachmentItem model) {
		return new BriarDataFetcher(messagingManager, dbExecutor, model);
	}

}

package org.briarproject.briar.android.conversation.glide;

import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.android.attachment.AttachmentItem;

import java.util.concurrent.Executor;

import javax.inject.Inject;

@NotNullByDefault
public class BriarDataFetcherFactory {

	private final ClientHelper clientHelper;
	@DatabaseExecutor
	private final Executor dbExecutor;

	@Inject
	public BriarDataFetcherFactory(ClientHelper clientHelper,
			@DatabaseExecutor Executor dbExecutor) {
		this.clientHelper = clientHelper;
		this.dbExecutor = dbExecutor;
	}

	BriarDataFetcher createBriarDataFetcher(AttachmentItem model) {
		return new BriarDataFetcher(clientHelper, dbExecutor, model);
	}

}

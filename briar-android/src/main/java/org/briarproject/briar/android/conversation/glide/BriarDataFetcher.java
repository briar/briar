package org.briarproject.briar.android.conversation.glide;

import android.support.annotation.Nullable;

import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.data.DataFetcher;

import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.android.conversation.AttachmentItem;
import org.briarproject.briar.api.messaging.MessagingManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static com.bumptech.glide.load.DataSource.LOCAL;
import static java.util.Objects.requireNonNull;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.LogUtils.logException;

@NotNullByDefault
class BriarDataFetcher implements DataFetcher<InputStream> {

	private final static Logger LOG =
			getLogger(BriarDataFetcher.class.getName());

	private final MessagingManager messagingManager;
	@DatabaseExecutor
	private final Executor dbExecutor;

	@Nullable
	private AttachmentItem attachment;
	@Nullable
	private volatile InputStream inputStream;

	@Inject
	public BriarDataFetcher(MessagingManager messagingManager,
			@DatabaseExecutor Executor dbExecutor) {
		this.messagingManager = messagingManager;
		this.dbExecutor = dbExecutor;
	}

	@Override
	public void loadData(Priority priority,
			DataCallback<? super InputStream> callback) {
		MessageId id = requireNonNull(attachment).getMessageId();
		dbExecutor.execute(() -> {
			try {
				inputStream = messagingManager.getAttachment(id).getStream();
				callback.onDataReady(inputStream);
			} catch (DbException e) {
				callback.onLoadFailed(e);
			}
		});
	}

	@Override
	public void cleanup() {
		final InputStream stream = inputStream;
		if (stream != null) {
			try {
				stream.close();
			} catch (IOException e) {
				logException(LOG, WARNING, e);
			}
		}
	}

	@Override
	public void cancel() {
		// does it make sense to cancel a database load?
	}

	@Override
	public Class<InputStream> getDataClass() {
		return InputStream.class;
	}

	@Override
	public DataSource getDataSource() {
		return LOCAL;
	}

	public void setAttachment(AttachmentItem attachment) {
		this.attachment = attachment;
	}

}

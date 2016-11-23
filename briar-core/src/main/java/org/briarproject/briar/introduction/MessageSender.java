package org.briarproject.briar.introduction;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.MetadataEncoder;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Metadata;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.briar.api.client.MessageQueueManager;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static org.briarproject.briar.api.introduction.IntroductionConstants.ACCEPT;
import static org.briarproject.briar.api.introduction.IntroductionConstants.E_PUBLIC_KEY;
import static org.briarproject.briar.api.introduction.IntroductionConstants.GROUP_ID;
import static org.briarproject.briar.api.introduction.IntroductionConstants.MAC;
import static org.briarproject.briar.api.introduction.IntroductionConstants.MESSAGE_TIME;
import static org.briarproject.briar.api.introduction.IntroductionConstants.MSG;
import static org.briarproject.briar.api.introduction.IntroductionConstants.NAME;
import static org.briarproject.briar.api.introduction.IntroductionConstants.PUBLIC_KEY;
import static org.briarproject.briar.api.introduction.IntroductionConstants.SESSION_ID;
import static org.briarproject.briar.api.introduction.IntroductionConstants.SIGNATURE;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TIME;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TRANSPORT;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TYPE;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TYPE_ABORT;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TYPE_ACK;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TYPE_REQUEST;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TYPE_RESPONSE;

@Immutable
@NotNullByDefault
class MessageSender {

	private final DatabaseComponent db;
	private final ClientHelper clientHelper;
	private final Clock clock;
	private final MetadataEncoder metadataEncoder;
	private final MessageQueueManager messageQueueManager;

	@Inject
	MessageSender(DatabaseComponent db, ClientHelper clientHelper, Clock clock,
			MetadataEncoder metadataEncoder,
			MessageQueueManager messageQueueManager) {

		this.db = db;
		this.clientHelper = clientHelper;
		this.clock = clock;
		this.metadataEncoder = metadataEncoder;
		this.messageQueueManager = messageQueueManager;
	}

	void sendMessage(Transaction txn, BdfDictionary message)
			throws DbException, FormatException {

		BdfList bdfList = encodeMessage(message);
		byte[] body = clientHelper.toByteArray(bdfList);
		GroupId groupId = new GroupId(message.getRaw(GROUP_ID));
		Group group = db.getGroup(txn, groupId);
		long timestamp = clock.currentTimeMillis();

		message.put(MESSAGE_TIME, timestamp);
		Metadata metadata = metadataEncoder.encode(message);

		messageQueueManager.sendMessage(txn, group, timestamp, body, metadata);
	}

	private BdfList encodeMessage(BdfDictionary d) throws FormatException {

		BdfList body;
		long type = d.getLong(TYPE);
		if (type == TYPE_REQUEST) {
			body = encodeRequest(d);
		} else if (type == TYPE_RESPONSE) {
			body = encodeResponse(d);
		} else if (type == TYPE_ACK) {
			body = encodeAck(d);
		} else if (type == TYPE_ABORT) {
			body = encodeAbort(d);
		} else {
			throw new FormatException();
		}
		return body;
	}

	private BdfList encodeRequest(BdfDictionary d) throws FormatException {
		BdfList list = BdfList.of(TYPE_REQUEST, d.getRaw(SESSION_ID),
				d.getString(NAME), d.getRaw(PUBLIC_KEY));

		if (d.containsKey(MSG)) {
			list.add(d.getString(MSG));
		}
		return list;
	}

	private BdfList encodeResponse(BdfDictionary d) throws FormatException {
		BdfList list = BdfList.of(TYPE_RESPONSE, d.getRaw(SESSION_ID),
				d.getBoolean(ACCEPT));

		if (d.getBoolean(ACCEPT)) {
			list.add(d.getLong(TIME));
			list.add(d.getRaw(E_PUBLIC_KEY));
			list.add(d.getDictionary(TRANSPORT));
		}
		return list;
	}

	private BdfList encodeAck(BdfDictionary d) throws FormatException {
		return BdfList.of(TYPE_ACK, d.getRaw(SESSION_ID), d.getRaw(MAC),
				d.getRaw(SIGNATURE));
	}

	private BdfList encodeAbort(BdfDictionary d) throws FormatException {
		return BdfList.of(TYPE_ABORT, d.getRaw(SESSION_ID));
	}

}

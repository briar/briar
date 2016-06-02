package org.briarproject.introduction;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.MessageQueueManager;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Metadata;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.system.Clock;

import javax.inject.Inject;

import static org.briarproject.api.clients.ReadableMessageConstants.TIMESTAMP;
import static org.briarproject.api.introduction.IntroductionConstants.ACCEPT;
import static org.briarproject.api.introduction.IntroductionConstants.E_PUBLIC_KEY;
import static org.briarproject.api.introduction.IntroductionConstants.GROUP_ID;
import static org.briarproject.api.introduction.IntroductionConstants.MSG;
import static org.briarproject.api.introduction.IntroductionConstants.NAME;
import static org.briarproject.api.introduction.IntroductionConstants.PUBLIC_KEY;
import static org.briarproject.api.introduction.IntroductionConstants.SESSION_ID;
import static org.briarproject.api.introduction.IntroductionConstants.TIME;
import static org.briarproject.api.introduction.IntroductionConstants.TRANSPORT;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE_ABORT;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE_ACK;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE_REQUEST;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE_RESPONSE;

public class MessageSender {

	final private DatabaseComponent db;
	final private ClientHelper clientHelper;
	final private Clock clock;
	final private MetadataEncoder metadataEncoder;
	final private MessageQueueManager messageQueueManager;

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

	public void sendMessage(Transaction txn, BdfDictionary message)
			throws DbException, FormatException {

		BdfList bdfList = encodeMessage(message);
		byte[] body = clientHelper.toByteArray(bdfList);
		GroupId groupId = new GroupId(message.getRaw(GROUP_ID));
		Group group = db.getGroup(txn, groupId);
		long timestamp = clock.currentTimeMillis();

		message.put(TIMESTAMP, timestamp);
		Metadata metadata = metadataEncoder.encode(message);

		messageQueueManager
				.sendMessage(txn, group, timestamp, body, metadata);
	}

	private BdfList encodeMessage(BdfDictionary d)
			throws FormatException {

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

	private BdfList encodeRequest(BdfDictionary d)
			throws FormatException {
		BdfList list = BdfList.of(TYPE_REQUEST, d.getRaw(SESSION_ID),
				d.getString(NAME), d.getRaw(PUBLIC_KEY));

		if (d.containsKey(MSG)) {
			list.add(d.getString(MSG));
		}
		return list;
	}

	private BdfList encodeResponse(BdfDictionary d)
			throws FormatException {
		BdfList list = BdfList.of(TYPE_RESPONSE, d.getRaw(SESSION_ID),
				d.getBoolean(ACCEPT));

		if (d.getBoolean(ACCEPT)) {
			list.add(d.getLong(TIME));
			list.add(d.getRaw(E_PUBLIC_KEY));
			list.add(d.getDictionary(TRANSPORT));
		}
		// TODO Sign the response, see #256
		return list;
	}

	private BdfList encodeAck(BdfDictionary d) throws FormatException {
		return BdfList.of(TYPE_ACK, d.getRaw(SESSION_ID));
	}

	private BdfList encodeAbort(BdfDictionary d) throws FormatException {
		return BdfList.of(TYPE_ABORT, d.getRaw(SESSION_ID));
	}

}

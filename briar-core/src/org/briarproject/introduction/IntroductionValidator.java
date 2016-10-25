package org.briarproject.introduction;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.BdfMessageContext;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.SessionId;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.Message;
import org.briarproject.api.system.Clock;
import org.briarproject.clients.BdfMessageValidator;

import static org.briarproject.api.TransportId.MAX_TRANSPORT_ID_LENGTH;
import static org.briarproject.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;
import static org.briarproject.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.api.identity.AuthorConstants.MAX_SIGNATURE_LENGTH;
import static org.briarproject.api.introduction.IntroductionConstants.ACCEPT;
import static org.briarproject.api.introduction.IntroductionConstants.E_PUBLIC_KEY;
import static org.briarproject.api.introduction.IntroductionConstants.GROUP_ID;
import static org.briarproject.api.introduction.IntroductionConstants.MAC;
import static org.briarproject.api.introduction.IntroductionConstants.MAC_LENGTH;
import static org.briarproject.api.introduction.IntroductionConstants.MAX_INTRODUCTION_MESSAGE_LENGTH;
import static org.briarproject.api.introduction.IntroductionConstants.MESSAGE_ID;
import static org.briarproject.api.introduction.IntroductionConstants.MESSAGE_TIME;
import static org.briarproject.api.introduction.IntroductionConstants.MSG;
import static org.briarproject.api.introduction.IntroductionConstants.NAME;
import static org.briarproject.api.introduction.IntroductionConstants.PUBLIC_KEY;
import static org.briarproject.api.introduction.IntroductionConstants.SESSION_ID;
import static org.briarproject.api.introduction.IntroductionConstants.SIGNATURE;
import static org.briarproject.api.introduction.IntroductionConstants.TIME;
import static org.briarproject.api.introduction.IntroductionConstants.TRANSPORT;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE_ABORT;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE_ACK;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE_REQUEST;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE_RESPONSE;
import static org.briarproject.api.properties.TransportPropertyConstants.MAX_PROPERTIES_PER_TRANSPORT;
import static org.briarproject.api.properties.TransportPropertyConstants.MAX_PROPERTY_LENGTH;

class IntroductionValidator extends BdfMessageValidator {

	IntroductionValidator(ClientHelper clientHelper,
			MetadataEncoder metadataEncoder, Clock clock) {
		super(clientHelper, metadataEncoder, clock);
	}

	@Override
	protected BdfMessageContext validateMessage(Message m, Group g, BdfList body)
			throws FormatException {

		BdfDictionary d;
		long type = body.getLong(0);
		byte[] id = body.getRaw(1);
		checkLength(id, SessionId.LENGTH);

		if (type == TYPE_REQUEST) {
			d = validateRequest(body);
		} else if (type == TYPE_RESPONSE) {
			d = validateResponse(body);
		} else if (type == TYPE_ACK) {
			d = validateAck(body);
		} else if (type == TYPE_ABORT) {
			d = validateAbort(body);
		} else {
			throw new FormatException();
		}

		d.put(TYPE, type);
		d.put(SESSION_ID, id);
		d.put(GROUP_ID, m.getGroupId());
		d.put(MESSAGE_ID, m.getId());
		d.put(MESSAGE_TIME, m.getTimestamp());
		return new BdfMessageContext(d);
	}

	private BdfDictionary validateRequest(BdfList message)
			throws FormatException {

		checkSize(message, 4, 5);

		// parse contact name
		String name = message.getString(2);
		checkLength(name, 1, MAX_AUTHOR_NAME_LENGTH);

		// parse contact's public key
		byte[] key = message.getRaw(3);
		checkLength(key, 0, MAX_PUBLIC_KEY_LENGTH);

		// parse (optional) message
		String msg = null;
		if (message.size() == 5) {
			msg = message.getString(4);
			checkLength(msg, 0, MAX_INTRODUCTION_MESSAGE_LENGTH);
		}

		// Return the metadata
		BdfDictionary d = new BdfDictionary();
		d.put(NAME, name);
		d.put(PUBLIC_KEY, key);
		if (msg != null) {
			d.put(MSG, msg);
		}
		return d;
	}

	private BdfDictionary validateResponse(BdfList message)
			throws FormatException {

		checkSize(message, 3, 6);

		// parse accept/decline
		boolean accept = message.getBoolean(2);

		long time = 0;
		byte[] pubkey = null;
		BdfDictionary tp = new BdfDictionary();
		if (accept) {
			checkSize(message, 6);

			// parse timestamp
			time = message.getLong(3);

			// parse ephemeral public key
			pubkey = message.getRaw(4);
			checkLength(pubkey, 0, MAX_PUBLIC_KEY_LENGTH);

			// parse transport properties
			tp = message.getDictionary(5);
			if (tp.size() < 1) throw new FormatException();
			for (String tId : tp.keySet()) {
				checkLength(tId, 1, MAX_TRANSPORT_ID_LENGTH);
				BdfDictionary tProps = tp.getDictionary(tId);
				checkSize(tProps, 0, MAX_PROPERTIES_PER_TRANSPORT);
				for (String propId : tProps.keySet()) {
					checkLength(propId, 0, MAX_PROPERTY_LENGTH);
					String prop = tProps.getString(propId);
					checkLength(prop, 0, MAX_PROPERTY_LENGTH);
				}
			}
		} else {
			checkSize(message, 3);
		}

		// Return the metadata
		BdfDictionary d = new BdfDictionary();
		d.put(ACCEPT, accept);
		if (accept) {
			d.put(TIME, time);
			d.put(E_PUBLIC_KEY, pubkey);
			d.put(TRANSPORT, tp);
		}
		return d;
	}

	private BdfDictionary validateAck(BdfList message) throws FormatException {
		checkSize(message, 4);

		byte[] mac = message.getRaw(2);
		checkLength(mac, 1, MAC_LENGTH);

		byte[] sig = message.getRaw(3);
		checkLength(sig, 1, MAX_SIGNATURE_LENGTH);

		// Return the metadata
		BdfDictionary d = new BdfDictionary();
		d.put(MAC, mac);
		d.put(SIGNATURE, sig);
		return d;
	}

	private BdfDictionary validateAbort(BdfList message)
			throws FormatException {

		checkSize(message, 2);

		// Return the metadata
		return new BdfDictionary();
	}
}

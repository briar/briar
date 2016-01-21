package org.briarproject.introduction;

import org.briarproject.api.FormatException;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfList;

import static org.briarproject.api.introduction.IntroductionConstants.ACCEPT;
import static org.briarproject.api.introduction.IntroductionConstants.DEVICE_ID;
import static org.briarproject.api.introduction.IntroductionConstants.E_PUBLIC_KEY;
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

public class MessageEncoder {

	public static BdfList encodeMessage(BdfDictionary d) throws FormatException {

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

	private static BdfList encodeRequest(BdfDictionary d) throws FormatException {
		BdfList list = BdfList.of(TYPE_REQUEST, d.getRaw(SESSION_ID),
				d.getString(NAME), d.getRaw(PUBLIC_KEY));

		if (d.containsKey(MSG)) {
			list.add(d.getString(MSG));
		}
		return list;
	}

	private static BdfList encodeResponse(BdfDictionary d) throws FormatException {
		BdfList list = BdfList.of(TYPE_RESPONSE, d.getRaw(SESSION_ID),
				d.getBoolean(ACCEPT));

		if (d.getBoolean(ACCEPT)) {
			list.add(d.getLong(TIME));
			list.add(d.getRaw(E_PUBLIC_KEY));
			list.add(d.getRaw(DEVICE_ID));
			list.add(d.getDictionary(TRANSPORT));
		}
		// TODO Sign the response, see #256
		return list;
	}

	private static BdfList encodeAck(BdfDictionary d) throws FormatException {
		return BdfList.of(TYPE_ACK, d.getRaw(SESSION_ID));
	}

	private static BdfList encodeAbort(BdfDictionary d) throws FormatException {
		return BdfList.of(TYPE_ABORT, d.getRaw(SESSION_ID));
	}

}

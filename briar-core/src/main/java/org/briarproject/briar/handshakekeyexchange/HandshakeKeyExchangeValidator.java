package org.briarproject.briar.handshakekeyexchange;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.BdfMessageContext;
import org.briarproject.bramble.api.client.BdfMessageValidator;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfEntry;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.MetadataEncoder;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.InvalidMessageException;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.system.Clock;
import static org.briarproject.briar.handshakekeyexchange.HandshakeKeyExchangeConstants.MSG_KEY_LOCAL;

import static org.briarproject.bramble.util.ValidationUtils.checkSize;

import javax.inject.Inject;

public class HandshakeKeyExchangeValidator extends BdfMessageValidator {

	@Inject
	protected HandshakeKeyExchangeValidator(
			ClientHelper clientHelper,
			MetadataEncoder metadataEncoder,
			Clock clock) {
		super(clientHelper, metadataEncoder, clock);
	}

	@Override
	protected BdfMessageContext validateMessage(Message m, Group g,
			BdfList body) throws InvalidMessageException, FormatException {
		checkSize(body,1);
	    BdfDictionary meta = BdfDictionary.of(
	       new BdfEntry(MSG_KEY_LOCAL, false)
	    );
		return new BdfMessageContext(meta);
	}
}

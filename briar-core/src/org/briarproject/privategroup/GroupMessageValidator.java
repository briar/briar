package org.briarproject.privategroup;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.BdfMessageContext;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.identity.AuthorFactory;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.InvalidMessageException;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.system.Clock;
import org.briarproject.clients.BdfMessageValidator;

import java.util.Collection;
import java.util.Collections;

class GroupMessageValidator extends BdfMessageValidator {

	private final CryptoComponent crypto;
	private final AuthorFactory authorFactory;

	GroupMessageValidator(CryptoComponent crypto, AuthorFactory authorFactory,
			ClientHelper clientHelper, MetadataEncoder metadataEncoder,
			Clock clock) {
		super(clientHelper, metadataEncoder, clock);
		this.crypto = crypto;
		this.authorFactory = authorFactory;
	}

	@Override
	protected BdfMessageContext validateMessage(Message m, Group g,
			BdfList body) throws InvalidMessageException, FormatException {

		BdfDictionary meta = new BdfDictionary();
		Collection<MessageId> dependencies = Collections.emptyList();
		return new BdfMessageContext(meta, dependencies);
	}

}

package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.BdfMessageContext;
import org.briarproject.bramble.api.client.BdfMessageValidator;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.MetadataEncoder;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.InvalidMessageException;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

import static org.briarproject.bramble.api.mailbox.MailboxUpdateManager.MSG_KEY_LOCAL;
import static org.briarproject.bramble.api.mailbox.MailboxUpdateManager.MSG_KEY_VERSION;
import static org.briarproject.bramble.util.ValidationUtils.checkSize;

@Immutable
@NotNullByDefault
class MailboxUpdateValidator extends BdfMessageValidator {

	MailboxUpdateValidator(ClientHelper clientHelper,
			MetadataEncoder metadataEncoder, Clock clock) {
		super(clientHelper, metadataEncoder, clock);
	}

	@Override
	protected BdfMessageContext validateMessage(Message m, Group g,
			BdfList body) throws InvalidMessageException, FormatException {
		// Version, Properties, clientSupports, serverSupports
		checkSize(body, 4);
		// Version
		long version = body.getLong(0);
		if (version < 0) throw new FormatException();
		// clientSupports
		BdfList clientSupports = body.getList(1);
		// serverSupports
		BdfList serverSupports = body.getList(2);
		// Properties
		BdfDictionary dictionary = body.getDictionary(3);
		clientHelper.parseAndValidateMailboxUpdate(clientSupports,
				serverSupports, dictionary);
		// Return the metadata
		BdfDictionary meta = new BdfDictionary();
		meta.put(MSG_KEY_VERSION, version);
		meta.put(MSG_KEY_LOCAL, false);
		return new BdfMessageContext(meta);
	}

}

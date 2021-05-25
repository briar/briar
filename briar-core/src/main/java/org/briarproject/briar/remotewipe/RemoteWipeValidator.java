package org.briarproject.briar.remotewipe;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.BdfMessageContext;
import org.briarproject.bramble.api.client.BdfMessageValidator;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfEntry;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.MetadataEncoder;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.system.Clock;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static org.briarproject.bramble.util.ValidationUtils.checkSize;
import static org.briarproject.briar.api.remotewipe.MessageType.SETUP;
import static org.briarproject.briar.api.remotewipe.MessageType.WIPE;
import static org.briarproject.briar.socialbackup.SocialBackupConstants.MSG_KEY_LOCAL;
import static org.briarproject.briar.socialbackup.SocialBackupConstants.MSG_KEY_MESSAGE_TYPE;

@Immutable
@NotNullByDefault
class RemoteWipeValidator extends BdfMessageValidator {

	@Inject
	RemoteWipeValidator(ClientHelper clientHelper,
			MetadataEncoder metadataEncoder, Clock clock) {
		super(clientHelper, metadataEncoder, clock);
	}

	@Override
	protected BdfMessageContext validateMessage(Message m, Group g,
			BdfList body) throws FormatException {
		org.briarproject.briar.api.remotewipe.MessageType
				type = org.briarproject.briar.api.remotewipe.MessageType
				.fromValue(body.getLong(0).intValue());
		if (type == SETUP) return validateSetupMessage(body);
		else if (type == WIPE) return validateWipeMessage(body);
		else throw new AssertionError();
	}

	private BdfMessageContext validateSetupMessage(BdfList body)
			throws FormatException {
		checkSize(body, 1);
		BdfDictionary meta = BdfDictionary.of(
				new BdfEntry(MSG_KEY_MESSAGE_TYPE, SETUP.getValue()),
				new BdfEntry(MSG_KEY_LOCAL, false));
		return new BdfMessageContext(meta);
	}

	private BdfMessageContext validateWipeMessage(BdfList body)
			throws FormatException {
		checkSize(body, 1);
		BdfDictionary meta = BdfDictionary.of(
				new BdfEntry(MSG_KEY_MESSAGE_TYPE, WIPE.getValue()),
				new BdfEntry(MSG_KEY_LOCAL, false));
		return new BdfMessageContext(meta);
	}
}

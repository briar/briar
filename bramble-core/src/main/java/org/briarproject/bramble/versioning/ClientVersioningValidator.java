package org.briarproject.bramble.versioning;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.BdfMessageContext;
import org.briarproject.bramble.api.client.BdfMessageValidator;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.MetadataEncoder;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.system.Clock;

import javax.annotation.concurrent.Immutable;

import static org.briarproject.bramble.api.sync.ClientId.MAX_CLIENT_ID_LENGTH;
import static org.briarproject.bramble.util.ValidationUtils.checkLength;
import static org.briarproject.bramble.util.ValidationUtils.checkSize;
import static org.briarproject.bramble.versioning.ClientVersioningConstants.MSG_KEY_LOCAL;
import static org.briarproject.bramble.versioning.ClientVersioningConstants.MSG_KEY_UPDATE_VERSION;

@Immutable
@NotNullByDefault
class ClientVersioningValidator extends BdfMessageValidator {

	ClientVersioningValidator(ClientHelper clientHelper,
			MetadataEncoder metadataEncoder, Clock clock) {
		super(clientHelper, metadataEncoder, clock);
	}

	@Override
	protected BdfMessageContext validateMessage(Message m, Group g,
			BdfList body) throws FormatException {
		// Client states, update version
		checkSize(body, 2);
		// Client states
		BdfList states = body.getList(0);
		int size = states.size();
		for (int i = 0; i < size; i++) {
			BdfList clientState = states.getList(i);
			// Client ID, major version, minor version, active
			checkSize(clientState, 4);
			String clientId = clientState.getString(0);
			checkLength(clientId, 1, MAX_CLIENT_ID_LENGTH);
			int majorVersion = clientState.getLong(1).intValue();
			if (majorVersion < 0) throw new FormatException();
			int minorVersion = clientState.getLong(2).intValue();
			if (minorVersion < 0) throw new FormatException();
			clientState.getBoolean(3);
		}
		// Update version
		long updateVersion = body.getLong(1);
		if (updateVersion < 0) throw new FormatException();
		// Return the metadata
		BdfDictionary meta = new BdfDictionary();
		meta.put(MSG_KEY_UPDATE_VERSION, updateVersion);
		meta.put(MSG_KEY_LOCAL, false);
		return new BdfMessageContext(meta);
	}
}

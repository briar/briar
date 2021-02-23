package org.briarproject.briar.socialbackup;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.socialbackup.BackupMetadata;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static org.briarproject.briar.socialbackup.SocialBackupConstants.GROUP_KEY_CUSTODIANS;
import static org.briarproject.briar.socialbackup.SocialBackupConstants.GROUP_KEY_SECRET;
import static org.briarproject.briar.socialbackup.SocialBackupConstants.GROUP_KEY_THRESHOLD;
import static org.briarproject.briar.socialbackup.SocialBackupConstants.GROUP_KEY_VERSION;

@Immutable
@NotNullByDefault
class BackupMetadataParserImpl implements BackupMetadataParser {

	private final ClientHelper clientHelper;

	@Inject
	BackupMetadataParserImpl(ClientHelper clientHelper) {
		this.clientHelper = clientHelper;
	}

	@Nullable
	@Override
	public BackupMetadata parseBackupMetadata(BdfDictionary meta)
			throws FormatException {
		if (meta.isEmpty()) return null;
		SecretKey secret = new SecretKey(meta.getRaw(GROUP_KEY_SECRET));
		BdfList bdfCustodians = meta.getList(GROUP_KEY_CUSTODIANS);
		List<Author> custodians = new ArrayList<>(bdfCustodians.size());
		for (int i = 0; i < bdfCustodians.size(); i++) {
			BdfList author = bdfCustodians.getList(i);
			custodians.add(clientHelper.parseAndValidateAuthor(author));
		}
		int threshold = meta.getLong(GROUP_KEY_THRESHOLD).intValue();
		int version = meta.getLong(GROUP_KEY_VERSION).intValue();
		return new BackupMetadata(secret, custodians, threshold, version);
	}
}

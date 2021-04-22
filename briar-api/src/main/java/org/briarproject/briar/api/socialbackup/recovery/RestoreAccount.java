package org.briarproject.briar.api.socialbackup.recovery;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.briar.api.socialbackup.ReturnShardPayload;
import org.briarproject.briar.api.socialbackup.SocialBackup;

import java.security.GeneralSecurityException;

public interface RestoreAccount {

	int getNumberOfShards();

	boolean addReturnShardPayload(ReturnShardPayload toAdd);

	boolean canRecover();

	int recover() throws FormatException, GeneralSecurityException;

	SocialBackup getSocialBackup();

	void addContactsToDb() throws InterruptedException, DbException;
}

package org.briarproject.briar.api.socialbackup;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;

import java.io.IOException;

@NotNullByDefault
public interface SocialBackupExchangeManager {

	/**
	 * Return a shard and encrypted backup to a secret-owner's new device
	 * @param conn
	 * @param masterKey
	 * @param verified
	 * @throws IOException
	 * @throws DbException
	 */
	public void sendReturnShard(DuplexTransportConnection conn,
			SecretKey masterKey,
			boolean verified) throws IOException, DbException;

	/**
	 * Receive a returned shard and encrypted backup from a custodian
	 * @param conn
	 * @param masterKey
	 * @param verified
	 * @return the shard and encrypted backup
	 * @throws IOException
	 * @throws DbException
	 */
	public ReturnShardPayload receiveReturnShard(DuplexTransportConnection conn,
			SecretKey masterKey, boolean verified)
			throws IOException, DbException;
}

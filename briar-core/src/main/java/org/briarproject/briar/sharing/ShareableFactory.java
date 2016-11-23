package org.briarproject.briar.sharing;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.briar.api.sharing.Shareable;
import org.briarproject.briar.api.sharing.SharingMessage;

@NotNullByDefault
interface ShareableFactory<S extends Shareable, I extends SharingMessage.Invitation, IS extends InviteeSessionState, SS extends SharerSessionState> {

	BdfList encode(S sh);

	S get(Transaction txn, GroupId groupId) throws DbException;

	S parse(BdfList shareable) throws FormatException;

	S parse(I msg);

	S parse(IS state);

	S parse(SS state);
}

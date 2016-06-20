package org.briarproject.sharing;

import org.briarproject.api.FormatException;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.sharing.Shareable;
import org.briarproject.api.sharing.SharingMessage;
import org.briarproject.api.sync.GroupId;

interface ShareableFactory<S extends Shareable, I extends SharingMessage.Invitation, IS extends InviteeSessionState, SS extends SharerSessionState> {

	BdfList encode(S sh);

	S get(Transaction txn, GroupId groupId) throws DbException;

	S parse(BdfList shareable) throws FormatException;

	S parse(I msg);

	S parse(IS state);

	S parse(SS state);
}

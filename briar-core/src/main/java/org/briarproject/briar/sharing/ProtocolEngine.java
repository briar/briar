package org.briarproject.briar.sharing;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.sharing.Shareable;

import javax.annotation.Nullable;

@NotNullByDefault
interface ProtocolEngine<S extends Shareable> {

	Session onInviteAction(Transaction txn, Session session,
			@Nullable String message, long timestamp) throws DbException;

	Session onAcceptAction(Transaction txn, Session session) throws DbException;

	Session onDeclineAction(Transaction txn, Session session)
			throws DbException;

	Session onLeaveAction(Transaction txn, Session session) throws DbException;

	Session onInviteMessage(Transaction txn, Session session,
			InviteMessage<S> m) throws DbException, FormatException;

	Session onAcceptMessage(Transaction txn, Session session, AcceptMessage m)
			throws DbException, FormatException;

	Session onDeclineMessage(Transaction txn, Session session, DeclineMessage m)
			throws DbException, FormatException;

	Session onLeaveMessage(Transaction txn, Session session, LeaveMessage m)
			throws DbException, FormatException;

	Session onAbortMessage(Transaction txn, Session session, AbortMessage m)
			throws DbException, FormatException;

}

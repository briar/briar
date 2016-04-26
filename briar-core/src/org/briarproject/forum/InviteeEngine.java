package org.briarproject.forum;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ProtocolEngine;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfEntry;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.ForumInvitationReceivedEvent;
import org.briarproject.api.forum.Forum;
import org.briarproject.api.forum.InviteeAction;
import org.briarproject.api.forum.InviteeProtocolState;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.api.forum.ForumConstants.CONTACT_ID;
import static org.briarproject.api.forum.ForumConstants.FORUM_NAME;
import static org.briarproject.api.forum.ForumConstants.FORUM_SALT;
import static org.briarproject.api.forum.ForumConstants.GROUP_ID;
import static org.briarproject.api.forum.ForumConstants.SESSION_ID;
import static org.briarproject.api.forum.ForumConstants.SHARE_MSG_TYPE_ABORT;
import static org.briarproject.api.forum.ForumConstants.SHARE_MSG_TYPE_ACCEPT;
import static org.briarproject.api.forum.ForumConstants.SHARE_MSG_TYPE_DECLINE;
import static org.briarproject.api.forum.ForumConstants.SHARE_MSG_TYPE_INVITATION;
import static org.briarproject.api.forum.ForumConstants.SHARE_MSG_TYPE_LEAVE;
import static org.briarproject.api.forum.ForumConstants.STATE;
import static org.briarproject.api.forum.ForumConstants.TASK;
import static org.briarproject.api.forum.ForumConstants.TASK_ADD_FORUM_TO_LIST_SHARED_WITH_US;
import static org.briarproject.api.forum.ForumConstants.TASK_ADD_SHARED_FORUM;
import static org.briarproject.api.forum.ForumConstants.TASK_REMOVE_FORUM_FROM_LIST_SHARED_WITH_US;
import static org.briarproject.api.forum.ForumConstants.TASK_UNSHARE_FORUM_SHARED_WITH_US;
import static org.briarproject.api.forum.ForumConstants.TYPE;
import static org.briarproject.api.forum.InviteeAction.LOCAL_ABORT;
import static org.briarproject.api.forum.InviteeAction.LOCAL_ACCEPT;
import static org.briarproject.api.forum.InviteeAction.LOCAL_DECLINE;
import static org.briarproject.api.forum.InviteeAction.LOCAL_LEAVE;
import static org.briarproject.api.forum.InviteeAction.REMOTE_INVITATION;
import static org.briarproject.api.forum.InviteeAction.REMOTE_LEAVE;
import static org.briarproject.api.forum.InviteeProtocolState.ERROR;
import static org.briarproject.api.forum.InviteeProtocolState.FINISHED;
import static org.briarproject.api.forum.InviteeProtocolState.LEFT;

public class InviteeEngine
		implements ProtocolEngine<BdfDictionary, BdfDictionary, BdfDictionary> {

	private static final Logger LOG =
			Logger.getLogger(SharerEngine.class.getName());

	@Override
	public StateUpdate<BdfDictionary, BdfDictionary> onLocalAction(
			BdfDictionary localState, BdfDictionary localAction) {

		try {
			InviteeProtocolState currentState =
					getState(localState.getLong(STATE));
			long type = localAction.getLong(TYPE);
			InviteeAction action = InviteeAction.getLocal(type);
			InviteeProtocolState nextState = currentState.next(action);
			localState.put(STATE, nextState.getValue());

			if (action == LOCAL_ABORT && currentState != ERROR) {
				return abortSession(currentState, localState);
			}

			if (nextState == ERROR) {
				if (LOG.isLoggable(WARNING)) {
					LOG.warning("Error: Invalid action in state " +
							currentState.name());
				}
				return noUpdate(localState, true);
			}
			List<BdfDictionary> messages;
			List<Event> events = Collections.emptyList();

			if (action == LOCAL_ACCEPT || action == LOCAL_DECLINE) {
				BdfDictionary msg = BdfDictionary.of(
						new BdfEntry(SESSION_ID, localState.getRaw(SESSION_ID)),
						new BdfEntry(GROUP_ID, localState.getRaw(GROUP_ID))
				);
				if (action == LOCAL_ACCEPT) {
					localState.put(TASK, TASK_ADD_SHARED_FORUM);
					msg.put(TYPE, SHARE_MSG_TYPE_ACCEPT);
				} else {
					localState.put(TASK,
							TASK_REMOVE_FORUM_FROM_LIST_SHARED_WITH_US);
					msg.put(TYPE, SHARE_MSG_TYPE_DECLINE);
				}
				messages = Collections.singletonList(msg);
				logLocalAction(currentState, localState, msg);
			}
			else if (action == LOCAL_LEAVE) {
				BdfDictionary msg = new BdfDictionary();
				msg.put(TYPE, SHARE_MSG_TYPE_LEAVE);
				msg.put(SESSION_ID, localState.getRaw(SESSION_ID));
				msg.put(GROUP_ID, localState.getRaw(GROUP_ID));
				messages = Collections.singletonList(msg);
				logLocalAction(currentState, localState, msg);
			}
			else {
				throw new IllegalArgumentException("Unknown Local Action");
			}
			return new StateUpdate<BdfDictionary, BdfDictionary>(false,
					false, localState, messages, events);
		} catch (FormatException e) {
			throw new IllegalArgumentException(e);
		}
	}

	@Override
	public StateUpdate<BdfDictionary, BdfDictionary> onMessageReceived(
			BdfDictionary localState, BdfDictionary msg) {

		try {
			InviteeProtocolState currentState =
					getState(localState.getLong(STATE));
			long type = msg.getLong(TYPE);
			InviteeAction action = InviteeAction.getRemote(type);
			InviteeProtocolState nextState = currentState.next(action);
			localState.put(STATE, nextState.getValue());

			logMessageReceived(currentState, nextState, type, msg);

			if (nextState == ERROR) {
				if (currentState != ERROR) {
					return abortSession(currentState, localState);
				} else {
					return noUpdate(localState, true);
				}
			}

			List<BdfDictionary> messages = Collections.emptyList();
			List<Event> events = Collections.emptyList();
			boolean deleteMsg = false;

			if (currentState == LEFT) {
				// ignore and delete messages coming in while in that state
				deleteMsg = true;
			}
			// the sharer left the forum she had shared with us
			else if (action == REMOTE_LEAVE && currentState == FINISHED) {
				localState.put(TASK, TASK_UNSHARE_FORUM_SHARED_WITH_US);
			}
			else if (currentState == FINISHED) {
				// ignore and delete messages coming in while in that state
				// note that LEAVE is possible, but was handled above
				deleteMsg = true;
			}
			// the sharer left the forum before we couldn't even respond
			else if (action == REMOTE_LEAVE) {
				localState.put(TASK, TASK_REMOVE_FORUM_FROM_LIST_SHARED_WITH_US);
			}
			// we have just received our invitation
			else if (action == REMOTE_INVITATION) {
				localState.put(TASK, TASK_ADD_FORUM_TO_LIST_SHARED_WITH_US);
				// TODO how to get the proper group here?
				Forum forum = new Forum(null, localState.getString(FORUM_NAME),
						localState.getRaw(FORUM_SALT));
				ContactId contactId = new ContactId(
						localState.getLong(CONTACT_ID).intValue());
				Event event = new ForumInvitationReceivedEvent(forum, contactId);
				events = Collections.singletonList(event);
			}
			else {
				throw new IllegalArgumentException("Bad state");
			}
			return new StateUpdate<BdfDictionary, BdfDictionary>(deleteMsg,
					false, localState, messages, events);
		} catch (FormatException e) {
			throw new IllegalArgumentException(e);
		}
	}

	private void logLocalAction(InviteeProtocolState state,
			BdfDictionary localState, BdfDictionary msg) {

		if (!LOG.isLoggable(INFO)) return;

		String a = "response";
		if (msg.getLong(TYPE, -1L) == SHARE_MSG_TYPE_LEAVE) a = "leave";

		try {
			LOG.info("Sending " + a + " in state " + state.name() +
					" with session ID " +
					Arrays.hashCode(msg.getRaw(SESSION_ID)) + " in group " +
					Arrays.hashCode(msg.getRaw(GROUP_ID)) + ". " +
					"Moving on to state " +
					getState(localState.getLong(STATE)).name()
			);
		} catch (FormatException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	private void logMessageReceived(InviteeProtocolState currentState,
			InviteeProtocolState nextState, long type, BdfDictionary msg) {
		if (!LOG.isLoggable(INFO)) return;

		try {
			String t = "unknown";
			if (type == SHARE_MSG_TYPE_INVITATION) t = "INVITE";
			else if (type == SHARE_MSG_TYPE_LEAVE) t = "LEAVE";
			else if (type == SHARE_MSG_TYPE_ABORT) t = "ABORT";

			LOG.info("Received " + t + " in state " + currentState.name() +
					" with session ID " +
					Arrays.hashCode(msg.getRaw(SESSION_ID)) + " in group " +
					Arrays.hashCode(msg.getRaw(GROUP_ID)) + ". " +
					"Moving on to state " + nextState.name()
			);
		} catch (FormatException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	@Override
	public StateUpdate<BdfDictionary, BdfDictionary> onMessageDelivered(
			BdfDictionary localState, BdfDictionary delivered) {
		try {
			return noUpdate(localState, false);
		} catch (FormatException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return null;
		}
	}

	private InviteeProtocolState getState(Long state) {
		return InviteeProtocolState.fromValue(state.intValue());
	}

	private StateUpdate<BdfDictionary, BdfDictionary> abortSession(
			InviteeProtocolState currentState, BdfDictionary localState)
			throws FormatException {

		if (LOG.isLoggable(WARNING)) {
			LOG.warning("Aborting protocol session " +
					Arrays.hashCode(localState.getRaw(SESSION_ID)) +
					" in state " + currentState.name());
		}

		localState.put(STATE, ERROR.getValue());
		BdfDictionary msg = new BdfDictionary();
		msg.put(TYPE, SHARE_MSG_TYPE_ABORT);
		msg.put(SESSION_ID, localState.getRaw(SESSION_ID));
		msg.put(GROUP_ID, localState.getRaw(GROUP_ID));
		List<BdfDictionary> messages = Collections.singletonList(msg);

		List<Event> events = Collections.emptyList();

		return new StateUpdate<BdfDictionary, BdfDictionary>(false, false,
				localState, messages, events);
	}

	private StateUpdate<BdfDictionary, BdfDictionary> noUpdate(
			BdfDictionary localState, boolean delete) throws FormatException {

		return new StateUpdate<BdfDictionary, BdfDictionary>(delete, false,
				localState, Collections.<BdfDictionary>emptyList(),
				Collections.<Event>emptyList());
	}
}

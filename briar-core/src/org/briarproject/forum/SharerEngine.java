package org.briarproject.forum;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ProtocolEngine;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.ForumInvitationResponseReceivedEvent;
import org.briarproject.api.forum.SharerAction;
import org.briarproject.api.forum.SharerProtocolState;

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
import static org.briarproject.api.forum.ForumConstants.INVITATION_MSG;
import static org.briarproject.api.forum.ForumConstants.SESSION_ID;
import static org.briarproject.api.forum.ForumConstants.SHARE_MSG_TYPE_ABORT;
import static org.briarproject.api.forum.ForumConstants.SHARE_MSG_TYPE_ACCEPT;
import static org.briarproject.api.forum.ForumConstants.SHARE_MSG_TYPE_DECLINE;
import static org.briarproject.api.forum.ForumConstants.SHARE_MSG_TYPE_INVITATION;
import static org.briarproject.api.forum.ForumConstants.SHARE_MSG_TYPE_LEAVE;
import static org.briarproject.api.forum.ForumConstants.STATE;
import static org.briarproject.api.forum.ForumConstants.TASK;
import static org.briarproject.api.forum.ForumConstants.TASK_ADD_FORUM_TO_LIST_TO_BE_SHARED_BY_US;
import static org.briarproject.api.forum.ForumConstants.TASK_REMOVE_FORUM_FROM_LIST_TO_BE_SHARED_BY_US;
import static org.briarproject.api.forum.ForumConstants.TASK_SHARE_FORUM;
import static org.briarproject.api.forum.ForumConstants.TASK_UNSHARE_FORUM_SHARED_BY_US;
import static org.briarproject.api.forum.ForumConstants.TYPE;
import static org.briarproject.api.forum.SharerAction.LOCAL_ABORT;
import static org.briarproject.api.forum.SharerAction.LOCAL_INVITATION;
import static org.briarproject.api.forum.SharerAction.LOCAL_LEAVE;
import static org.briarproject.api.forum.SharerAction.REMOTE_ACCEPT;
import static org.briarproject.api.forum.SharerAction.REMOTE_DECLINE;
import static org.briarproject.api.forum.SharerAction.REMOTE_LEAVE;
import static org.briarproject.api.forum.SharerProtocolState.ERROR;
import static org.briarproject.api.forum.SharerProtocolState.FINISHED;
import static org.briarproject.api.forum.SharerProtocolState.LEFT;

public class SharerEngine
		implements ProtocolEngine<BdfDictionary, BdfDictionary, BdfDictionary> {

	private static final Logger LOG =
			Logger.getLogger(SharerEngine.class.getName());

	@Override
	public StateUpdate<BdfDictionary, BdfDictionary> onLocalAction(
			BdfDictionary localState, BdfDictionary localAction) {

		try {
			SharerProtocolState currentState =
					getState(localState.getLong(STATE));
			long type = localAction.getLong(TYPE);
			SharerAction action = SharerAction.getLocal(type);
			SharerProtocolState nextState = currentState.next(action);
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

			if (action == LOCAL_INVITATION) {
				BdfDictionary msg = new BdfDictionary();
				msg.put(TYPE, SHARE_MSG_TYPE_INVITATION);
				msg.put(SESSION_ID, localState.getRaw(SESSION_ID));
				msg.put(GROUP_ID, localState.getRaw(GROUP_ID));
				msg.put(FORUM_NAME, localState.getString(FORUM_NAME));
				msg.put(FORUM_SALT, localState.getRaw(FORUM_SALT));
				if (localAction.containsKey(INVITATION_MSG)) {
					msg.put(INVITATION_MSG,
							localAction.getString(INVITATION_MSG));
				}
				messages = Collections.singletonList(msg);
				logLocalAction(currentState, localState, msg);

				// remember that we offered to share this forum
				localState.put(TASK, TASK_ADD_FORUM_TO_LIST_TO_BE_SHARED_BY_US);
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
			SharerProtocolState currentState =
					getState(localState.getLong(STATE));
			long type = msg.getLong(TYPE);
			SharerAction action = SharerAction.getRemote(type);
			SharerProtocolState nextState = currentState.next(action);
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
			else if (action == REMOTE_LEAVE) {
				localState.put(TASK, TASK_UNSHARE_FORUM_SHARED_BY_US);
			}
			else if (currentState == FINISHED) {
				// ignore and delete messages coming in while in that state
				// note that LEAVE is possible, but was handled above
				deleteMsg = true;
			}
			// we have sent our invitation and just got a response
			else if (action == REMOTE_ACCEPT || action == REMOTE_DECLINE) {
				if (action == REMOTE_ACCEPT) {
					localState.put(TASK, TASK_SHARE_FORUM);
				} else {
					// this ensures that the forum can be shared again
					localState.put(TASK,
							TASK_REMOVE_FORUM_FROM_LIST_TO_BE_SHARED_BY_US);
				}
				String name = localState.getString(FORUM_NAME);
				ContactId c = new ContactId(
						localState.getLong(CONTACT_ID).intValue());
				Event event = new ForumInvitationResponseReceivedEvent(name, c);
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

	private void logLocalAction(SharerProtocolState state,
			BdfDictionary localState, BdfDictionary msg) {

		if (!LOG.isLoggable(INFO)) return;

		String a = "invitation";
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

	private void logMessageReceived(SharerProtocolState currentState,
			SharerProtocolState nextState, long type, BdfDictionary msg) {
		if (!LOG.isLoggable(INFO)) return;

		try {
			String t = "unknown";
			if (type == SHARE_MSG_TYPE_ACCEPT) t = "ACCEPT";
			else if (type == SHARE_MSG_TYPE_DECLINE) t = "DECLINE";
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

	private SharerProtocolState getState(Long state) {
		 return SharerProtocolState.fromValue(state.intValue());
	}

	private StateUpdate<BdfDictionary, BdfDictionary> abortSession(
			SharerProtocolState currentState, BdfDictionary localState)
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

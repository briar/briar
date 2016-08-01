package org.briarproject.sharing;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ProtocolEngine;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.InvitationReceivedEvent;

import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.api.sharing.SharingConstants.SHARE_MSG_TYPE_ABORT;
import static org.briarproject.api.sharing.SharingConstants.SHARE_MSG_TYPE_ACCEPT;
import static org.briarproject.api.sharing.SharingConstants.SHARE_MSG_TYPE_DECLINE;
import static org.briarproject.api.sharing.SharingConstants.SHARE_MSG_TYPE_INVITATION;
import static org.briarproject.api.sharing.SharingConstants.SHARE_MSG_TYPE_LEAVE;
import static org.briarproject.api.sharing.SharingConstants.TASK_ADD_SHAREABLE_TO_LIST_SHARED_WITH_US;
import static org.briarproject.api.sharing.SharingConstants.TASK_ADD_SHARED_SHAREABLE;
import static org.briarproject.api.sharing.SharingConstants.TASK_REMOVE_SHAREABLE_FROM_LIST_SHARED_WITH_US;
import static org.briarproject.api.sharing.SharingConstants.TASK_UNSHARE_SHAREABLE_SHARED_WITH_US;
import static org.briarproject.api.sharing.SharingMessage.BaseMessage;
import static org.briarproject.api.sharing.SharingMessage.SimpleMessage;

class InviteeEngine<IS extends InviteeSessionState, IR extends InvitationReceivedEvent>
		implements ProtocolEngine<InviteeSessionState.Action, IS, BaseMessage> {

	private static final Logger LOG =
			Logger.getLogger(InviteeEngine.class.getName());

	private final InvitationReceivedEventFactory<IS, IR> invitationReceivedEventFactory;

	InviteeEngine(InvitationReceivedEventFactory<IS, IR> invitationReceivedEventFactory) {
		this.invitationReceivedEventFactory = invitationReceivedEventFactory;
	}

	@Override
	public StateUpdate<IS, BaseMessage> onLocalAction(
			IS localState, InviteeSessionState.Action action) {

		try {
			InviteeSessionState.State currentState = localState.getState();
			InviteeSessionState.State nextState = currentState.next(action);
			localState.setState(nextState);

			if (action == InviteeSessionState.Action.LOCAL_ABORT && currentState != InviteeSessionState.State.ERROR) {
				return abortSession(currentState, localState);
			}

			if (nextState == InviteeSessionState.State.ERROR) {
				if (LOG.isLoggable(WARNING)) {
					LOG.warning("Error: Invalid action in state " +
							currentState.name());
				}
				return noUpdate(localState, true);
			}
			List<BaseMessage> messages;
			List<Event> events = Collections.emptyList();

			if (action == InviteeSessionState.Action.LOCAL_ACCEPT || action == InviteeSessionState.Action.LOCAL_DECLINE) {
				BaseMessage msg;
				if (action == InviteeSessionState.Action.LOCAL_ACCEPT) {
					localState.setTask(TASK_ADD_SHARED_SHAREABLE);
					msg = new SimpleMessage(SHARE_MSG_TYPE_ACCEPT,
							localState.getGroupId(), localState.getSessionId());
				} else {
					localState.setTask(
							TASK_REMOVE_SHAREABLE_FROM_LIST_SHARED_WITH_US);
					msg = new SimpleMessage(SHARE_MSG_TYPE_DECLINE,
							localState.getGroupId(), localState.getSessionId());
				}
				messages = Collections.singletonList(msg);
				logLocalAction(currentState, localState, msg);
			}
			else if (action == InviteeSessionState.Action.LOCAL_LEAVE) {
				BaseMessage msg = new SimpleMessage(SHARE_MSG_TYPE_LEAVE,
						localState.getGroupId(), localState.getSessionId());
				messages = Collections.singletonList(msg);
				logLocalAction(currentState, localState, msg);
			}
			else {
				throw new IllegalArgumentException("Unknown Local Action");
			}
			return new StateUpdate<IS, BaseMessage>(false,
					false, localState, messages, events);
		} catch (FormatException e) {
			throw new IllegalArgumentException(e);
		}
	}

	@Override
	public StateUpdate<IS, BaseMessage> onMessageReceived(
			IS localState, BaseMessage msg) {

		try {
			InviteeSessionState.State currentState = localState.getState();
			InviteeSessionState.Action action = InviteeSessionState.Action.getRemote(msg.getType());
			InviteeSessionState.State nextState = currentState.next(action);
			localState.setState(nextState);

			logMessageReceived(currentState, nextState, msg.getType(), msg);

			if (nextState == InviteeSessionState.State.ERROR) {
				if (currentState != InviteeSessionState.State.ERROR) {
					return abortSession(currentState, localState);
				} else {
					return noUpdate(localState, true);
				}
			}

			List<BaseMessage> messages = Collections.emptyList();
			List<Event> events = Collections.emptyList();
			boolean deleteMsg = false;

			if (currentState == InviteeSessionState.State.LEFT) {
				// ignore and delete messages coming in while in that state
				deleteMsg = true;
			}
			// the sharer left the forum she had shared with us
			else if (action == InviteeSessionState.Action.REMOTE_LEAVE && currentState == InviteeSessionState.State.FINISHED) {
				localState.setTask(TASK_UNSHARE_SHAREABLE_SHARED_WITH_US);
			}
			else if (currentState == InviteeSessionState.State.FINISHED) {
				// ignore and delete messages coming in while in that state
				// note that LEAVE is possible, but was handled above
				deleteMsg = true;
			}
			// the sharer left the forum before we couldn't even respond
			else if (action == InviteeSessionState.Action.REMOTE_LEAVE) {
				localState.setTask(TASK_REMOVE_SHAREABLE_FROM_LIST_SHARED_WITH_US);
			}
			// we have just received our invitation
			else if (action == InviteeSessionState.Action.REMOTE_INVITATION) {
				localState.setTask(TASK_ADD_SHAREABLE_TO_LIST_SHARED_WITH_US);
				Event event = invitationReceivedEventFactory.build(localState);
				events = Collections.singletonList(event);
			}
			else {
				throw new IllegalArgumentException("Bad state");
			}
			return new StateUpdate<IS, BaseMessage>(deleteMsg,
					false, localState, messages, events);
		} catch (FormatException e) {
			throw new IllegalArgumentException(e);
		}
	}

	private void logLocalAction(InviteeSessionState.State state,
			InviteeSessionState localState, BaseMessage msg) {

		if (!LOG.isLoggable(INFO)) return;

		String a = "response";
		if (msg.getType() == SHARE_MSG_TYPE_LEAVE) a = "leave";

		LOG.info("Sending " + a + " in state " + state.name() +
				" with session ID " +
				msg.getSessionId().hashCode() + " in group " +
				msg.getGroupId().hashCode() + ". " +
				"Moving on to state " + localState.getState().name()
		);
	}

	private void logMessageReceived(InviteeSessionState.State currentState, InviteeSessionState.State nextState,
			long type, BaseMessage msg) {

		if (!LOG.isLoggable(INFO)) return;

		String t = "unknown";
		if (type == SHARE_MSG_TYPE_INVITATION) t = "INVITE";
		else if (type == SHARE_MSG_TYPE_LEAVE) t = "LEAVE";
		else if (type == SHARE_MSG_TYPE_ABORT) t = "ABORT";

		LOG.info("Received " + t + " in state " + currentState.name() +
				" with session ID " +
				msg.getSessionId().hashCode() + " in group " +
				msg.getGroupId().hashCode() + ". " +
				"Moving on to state " + nextState.name()
		);
	}

	@Override
	public StateUpdate<IS, BaseMessage> onMessageDelivered(
			IS localState, BaseMessage delivered) {
		try {
			return noUpdate(localState, false);
		} catch (FormatException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return null;
		}
	}

	private StateUpdate<IS, BaseMessage> abortSession(
			InviteeSessionState.State currentState, IS localState)
			throws FormatException {

		if (LOG.isLoggable(WARNING)) {
			LOG.warning("Aborting protocol session " +
					localState.getSessionId().hashCode() +
					" in state " + currentState.name());
		}
		localState.setState(InviteeSessionState.State.ERROR);
		BaseMessage msg =
				new SimpleMessage(SHARE_MSG_TYPE_ABORT, localState.getGroupId(),
						localState.getSessionId());
		List<BaseMessage> messages = Collections.singletonList(msg);

		List<Event> events = Collections.emptyList();

		return new StateUpdate<IS, BaseMessage>(false, false,
				localState, messages, events);
	}

	private StateUpdate<IS, BaseMessage> noUpdate(
			IS localState, boolean delete) throws FormatException {

		return new StateUpdate<IS, BaseMessage>(delete, false,
				localState, Collections.<BaseMessage>emptyList(),
				Collections.<Event>emptyList());
	}
}

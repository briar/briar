package org.briarproject.briar.sharing;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.briar.api.client.ProtocolEngine;
import org.briarproject.briar.api.sharing.event.InvitationResponseReceivedEvent;

import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import javax.annotation.concurrent.Immutable;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.briar.api.sharing.SharingConstants.SHARE_MSG_TYPE_ABORT;
import static org.briarproject.briar.api.sharing.SharingConstants.SHARE_MSG_TYPE_ACCEPT;
import static org.briarproject.briar.api.sharing.SharingConstants.SHARE_MSG_TYPE_DECLINE;
import static org.briarproject.briar.api.sharing.SharingConstants.SHARE_MSG_TYPE_LEAVE;
import static org.briarproject.briar.api.sharing.SharingConstants.TASK_ADD_SHAREABLE_TO_LIST_TO_BE_SHARED_BY_US;
import static org.briarproject.briar.api.sharing.SharingConstants.TASK_REMOVE_SHAREABLE_FROM_LIST_TO_BE_SHARED_BY_US;
import static org.briarproject.briar.api.sharing.SharingConstants.TASK_SHARE_SHAREABLE;
import static org.briarproject.briar.api.sharing.SharingConstants.TASK_UNSHARE_SHAREABLE_SHARED_BY_US;
import static org.briarproject.briar.api.sharing.SharingMessage.BaseMessage;
import static org.briarproject.briar.api.sharing.SharingMessage.Invitation;
import static org.briarproject.briar.api.sharing.SharingMessage.SimpleMessage;
import static org.briarproject.briar.sharing.SharerSessionState.Action.REMOTE_ACCEPT;
import static org.briarproject.briar.sharing.SharerSessionState.Action.REMOTE_DECLINE;

@Immutable
@NotNullByDefault
class SharerEngine<I extends Invitation, SS extends SharerSessionState, IRR extends InvitationResponseReceivedEvent>
		implements ProtocolEngine<SharerSessionState.Action, SS, BaseMessage> {

	private static final Logger LOG =
			Logger.getLogger(SharerEngine.class.getName());

	private final InvitationFactory<I, SS> invitationFactory;
	private final InvitationResponseReceivedEventFactory<SS, IRR>
			invitationResponseReceivedEventFactory;
	private final Clock clock;

	SharerEngine(InvitationFactory<I, SS> invitationFactory,
			InvitationResponseReceivedEventFactory<SS, IRR> invitationResponseReceivedEventFactory,
			Clock clock) {
		this.invitationFactory = invitationFactory;
		this.invitationResponseReceivedEventFactory =
				invitationResponseReceivedEventFactory;
		this.clock = clock;
	}

	@Override
	public StateUpdate<SS, BaseMessage> onLocalAction(
			SS localState, SharerSessionState.Action action) {

		try {
			SharerSessionState.State currentState = localState.getState();
			SharerSessionState.State nextState = currentState.next(action);
			localState.setState(nextState);

			if (action == SharerSessionState.Action.LOCAL_ABORT &&
					currentState != SharerSessionState.State.ERROR) {
				return abortSession(currentState, localState);
			}

			if (nextState == SharerSessionState.State.ERROR) {
				if (LOG.isLoggable(WARNING)) {
					LOG.warning("Error: Invalid action in state " +
							currentState.name());
				}
				return noUpdate(localState, true);
			}
			List<BaseMessage> messages;
			List<Event> events = Collections.emptyList();

			if (action == SharerSessionState.Action.LOCAL_INVITATION) {
				BaseMessage msg = invitationFactory.build(localState,
						clock.currentTimeMillis());
				messages = Collections.singletonList(msg);
				logLocalAction(currentState, nextState, msg);

				// remember that we offered to share this forum
				localState
						.setTask(TASK_ADD_SHAREABLE_TO_LIST_TO_BE_SHARED_BY_US);
			} else if (action == SharerSessionState.Action.LOCAL_LEAVE) {
				BaseMessage msg = new SimpleMessage(SHARE_MSG_TYPE_LEAVE,
						localState.getGroupId(), localState.getSessionId(),
						clock.currentTimeMillis());
				messages = Collections.singletonList(msg);
				logLocalAction(currentState, nextState, msg);
			} else {
				throw new IllegalArgumentException("Unknown Local Action");
			}
			return new StateUpdate<SS, BaseMessage>(false,
					false, localState, messages, events);
		} catch (FormatException e) {
			throw new IllegalArgumentException(e);
		}
	}

	@Override
	public StateUpdate<SS, BaseMessage> onMessageReceived(
			SS localState, BaseMessage msg) {

		try {
			SharerSessionState.State currentState = localState.getState();
			SharerSessionState.Action action =
					SharerSessionState.Action.getRemote(msg.getType());
			SharerSessionState.State nextState = currentState.next(action);
			localState.setState(nextState);

			logMessageReceived(currentState, nextState, msg.getType(), msg);

			if (nextState == SharerSessionState.State.ERROR) {
				if (currentState != SharerSessionState.State.ERROR) {
					return abortSession(currentState, localState);
				} else {
					return noUpdate(localState, true);
				}
			}
			List<BaseMessage> messages = Collections.emptyList();
			List<Event> events = Collections.emptyList();
			boolean deleteMsg = false;

			if (currentState == SharerSessionState.State.LEFT) {
				// ignore and delete messages coming in while in that state
				deleteMsg = true;
			} else if (action == SharerSessionState.Action.REMOTE_LEAVE) {
				localState.setTask(TASK_UNSHARE_SHAREABLE_SHARED_BY_US);
			} else if (currentState == SharerSessionState.State.FINISHED) {
				// ignore and delete messages coming in while in that state
				// note that LEAVE is possible, but was handled above
				deleteMsg = true;
			}
			// we have sent our invitation and just got a response
			else if (action == REMOTE_ACCEPT || action == REMOTE_DECLINE) {
				if (action == REMOTE_ACCEPT) {
					localState.setTask(TASK_SHARE_SHAREABLE);
				} else {
					// this ensures that the forum can be shared again
					localState.setTask(
							TASK_REMOVE_SHAREABLE_FROM_LIST_TO_BE_SHARED_BY_US);
				}
				Event event = invitationResponseReceivedEventFactory
						.build(localState, action == REMOTE_ACCEPT,
								msg.getTime());
				events = Collections.singletonList(event);
			} else {
				throw new IllegalArgumentException("Bad state");
			}
			return new StateUpdate<SS, BaseMessage>(deleteMsg,
					false, localState, messages, events);
		} catch (FormatException e) {
			throw new IllegalArgumentException(e);
		}
	}

	private void logLocalAction(SharerSessionState.State currentState,
			SharerSessionState.State nextState,
			BaseMessage msg) {

		if (!LOG.isLoggable(INFO)) return;

		String a = "invitation";
		if (msg.getType() == SHARE_MSG_TYPE_LEAVE) a = "leave";

		LOG.info("Sending " + a + " in state " + currentState.name() +
				" with session ID " +
				msg.getSessionId().hashCode() + " in group " +
				msg.getGroupId().hashCode() + ". " +
				"Moving on to state " + nextState.name()
		);
	}

	private void logMessageReceived(SharerSessionState.State currentState,
			SharerSessionState.State nextState,
			long type, BaseMessage msg) {

		if (!LOG.isLoggable(INFO)) return;

		String t = "unknown";
		if (type == SHARE_MSG_TYPE_ACCEPT) t = "ACCEPT";
		else if (type == SHARE_MSG_TYPE_DECLINE) t = "DECLINE";
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
	public StateUpdate<SS, BaseMessage> onMessageDelivered(
			SS localState, BaseMessage delivered) {
		try {
			return noUpdate(localState, false);
		} catch (FormatException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return null;
		}
	}

	private StateUpdate<SS, BaseMessage> abortSession(
			SharerSessionState.State currentState, SS localState)
			throws FormatException {

		if (LOG.isLoggable(WARNING)) {
			LOG.warning("Aborting protocol session " +
					localState.getSessionId().hashCode() +
					" in state " + currentState.name());
		}

		localState.setState(SharerSessionState.State.ERROR);
		BaseMessage msg = new SimpleMessage(SHARE_MSG_TYPE_ABORT,
				localState.getGroupId(), localState.getSessionId(),
				clock.currentTimeMillis());
		List<BaseMessage> messages = Collections.singletonList(msg);

		List<Event> events = Collections.emptyList();

		return new StateUpdate<SS, BaseMessage>(false, false,
				localState, messages, events);
	}

	private StateUpdate<SS, BaseMessage> noUpdate(
			SS localState, boolean delete)
			throws FormatException {

		return new StateUpdate<SS, BaseMessage>(delete, false,
				localState, Collections.<BaseMessage>emptyList(),
				Collections.<Event>emptyList());
	}

}

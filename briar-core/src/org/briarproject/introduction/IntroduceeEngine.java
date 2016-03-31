package org.briarproject.introduction;

import org.briarproject.api.FormatException;
import org.briarproject.api.ProtocolEngine;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.IntroductionRequestReceivedEvent;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.introduction.IntroduceeAction;
import org.briarproject.api.introduction.IntroduceeProtocolState;
import org.briarproject.api.introduction.IntroductionRequest;
import org.briarproject.api.introduction.SessionId;
import org.briarproject.api.sync.MessageId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.api.introduction.IntroduceeAction.LOCAL_ABORT;
import static org.briarproject.api.introduction.IntroduceeAction.LOCAL_ACCEPT;
import static org.briarproject.api.introduction.IntroduceeAction.LOCAL_DECLINE;
import static org.briarproject.api.introduction.IntroduceeAction.REMOTE_ABORT;
import static org.briarproject.api.introduction.IntroduceeProtocolState.AWAIT_ACK;
import static org.briarproject.api.introduction.IntroduceeProtocolState.AWAIT_REMOTE_RESPONSE;
import static org.briarproject.api.introduction.IntroduceeProtocolState.AWAIT_REQUEST;
import static org.briarproject.api.introduction.IntroduceeProtocolState.AWAIT_RESPONSES;
import static org.briarproject.api.introduction.IntroduceeProtocolState.ERROR;
import static org.briarproject.api.introduction.IntroduceeProtocolState.FINISHED;
import static org.briarproject.api.introduction.IntroductionConstants.ACCEPT;
import static org.briarproject.api.introduction.IntroductionConstants.ANSWERED;
import static org.briarproject.api.introduction.IntroductionConstants.CONTACT_ID_1;
import static org.briarproject.api.introduction.IntroductionConstants.DEVICE_ID;
import static org.briarproject.api.introduction.IntroductionConstants.EXISTS;
import static org.briarproject.api.introduction.IntroductionConstants.E_PUBLIC_KEY;
import static org.briarproject.api.introduction.IntroductionConstants.GROUP_ID;
import static org.briarproject.api.introduction.IntroductionConstants.INTRODUCER;
import static org.briarproject.api.introduction.IntroductionConstants.MESSAGE_ID;
import static org.briarproject.api.introduction.IntroductionConstants.MESSAGE_TIME;
import static org.briarproject.api.introduction.IntroductionConstants.MSG;
import static org.briarproject.api.introduction.IntroductionConstants.NAME;
import static org.briarproject.api.introduction.IntroductionConstants.NOT_OUR_RESPONSE;
import static org.briarproject.api.introduction.IntroductionConstants.OUR_PUBLIC_KEY;
import static org.briarproject.api.introduction.IntroductionConstants.OUR_TIME;
import static org.briarproject.api.introduction.IntroductionConstants.PUBLIC_KEY;
import static org.briarproject.api.introduction.IntroductionConstants.REMOTE_AUTHOR_ID;
import static org.briarproject.api.introduction.IntroductionConstants.SESSION_ID;
import static org.briarproject.api.introduction.IntroductionConstants.STATE;
import static org.briarproject.api.introduction.IntroductionConstants.TASK;
import static org.briarproject.api.introduction.IntroductionConstants.TASK_ABORT;
import static org.briarproject.api.introduction.IntroductionConstants.TASK_ACTIVATE_CONTACT;
import static org.briarproject.api.introduction.IntroductionConstants.TASK_ADD_CONTACT;
import static org.briarproject.api.introduction.IntroductionConstants.TIME;
import static org.briarproject.api.introduction.IntroductionConstants.TRANSPORT;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE_ABORT;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE_ACK;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE_REQUEST;
import static org.briarproject.api.introduction.IntroductionConstants.TYPE_RESPONSE;

public class IntroduceeEngine
		implements ProtocolEngine<BdfDictionary, BdfDictionary, BdfDictionary> {

	private static final Logger LOG =
			Logger.getLogger(IntroduceeEngine.class.getName());

	@Override
	public StateUpdate<BdfDictionary, BdfDictionary> onLocalAction(
			BdfDictionary localState, BdfDictionary localAction) {

		try {
			IntroduceeProtocolState currentState =
					getState(localState.getLong(STATE));
			int type = localAction.getLong(TYPE).intValue();
			IntroduceeAction action;
			if (localState.containsKey(ACCEPT)) action = IntroduceeAction
					.getLocal(type, localState.getBoolean(ACCEPT));
			else action = IntroduceeAction.getLocal(type);
			IntroduceeProtocolState nextState = currentState.next(action);

			if (action == LOCAL_ABORT && currentState != ERROR) {
				return abortSession(currentState, localState);
			}

			if (nextState == ERROR) {
				if (LOG.isLoggable(WARNING)) {
					LOG.warning("Error: Invalid action in state " +
							currentState.name());
				}
				if (currentState == ERROR) return noUpdate(localState);
				else abortSession(currentState, localState);
			}

			if (action == LOCAL_ACCEPT || action == LOCAL_DECLINE) {
				localState.put(STATE, nextState.getValue());
				localState.put(ANSWERED, true);
				List<BdfDictionary> messages = new ArrayList<BdfDictionary>(1);
				// create the introduction response message
				BdfDictionary msg = new BdfDictionary();
				msg.put(TYPE, TYPE_RESPONSE);
				msg.put(GROUP_ID, localState.getRaw(GROUP_ID));
				msg.put(SESSION_ID, localState.getRaw(SESSION_ID));
				msg.put(ACCEPT, localState.getBoolean(ACCEPT));
				if (localState.getBoolean(ACCEPT)) {
					msg.put(TIME, localState.getLong(OUR_TIME));
					msg.put(E_PUBLIC_KEY, localState.getRaw(OUR_PUBLIC_KEY));
					msg.put(DEVICE_ID, localAction.getRaw(DEVICE_ID));
					msg.put(TRANSPORT, localAction.getDictionary(TRANSPORT));
				}
				messages.add(msg);
				logAction(currentState, localState, msg);

				if (nextState == AWAIT_ACK) {
					localState.put(TASK, TASK_ADD_CONTACT);
					// also send ACK, because we already have the other response
					BdfDictionary ack = getAckMessage(localState);
					messages.add(ack);
				}
				List<Event> events = Collections.emptyList();
				return new StateUpdate<BdfDictionary, BdfDictionary>(false,
						false,
						localState, messages, events);
			} else {
				throw new IllegalArgumentException();
			}
		} catch (FormatException e) {
			throw new IllegalArgumentException(e);
		}
	}

	@Override
	public StateUpdate<BdfDictionary, BdfDictionary> onMessageReceived(
			BdfDictionary localState, BdfDictionary msg) {

		try {
			IntroduceeProtocolState currentState =
					getState(localState.getLong(STATE));
			int type = msg.getLong(TYPE).intValue();
			IntroduceeAction action = IntroduceeAction.getRemote(type);
			IntroduceeProtocolState nextState = currentState.next(action);

			logMessageReceived(currentState, nextState, localState, type, msg);

			if (nextState == ERROR) {
				if (currentState != ERROR && action != REMOTE_ABORT) {
					return abortSession(currentState, localState);
				} else {
					return noUpdate(localState);
				}
			}

			// update local session state with next protocol state
			localState.put(STATE, nextState.getValue());
			List<BdfDictionary> messages;
			List<Event> events;
			// we received the introduction request
			if (currentState == AWAIT_REQUEST) {
				// remember the session ID used by the introducer
				localState.put(SESSION_ID, msg.getRaw(SESSION_ID));

				addRequestData(localState, msg);
				messages = Collections.emptyList();
				events = Collections.singletonList(getEvent(localState, msg));
			}
			// we had the request and now one response came in _OR_
			// we had sent our response already and now received the other one
			else if (currentState == AWAIT_RESPONSES ||
					currentState == AWAIT_REMOTE_RESPONSE) {
				// update next state based on message content
				action = IntroduceeAction
						.getRemote(type, msg.getBoolean(ACCEPT));
				nextState = currentState.next(action);
				localState.put(STATE, nextState.getValue());

				addResponseData(localState, msg);
				if (nextState == AWAIT_ACK) {
					localState.put(TASK, TASK_ADD_CONTACT);
					messages = Collections
							.singletonList(getAckMessage(localState));
				} else {
					messages = Collections.emptyList();
				}
				events = Collections.emptyList();
			}
			// we already sent our ACK and now received the other one
			else if (currentState == AWAIT_ACK) {
				localState.put(TASK, TASK_ACTIVATE_CONTACT);
				messages = Collections.emptyList();
				events = Collections.emptyList();
			}
			// we are done (probably declined response) and ignore this message
			else if (currentState == FINISHED) {
				return noUpdate(localState);
			}
			// this should not happen
			else {
				throw new IllegalArgumentException();
			}
			return new StateUpdate<BdfDictionary, BdfDictionary>(false, false,
					localState, messages, events);
		} catch (FormatException e) {
			throw new IllegalArgumentException(e);
		}
	}

	private void addRequestData(BdfDictionary localState, BdfDictionary msg)
			throws FormatException {

		localState.put(NAME, msg.getString(NAME));
		localState.put(PUBLIC_KEY, msg.getRaw(PUBLIC_KEY));
		if (msg.containsKey(MSG)) {
			localState.put(MSG, msg.getString(MSG));
		}
	}

	private void addResponseData(BdfDictionary localState, BdfDictionary msg)
			throws FormatException {

		if (localState.containsKey(ACCEPT)) {
			localState.put(ACCEPT,
					localState.getBoolean(ACCEPT) && msg.getBoolean(ACCEPT));
		} else {
			localState.put(ACCEPT, msg.getBoolean(ACCEPT));
		}
		localState.put(NOT_OUR_RESPONSE, msg.getRaw(MESSAGE_ID));

		if (msg.getBoolean(ACCEPT)) {
			localState.put(TIME, msg.getLong(TIME));
			localState.put(E_PUBLIC_KEY, msg.getRaw(E_PUBLIC_KEY));
			localState.put(DEVICE_ID, msg.getRaw(DEVICE_ID));
			localState.put(TRANSPORT, msg.getDictionary(TRANSPORT));
		}
	}

	private BdfDictionary getAckMessage(BdfDictionary localState)
			throws FormatException {

		BdfDictionary m = new BdfDictionary();
		m.put(TYPE, TYPE_ACK);
		m.put(GROUP_ID, localState.getRaw(GROUP_ID));
		m.put(SESSION_ID, localState.getRaw(SESSION_ID));

		if (LOG.isLoggable(INFO)) {
			LOG.info("Sending ACK " + " to " +
					localState.getString(INTRODUCER) + " for " +
					localState.getString(NAME) + " with session ID " +
					Arrays.hashCode(m.getRaw(SESSION_ID)) + " in group " +
					Arrays.hashCode(m.getRaw(GROUP_ID)));
		}
		return m;
	}

	private void logAction(IntroduceeProtocolState state,
			BdfDictionary localState, BdfDictionary msg) {

		if (!LOG.isLoggable(INFO)) return;

		try {
			LOG.info("Sending " +
					(localState.getBoolean(ACCEPT) ? "accept " : "decline ") +
					"response in state " + state.name() +
					" to " + localState.getString(INTRODUCER) +
					" for " + localState.getString(NAME) + " with session ID " +
					Arrays.hashCode(msg.getRaw(SESSION_ID)) + " in group " +
					Arrays.hashCode(msg.getRaw(GROUP_ID)) + ". " +
					"Moving on to state " +
					getState(localState.getLong(STATE)).name()
			);
		} catch (FormatException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	private void logMessageReceived(IntroduceeProtocolState currentState,
			IntroduceeProtocolState nextState, BdfDictionary localState,
			int type, BdfDictionary msg) {

		if (!LOG.isLoggable(INFO)) return;

		try {
			String t = "unknown";
			if (type == TYPE_REQUEST) t = "Introduction";
			else if (type == TYPE_RESPONSE) t = "Response";
			else if (type == TYPE_ACK) t = "ACK";
			else if (type == TYPE_ABORT) t = "Abort";

			LOG.info("Received " + t + " in state " + currentState.name() +
					" from " + localState.getString(INTRODUCER) +
					(localState.containsKey(NAME) ?
							" related to " + localState.getString(NAME) : "") +
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
			return noUpdate(localState);
		} catch (FormatException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return null;
		}
	}

	private IntroduceeProtocolState getState(Long state) {
		return IntroduceeProtocolState.fromValue(state.intValue());
	}

	private Event getEvent(BdfDictionary localState, BdfDictionary msg)
			throws FormatException {

		ContactId contactId =
				new ContactId(localState.getLong(CONTACT_ID_1).intValue());
		AuthorId authorId = new AuthorId(localState.getRaw(REMOTE_AUTHOR_ID));

		SessionId sessionId = new SessionId(localState.getRaw(SESSION_ID));
		MessageId messageId = new MessageId(msg.getRaw(MESSAGE_ID));
		long time = msg.getLong(MESSAGE_TIME);
		String name = msg.getString(NAME);
		String message = msg.getOptionalString(MSG);
		boolean exists = localState.getBoolean(EXISTS);

		IntroductionRequest ir = new IntroductionRequest(sessionId, messageId,
				time, false, false, false, false, authorId, name, false,
				message, false, exists);
		return new IntroductionRequestReceivedEvent(contactId, ir);
	}

	private StateUpdate<BdfDictionary, BdfDictionary> abortSession(
			IntroduceeProtocolState currentState, BdfDictionary localState)
			throws FormatException {

		if (LOG.isLoggable(WARNING)) {
			LOG.warning("Aborting protocol session " +
					Arrays.hashCode(localState.getRaw(SESSION_ID)) +
					" in state " + currentState.name());
		}

		localState.put(STATE, ERROR.getValue());
		localState.put(TASK, TASK_ABORT);
		BdfDictionary msg = new BdfDictionary();
		msg.put(TYPE, TYPE_ABORT);
		msg.put(GROUP_ID, localState.getRaw(GROUP_ID));
		msg.put(SESSION_ID, localState.getRaw(SESSION_ID));
		List<BdfDictionary> messages = Collections.singletonList(msg);
		// TODO inform about protocol abort via new Event?
		List<Event> events = Collections.emptyList();
		return new StateUpdate<BdfDictionary, BdfDictionary>(false, false,
				localState, messages, events);
	}

	private StateUpdate<BdfDictionary, BdfDictionary> noUpdate(
			BdfDictionary localState) throws FormatException {

		return new StateUpdate<BdfDictionary, BdfDictionary>(false, false,
				localState, new ArrayList<BdfDictionary>(0),
				new ArrayList<Event>(0));
	}

}

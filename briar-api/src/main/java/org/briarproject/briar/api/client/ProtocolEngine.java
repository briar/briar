package org.briarproject.briar.api.client;

import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.List;

@Deprecated
@NotNullByDefault
public interface ProtocolEngine<A, S, M> {

	StateUpdate<S, M> onLocalAction(S localState, A action);

	StateUpdate<S, M> onMessageReceived(S localState, M received);

	StateUpdate<S, M> onMessageDelivered(S localState, M delivered);

	class StateUpdate<S, M> {
		public final boolean deleteMessage;
		public final boolean deleteState;
		public final S localState;
		public final List<M> toSend;
		public final List<Event> toBroadcast;

		/**
		 * This class represents an update of the local protocol state.
		 * It only shows how the state should be updated,
		 * but does not carry out the updates on its own.
		 *
		 * @param deleteMessage whether to delete the message that triggered
		 * the state update. This will be ignored for
		 * {@link ProtocolEngine#onLocalAction}.
		 * @param deleteState whether to delete the localState {@link S}
		 * @param localState the new local state
		 * @param toSend a list of messages to be sent as part of the
		 * state update
		 * @param toBroadcast a list of events to broadcast as result of the
		 * state update
		 */
		public StateUpdate(boolean deleteMessage, boolean deleteState,
				S localState, List<M> toSend, List<Event> toBroadcast) {

			this.deleteMessage = deleteMessage;
			this.deleteState = deleteState;
			this.localState = localState;
			this.toSend = toSend;
			this.toBroadcast = toBroadcast;
		}
	}
}

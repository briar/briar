package org.briarproject.conversation;

import org.briarproject.api.conversation.ConversationIntroductionResponseItem;
import org.briarproject.api.conversation.ConversationItem;
import org.briarproject.api.introduction.IntroductionResponse;

class ConversationIntroductionResponseItemImpl {

	static ConversationItem from(IntroductionResponse i) {
		return i.isLocal() ? new Outgoing(i) : new Incoming(i);
	}

	static class Outgoing extends OutgoingConversationItem
			implements ConversationIntroductionResponseItem {

		private final IntroductionResponse ir;

		public Outgoing(IntroductionResponse ir) {
			super(ir.getMessageId(), ir.getTimestamp(), ir.isSent(), ir.isSeen());

			this.ir = ir;
		}

		@Override
		public IntroductionResponse getIntroductionResponse() {
			return ir;
		}
	}

	static class Incoming extends IncomingConversationItem
			implements ConversationIntroductionResponseItem {

		private final IntroductionResponse ir;

		public Incoming(IntroductionResponse ir) {
			super(ir.getMessageId(), ir.getTimestamp(), ir.isRead());

			this.ir = ir;
		}

		@Override
		public IntroductionResponse getIntroductionResponse() {
			return ir;
		}
	}
}

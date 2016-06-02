package org.briarproject.conversation;

import org.briarproject.api.conversation.ConversationItem;
import org.briarproject.api.conversation.ConversationMessageItem;
import org.briarproject.api.messaging.PrivateMessageHeader;

import java.util.concurrent.locks.ReentrantReadWriteLock;

class ConversationMessageItemImpl {

	static ConversationItem from(PrivateMessageHeader h) {
		return h.isLocal() ? new Outgoing(h) : new Incoming(h);
	}

	static class Outgoing extends OutgoingConversationItem
			implements ConversationMessageItem {

		private final PrivateMessageHeader header;
		private byte[] body;
		private ContentListener listener;
		private final ReentrantReadWriteLock bodyLock =
				new ReentrantReadWriteLock();
		private final ReentrantReadWriteLock listenerLock =
				new ReentrantReadWriteLock();

		public Outgoing(PrivateMessageHeader header) {
			super(header.getId(), header.getTimestamp(), header.isSent(),
					header.isSeen());

			this.header = header;
			body = null;
			listener = null;
		}

		@Override
		public PrivateMessageHeader getHeader() {
			return header;
		}

		@Override
		public byte[] getBody() {
			bodyLock.readLock().lock();
			byte[] ret = body;
			bodyLock.readLock().unlock();
			return ret;
		}

		@Override
		public void setContent(byte[] content) {
			bodyLock.writeLock().lock();
			this.body = content;
			bodyLock.writeLock().unlock();
			listenerLock.readLock().lock();
			if (listener != null) {
				listener.contentReady();
			}
			listenerLock.readLock().unlock();
		}

		@Override
		public void setContentListener(
				ContentListener listener) {
			listenerLock.writeLock().lock();
			this.listener = listener;
			listenerLock.writeLock().unlock();
			bodyLock.readLock().lock();
			if (body != null) {
				listener.contentReady();
			}
			bodyLock.readLock().unlock();
		}
	}

	static class Incoming extends IncomingConversationItem
			implements ConversationMessageItem {

		private final PrivateMessageHeader header;
		private byte[] body;
		private ContentListener listener;
		private final ReentrantReadWriteLock bodyLock =
				new ReentrantReadWriteLock();
		private final ReentrantReadWriteLock listenerLock =
				new ReentrantReadWriteLock();

		public Incoming(PrivateMessageHeader header) {
			super(header.getId(), header.getTimestamp(), header.isRead());

			this.header = header;
			body = null;
			listener = null;
		}

		@Override
		public PrivateMessageHeader getHeader() {
			return header;
		}

		@Override
		public byte[] getBody() {
			bodyLock.readLock().lock();
			byte[] ret = body;
			bodyLock.readLock().unlock();
			return ret;
		}

		@Override
		public void setContent(byte[] content) {
			bodyLock.writeLock().lock();
			this.body = content;
			bodyLock.writeLock().unlock();
			listenerLock.readLock().lock();
			if (listener != null) {
				listener.contentReady();
			}
			listenerLock.readLock().unlock();
		}

		@Override
		public void setContentListener(
				ContentListener listener) {
			listenerLock.writeLock().lock();
			this.listener = listener;
			listenerLock.writeLock().unlock();
			bodyLock.readLock().lock();
			if (body != null) {
				listener.contentReady();
			}
			bodyLock.readLock().unlock();
		}
	}
}

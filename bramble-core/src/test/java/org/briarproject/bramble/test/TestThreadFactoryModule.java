package org.briarproject.bramble.test;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import dagger.Module;
import dagger.Provides;

@Module
public class TestThreadFactoryModule {

	@Nullable
	private final String prefix;

	public TestThreadFactoryModule() {
		this(null);
	}

	public TestThreadFactoryModule(@Nullable String prefix) {
		this.prefix = prefix;
	}

	@Provides
	ThreadFactory provideThreadFactory() {
		if (prefix == null) return Executors.defaultThreadFactory();
		return new TestThreadFactory(prefix);
	}

	/**
	 * This class is mostly copied from
	 * {@link Executors#defaultThreadFactory()} only adds a given prefix.
	 */
	static class TestThreadFactory implements ThreadFactory {
		private static final AtomicInteger poolNumber = new AtomicInteger(1);
		private final ThreadGroup group;
		private final AtomicInteger threadNumber = new AtomicInteger(1);
		private final String namePrefix;

		private TestThreadFactory(String prefix) {
			SecurityManager s = System.getSecurityManager();
			this.group = s != null ? s.getThreadGroup() :
					Thread.currentThread().getThreadGroup();
			this.namePrefix =
					prefix + "-p-" + poolNumber.getAndIncrement() + "-t-";
		}

		@Override
		public Thread newThread(@Nonnull Runnable r) {
			Thread t = new Thread(this.group, r,
					this.namePrefix + this.threadNumber.getAndIncrement(), 0L);
			if (t.isDaemon()) {
				t.setDaemon(false);
			}

			if (t.getPriority() != 5) {
				t.setPriority(5);
			}

			return t;
		}
	}
}

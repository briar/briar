package net.sf.briar.api.setup;

public interface SetupWorkerFactory {

	Runnable createWorker(SetupCallback callback, SetupParameters parameters);
}

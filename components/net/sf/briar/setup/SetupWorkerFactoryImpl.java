package net.sf.briar.setup;

import net.sf.briar.api.i18n.I18n;
import net.sf.briar.api.setup.SetupCallback;
import net.sf.briar.api.setup.SetupParameters;
import net.sf.briar.api.setup.SetupWorkerFactory;

import com.google.inject.Inject;

public class SetupWorkerFactoryImpl implements SetupWorkerFactory {

	private final I18n i18n;

	@Inject
	public SetupWorkerFactoryImpl(I18n i18n) {
		this.i18n = i18n;
	}

	public Runnable createWorker(SetupCallback callback,
			SetupParameters parameters) {
		return new SetupWorker(callback, parameters, i18n);
	}
}

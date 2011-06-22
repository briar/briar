package net.sf.briar.setup;

import java.io.File;
import java.security.CodeSource;

import net.sf.briar.api.i18n.I18n;
import net.sf.briar.api.setup.SetupCallback;
import net.sf.briar.api.setup.SetupParameters;
import net.sf.briar.api.setup.SetupWorkerFactory;
import net.sf.briar.util.FileUtils;

public class SetupWorkerFactoryImpl implements SetupWorkerFactory {

	private final I18n i18n;

	public SetupWorkerFactoryImpl(I18n i18n) {
		this.i18n = i18n;
	}

	public Runnable createWorker(SetupCallback callback,
			SetupParameters parameters) {
		CodeSource c = FileUtils.class.getProtectionDomain().getCodeSource();
		File jar = new File(c.getLocation().getPath());
		assert jar.exists();
		return new SetupWorker(callback, parameters, i18n, jar);
	}
}

package net.sf.briar.crypto;

import net.sf.briar.api.crypto.CryptoComponent;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

public class CryptoModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(CryptoComponent.class).to(
				CryptoComponentImpl.class).in(Singleton.class);
	}
}

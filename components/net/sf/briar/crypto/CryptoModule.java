package net.sf.briar.crypto;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.SecretStorageKey;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

public class CryptoModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(CryptoComponent.class).to(
				CryptoComponentImpl.class).in(Singleton.class);
		// FIXME: Use a real key
		bind(SecretKey.class).annotatedWith(SecretStorageKey.class).toInstance(
				new SecretKeySpec(new byte[32], "AES"));
				
	}
}

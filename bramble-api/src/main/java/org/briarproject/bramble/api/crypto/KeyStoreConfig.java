package org.briarproject.bramble.api.crypto;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.security.spec.AlgorithmParameterSpec;

@NotNullByDefault
public interface KeyStoreConfig {

	String getKeyStoreType();

	String getAlias();

	String getProviderName();

	String getMacAlgorithmName();

	AlgorithmParameterSpec getParameterSpec();
}

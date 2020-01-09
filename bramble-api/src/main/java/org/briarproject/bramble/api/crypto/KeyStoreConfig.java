package org.briarproject.bramble.api.crypto;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.security.spec.AlgorithmParameterSpec;
import java.util.List;

/**
 * Configures the use of a stored key to strengthen password-based encryption.
 * The key may be stored in a hardware security module, but this is not
 * guaranteed. See
 * {@link CryptoComponent#encryptWithPassword(byte[], String, KeyStoreConfig)}
 * and
 * {@link CryptoComponent#decryptWithPassword(byte[], String, KeyStoreConfig)}.
 */
@NotNullByDefault
public interface KeyStoreConfig {

	String getKeyStoreType();

	String getKeyAlias();

	String getProviderName();

	String getMacAlgorithmName();

	/**
	 * Returns a list of {@link AlgorithmParameterSpec AlgorithmParameterSpecs}
	 * to use for key generation, in order of preference.
	 */
	List<AlgorithmParameterSpec> getParameterSpecs();
}

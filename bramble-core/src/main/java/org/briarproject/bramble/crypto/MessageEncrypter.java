package org.briarproject.bramble.crypto;

import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.crypto.KeyParser;
import org.briarproject.bramble.api.crypto.PrivateKey;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.util.StringUtils;
import org.spongycastle.asn1.teletrust.TeleTrusTNamedCurves;
import org.spongycastle.asn1.x9.X9ECParameters;
import org.spongycastle.crypto.AsymmetricCipherKeyPair;
import org.spongycastle.crypto.BasicAgreement;
import org.spongycastle.crypto.BlockCipher;
import org.spongycastle.crypto.CipherParameters;
import org.spongycastle.crypto.CryptoException;
import org.spongycastle.crypto.DerivationFunction;
import org.spongycastle.crypto.KeyEncoder;
import org.spongycastle.crypto.Mac;
import org.spongycastle.crypto.agreement.ECDHCBasicAgreement;
import org.spongycastle.crypto.digests.SHA256Digest;
import org.spongycastle.crypto.engines.AESLightEngine;
import org.spongycastle.crypto.engines.IESEngine;
import org.spongycastle.crypto.generators.ECKeyPairGenerator;
import org.spongycastle.crypto.generators.EphemeralKeyPairGenerator;
import org.spongycastle.crypto.generators.KDF2BytesGenerator;
import org.spongycastle.crypto.macs.HMac;
import org.spongycastle.crypto.modes.CBCBlockCipher;
import org.spongycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.spongycastle.crypto.params.AsymmetricKeyParameter;
import org.spongycastle.crypto.params.ECDomainParameters;
import org.spongycastle.crypto.params.ECKeyGenerationParameters;
import org.spongycastle.crypto.params.ECPrivateKeyParameters;
import org.spongycastle.crypto.params.ECPublicKeyParameters;
import org.spongycastle.crypto.params.IESWithCipherParameters;
import org.spongycastle.crypto.parsers.ECIESPublicKeyParser;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.util.Scanner;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class MessageEncrypter {

	private static final ECDomainParameters PARAMETERS;
	private static final int MESSAGE_KEY_BITS = 512;
	private static final int MAC_KEY_BITS = 256;
	private static final int CIPHER_KEY_BITS = 256;
	private static final int LINE_LENGTH = 70;

	static {
		X9ECParameters x9 = TeleTrusTNamedCurves.getByName("brainpoolp512r1");
		PARAMETERS = new ECDomainParameters(x9.getCurve(), x9.getG(),
				x9.getN(), x9.getH());
	}

	private final ECKeyPairGenerator generator;
	private final KeyParser parser;
	private final EphemeralKeyPairGenerator ephemeralGenerator;
	private final PublicKeyParser ephemeralParser;

	MessageEncrypter(SecureRandom random) {
		generator = new ECKeyPairGenerator();
		generator.init(new ECKeyGenerationParameters(PARAMETERS, random));
		parser = new Sec1KeyParser(PARAMETERS, MESSAGE_KEY_BITS);
		KeyEncoder encoder = new PublicKeyEncoder();
		ephemeralGenerator = new EphemeralKeyPairGenerator(generator, encoder);
		ephemeralParser = new PublicKeyParser(PARAMETERS);
	}

	KeyPair generateKeyPair() {
		AsymmetricCipherKeyPair keyPair = generator.generateKeyPair();
		// Return a wrapper that uses the SEC 1 encoding
		ECPublicKeyParameters ecPublicKey =
				(ECPublicKeyParameters) keyPair.getPublic();
		PublicKey publicKey = new Sec1PublicKey(ecPublicKey);
		ECPrivateKeyParameters ecPrivateKey =
				(ECPrivateKeyParameters) keyPair.getPrivate();
		PrivateKey privateKey =
				new Sec1PrivateKey(ecPrivateKey, MESSAGE_KEY_BITS);
		return new KeyPair(publicKey, privateKey);
	}

	KeyParser getKeyParser() {
		return parser;
	}

	byte[] encrypt(PublicKey pub, byte[] plaintext) throws CryptoException {
		if (!(pub instanceof Sec1PublicKey))
			throw new IllegalArgumentException();
		IESEngine engine = getEngine();
		engine.init(((Sec1PublicKey) pub).getKey(), getCipherParameters(),
				ephemeralGenerator);
		return engine.processBlock(plaintext, 0, plaintext.length);
	}

	byte[] decrypt(PrivateKey priv, byte[] ciphertext)
			throws CryptoException {
		if (!(priv instanceof Sec1PrivateKey))
			throw new IllegalArgumentException();
		IESEngine engine = getEngine();
		engine.init(((Sec1PrivateKey) priv).getKey(), getCipherParameters(),
				ephemeralParser);
		return engine.processBlock(ciphertext, 0, ciphertext.length);
	}

	private IESEngine getEngine() {
		BasicAgreement agreement = new ECDHCBasicAgreement();
		DerivationFunction kdf = new KDF2BytesGenerator(new SHA256Digest());
		Mac mac = new HMac(new SHA256Digest());
		BlockCipher cipher = new CBCBlockCipher(new AESLightEngine());
		PaddedBufferedBlockCipher pad = new PaddedBufferedBlockCipher(cipher);
		return new IESEngine(agreement, kdf, mac, pad);
	}

	private CipherParameters getCipherParameters() {
		return new IESWithCipherParameters(null, null, MAC_KEY_BITS,
				CIPHER_KEY_BITS);
	}

	private static class PublicKeyEncoder implements KeyEncoder {

		@Override
		public byte[] getEncoded(AsymmetricKeyParameter key) {
			if (!(key instanceof ECPublicKeyParameters))
				throw new IllegalArgumentException();
			return ((ECPublicKeyParameters) key).getQ().getEncoded(false);
		}
	}

	private static class PublicKeyParser extends ECIESPublicKeyParser {

		private PublicKeyParser(ECDomainParameters ecParams) {
			super(ecParams);
		}

		@Override
		public AsymmetricKeyParameter readKey(InputStream in)
				throws IOException {
			try {
				return super.readKey(in);
			} catch (IllegalArgumentException e) {
				throw new IOException(e);
			}
		}
	}

	public static void main(String[] args) throws Exception {
		if (args.length < 1) {
			printUsage();
			return;
		}
		SecureRandom random = new SecureRandom();
		MessageEncrypter encrypter = new MessageEncrypter(random);
		if (args[0].equals("generate")) {
			if (args.length != 3) {
				printUsage();
				return;
			}
			// Generate a key pair
			KeyPair keyPair = encrypter.generateKeyPair();
			PrintStream out = new PrintStream(new FileOutputStream(args[1]));
			out.print(
					StringUtils.toHexString(keyPair.getPublic().getEncoded()));
			out.flush();
			out.close();
			out = new PrintStream(new FileOutputStream(args[2]));
			out.print(
					StringUtils.toHexString(keyPair.getPrivate().getEncoded()));
			out.flush();
			out.close();
		} else if (args[0].equals("encrypt")) {
			if (args.length != 2) {
				printUsage();
				return;
			}
			// Encrypt a decrypted message
			InputStream in = new FileInputStream(args[1]);
			byte[] keyBytes = StringUtils.fromHexString(readFully(in).trim());
			PublicKey publicKey =
					encrypter.getKeyParser().parsePublicKey(keyBytes);
			String message = readFully(System.in);
			byte[] plaintext = message.getBytes(Charset.forName("UTF-8"));
			byte[] ciphertext = encrypter.encrypt(publicKey, plaintext);
			System.out.println(AsciiArmour.wrap(ciphertext, LINE_LENGTH));
		} else if (args[0].equals("decrypt")) {
			if (args.length != 2) {
				printUsage();
				return;
			}
			// Decrypt an encrypted message
			InputStream in = new FileInputStream(args[1]);
			byte[] keyBytes = StringUtils.fromHexString(readFully(in).trim());
			PrivateKey privateKey =
					encrypter.getKeyParser().parsePrivateKey(keyBytes);
			byte[] ciphertext = AsciiArmour.unwrap(readFully(System.in));
			byte[] plaintext = encrypter.decrypt(privateKey, ciphertext);
			System.out.println(new String(plaintext, Charset.forName("UTF-8")));
		} else {
			printUsage();
		}
	}

	private static void printUsage() {
		System.err.println("Usage:");
		System.err.println(
				"MessageEncrypter generate <public_key_file> <private_key_file>");
		System.err.println("MessageEncrypter encrypt <public_key_file>");
		System.err.println("MessageEncrypter decrypt <private_key_file>");
	}

	private static String readFully(InputStream in) throws IOException {
		String newline = System.getProperty("line.separator");
		StringBuilder stringBuilder = new StringBuilder();
		Scanner scanner = new Scanner(in);
		while (scanner.hasNextLine()) {
			stringBuilder.append(scanner.nextLine());
			stringBuilder.append(newline);
		}
		scanner.close();
		in.close();
		return stringBuilder.toString();
	}
}

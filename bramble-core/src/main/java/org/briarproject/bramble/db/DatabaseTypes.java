package org.briarproject.bramble.db;

class DatabaseTypes {

	private final String hashType, secretType, binaryType;
	private final String counterType, stringType;
	private final String explainCommand; // FIXME: Remove

	public DatabaseTypes(String hashType, String secretType, String binaryType,
			String counterType, String stringType, String explainCommand) {
		this.hashType = hashType;
		this.secretType = secretType;
		this.binaryType = binaryType;
		this.counterType = counterType;
		this.stringType = stringType;
		this.explainCommand = explainCommand;
	}

	/**
	 * Replaces database type placeholders in a statement with the actual types.
	 * These placeholders are currently supported:
	 * <li> _HASH
	 * <li> _SECRET
	 * <li> _BINARY
	 * <li> _COUNTER
	 * <li> _STRING
	 * <li> _EXPLAIN
	 */
	String replaceTypes(String s) {
		s = s.replaceAll("_HASH", hashType);
		s = s.replaceAll("_SECRET", secretType);
		s = s.replaceAll("_BINARY", binaryType);
		s = s.replaceAll("_COUNTER", counterType);
		s = s.replaceAll("_STRING", stringType);
		s = s.replaceAll("_EXPLAIN", explainCommand);
		return s;
	}
}

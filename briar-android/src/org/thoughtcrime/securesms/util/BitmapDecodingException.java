package org.thoughtcrime.securesms.util;

public class BitmapDecodingException extends Exception {

	BitmapDecodingException(String s) {
		super(s);
	}

	BitmapDecodingException(Exception nested) {
		super(nested);
	}
}

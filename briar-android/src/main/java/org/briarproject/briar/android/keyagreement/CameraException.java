package org.briarproject.briar.android.keyagreement;

import java.io.IOException;

public class CameraException extends IOException {

	CameraException(String message) {
		super(message);
	}

	CameraException(Throwable cause) {
		super(cause);
	}
}

package org.briarproject.briar.android.attachment;

class Size {

	final int width;
	final int height;
	final String mimeType;
	final boolean error;

	Size(int width, int height, String mimeType) {
		this.width = width;
		this.height = height;
		this.mimeType = mimeType;
		this.error = false;
	}

	Size() {
		this.width = 0;
		this.height = 0;
		this.mimeType = "";
		this.error = true;
	}
}

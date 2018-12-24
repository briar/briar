package org.briarproject.briar.android.conversation;

import android.graphics.BitmapFactory.Options;
import android.support.annotation.Nullable;

import org.hamcrest.Description;
import org.jmock.Expectations;
import org.jmock.api.Action;
import org.jmock.api.Invocation;

import java.io.InputStream;

class BitmapDecoderExpectations extends Expectations {

	protected void withDecodeStream(ImageManager imageManager,
			OptionsModifier optionsModifier) {
		oneOf(imageManager).decodeStream(with(any(InputStream.class)),
				with(any(Options.class)));
		currentBuilder().setAction(new BitmapDecoderAction(optionsModifier));
	}

	private class BitmapDecoderAction implements Action {

		private final OptionsModifier optionsModifier;

		private BitmapDecoderAction(OptionsModifier optionsModifier) {
			this.optionsModifier = optionsModifier;
		}

		@Nullable
		@Override
		public Object invoke(Invocation invocation) {
			Options options = (Options) invocation.getParameter(1);
			optionsModifier.modifyOptions(options);
			return null;
		}

		@Override
		public void describeTo(Description description) {
			description.appendText("decodes a Bitmap InputStream");
		}
	}

	interface OptionsModifier {
		void modifyOptions(Options options);
	}

}

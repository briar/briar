package org.briarproject.briar.android.attachment;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {
		AttachmentModule.class
})
interface AbstractImageCompressorComponent {

	void inject(AbstractImageCompressorTest test);

}

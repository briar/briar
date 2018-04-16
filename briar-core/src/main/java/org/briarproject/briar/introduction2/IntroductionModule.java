package org.briarproject.briar.introduction2;

import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.data.MetadataEncoder;
import org.briarproject.bramble.api.sync.ValidationManager;
import org.briarproject.bramble.api.system.Clock;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

import static org.briarproject.briar.api.introduction.IntroductionManager.CLIENT_ID;

@Module
public class IntroductionModule {

	public static class EagerSingletons {
		@Inject
		IntroductionValidator introductionValidator;
	}

	@Provides
	@Singleton
	IntroductionValidator provideValidator(ValidationManager validationManager,
			MessageEncoder messageEncoder, MetadataEncoder metadataEncoder,
			ClientHelper clientHelper, Clock clock) {

		IntroductionValidator introductionValidator =
				new IntroductionValidator(messageEncoder, clientHelper,
						metadataEncoder, clock);
		validationManager.registerMessageValidator(CLIENT_ID,
				introductionValidator);

		return introductionValidator;
	}

}

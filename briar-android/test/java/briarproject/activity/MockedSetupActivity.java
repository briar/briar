package briarproject.activity;

import org.briarproject.android.ActivityModule;
import org.briarproject.android.SetupActivity;
import org.briarproject.android.controller.SetupController;
import org.briarproject.android.controller.SetupControllerImp;
import org.briarproject.android.controller.handler.ResultHandler;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.logging.Logger;

import static org.briarproject.api.crypto.PasswordStrengthEstimator.NONE;
import static org.briarproject.api.crypto.PasswordStrengthEstimator.QUITE_STRONG;
import static org.briarproject.api.crypto.PasswordStrengthEstimator.QUITE_WEAK;
import static org.briarproject.api.crypto.PasswordStrengthEstimator.STRONG;
import static org.briarproject.api.crypto.PasswordStrengthEstimator.WEAK;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;

public class MockedSetupActivity extends SetupActivity {

	private static final Logger LOG =
			Logger.getLogger(MockedSetupActivity.class.getName());

	final static String STRONG_PASS = "strong";
	final static String QSTRONG_PASS = "qstrong";
	final static String QWEAK_PASS = "qweak";
	final static String WEAK_PASS = "weak";
	final static String NO_PASS = "none";

	@Override
	protected ActivityModule getActivityModule() {
		return new ActivityModule(this) {

			@Override
			protected SetupController provideSetupController(
					SetupControllerImp setupControllerImp) {
				SetupController setupController =
						Mockito.mock(SetupControllerImp.class);

				Mockito.doAnswer(new Answer<Void>() {
					@Override
					public Void answer(InvocationOnMock invocation)
							throws Throwable {
						((ResultHandler<Long>) invocation.getArguments()[2])
								.onResult(1L);
						return null;
					}
				}).when(setupController)
						.createIdentity(anyString(), anyString(),
								(ResultHandler<Long>) any());
				Mockito.when(
						setupController
								.estimatePasswordStrength(anyString()))
						.thenAnswer(new Answer<Float>() {
							@Override
							public Float answer(
									InvocationOnMock invocation)
									throws Throwable {
								String p = (String) invocation
										.getArguments()[0];
								LOG.info("p = " + p);
								if (p.equals(STRONG_PASS)) {
									return STRONG;
								} else if (p.equals(QSTRONG_PASS)) {
									return QUITE_STRONG;
								} else if (p.equals(QWEAK_PASS)) {
									return QUITE_WEAK;
								} else if (p.equals(WEAK_PASS)) {
									return WEAK;
								} else if (p.equals(NO_PASS)) {
									return NONE;
								} else {
									return STRONG;
								}
							}
						});

				return setupController;
			}
		};
	}
}

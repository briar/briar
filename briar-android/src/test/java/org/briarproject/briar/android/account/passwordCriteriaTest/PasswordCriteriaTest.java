package org.briarproject.briar.android.account.passwordCriteriaTest;

import org.briarproject.briar.android.util.AccountSetUpCriteria;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class PasswordCriteriaTest {

	// This test case is drafted to check the implementation of the checkCriteria method in AccountSetUpCriteria class.
	// This test case is to test if it returns throws an exception when a null value is passed which will never be the
	// case in the code I implemented, but for the developers who might use that method in the future.
	@Test(expected = Exception.class)
	public void testExceptionIsThrown() {
		AccountSetUpCriteria accountSetUpCriteria = new AccountSetUpCriteria();
		accountSetUpCriteria.checkCriteria(null);
	}

	// This test case is drafted to check the implementation of the checkCriteria method in AccountSetUpCriteria class.
	// The method this test case is used to verify if the password chosen by the user is secure enough.
	// This test case is to test if it returns true for a String that should actually be allowed to be set as the password
	// i.e. A string with an upper case, a lower case, a digit and a special character.
	@Test
	public void testPasswordCriteriaPass() {
		AccountSetUpCriteria accountSetUpCriteria = new AccountSetUpCriteria();
		boolean result = accountSetUpCriteria.checkCriteria("Test@123");
		assertTrue(result);
	}
}

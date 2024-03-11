package org.briarproject.briar.android.account.passwordCriteriaTest;

import org.briarproject.briar.android.util.AccountSetUpCriteria;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class PasswordCriteriaFailTest {
	private final String stringToUse;
	private final boolean expectedReturn;

	public PasswordCriteriaFailTest(String stringToUse, boolean expectedReturn) {
		this.stringToUse = stringToUse;
		this.expectedReturn = expectedReturn;
	}

	@Parameterized.Parameters
	public static Collection<Object[]> data() {
		return Arrays.asList(new Object[][] {
				{"TEST123", false},         //Upper Case letters and digits only
				{"Test@!#$", false},        //Upper Case, Lower Case and special letters only
				{"TestPassword", false},    //Only Upper Case and Lower Case letters
				{"TESTPASSWORD", false},    //Only Upper Case letters
				{"123456@QWE", false},      //Only Upper Case letters, special characters and digits
				{"", false},                //Empty string
				{"test@123", false}         //Only Lower Case letters, Special characters and digits
		});
	}

	// This test case is drafted to check the implementation of the checkCriteria method in AccountSetUpCriteria class.
	// This test case is to test if it returns false in the case where the string passed is missing even one of the elements
	// from theUpper Case, Lower Case, Digits or a special characters.
	@Test
	public void testPasswordCriteriaFail() {
		AccountSetUpCriteria accountSetUpCriteria = new AccountSetUpCriteria();
		boolean result = accountSetUpCriteria.checkCriteria(stringToUse);
		assertEquals(expectedReturn, result);
	}
}

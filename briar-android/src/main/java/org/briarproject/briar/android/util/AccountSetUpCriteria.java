package org.briarproject.briar.android.util;

import java.util.ArrayList;
import java.util.Arrays;

public class AccountSetUpCriteria {
	public boolean checkCriteria(String enteredPassword) {
		Character[] symbols = {'!','@','#','$','%','^','&','*','(',')','_','+','-','=','{','}','[',']',':',';','"','<','>','?','/','|'};
		ArrayList<Character> specialCharacters =
				new ArrayList<>(Arrays.asList(symbols));
		String upperCaseAlphabets = "QWERTYUIOPASDFGHJKLZXCVBNM";
		String lowerCaseAlphabets = "qwertyuiopasdfghjklzxcvbnm";
		String digits = "1234567890";
		boolean containsSymbol = false;
		boolean containsUpperCase = false;
		boolean containsLowerCase = false;
		boolean containsdigit = false;
		for (char character : enteredPassword.toCharArray()) {
			if (!containsSymbol || !containsdigit || !containsLowerCase || !containsUpperCase) {
				if (specialCharacters.contains(character)) {
					containsSymbol = true;
				}
				if (upperCaseAlphabets.contains(String.valueOf(character))) {
					containsUpperCase = true;
				}
				if (lowerCaseAlphabets.contains(String.valueOf(character))) {
					containsLowerCase = true;
				}
				if (digits.contains(String.valueOf(character))) {
					containsdigit = true;
				}
			}
		}
		if (containsSymbol && containsdigit && containsLowerCase && containsUpperCase) {
			return true;
		} else {
			return false;
		}
	}
}

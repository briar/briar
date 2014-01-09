package org.briarproject.crypto;

import java.util.HashSet;

import org.briarproject.api.crypto.PasswordStrengthEstimator;

class PasswordStrengthEstimatorImpl implements PasswordStrengthEstimator {

	private static final int LOWER = 26;
	private static final int UPPER = 26;
	private static final int DIGIT = 10;
	private static final int OTHER = 10;
	private static final double STRONG = Math.log(Math.pow(LOWER + UPPER +
			DIGIT + OTHER, 10));

	public float estimateStrength(char[] password) {
		HashSet<Character> unique = new HashSet<Character>();
		for(char c : password) unique.add(c);
		boolean lower = false, upper = false, digit = false, other = false;
		for(char c : unique) {
			if(Character.isLowerCase(c)) lower = true;
			else if(Character.isUpperCase(c)) upper = true;
			else if(Character.isDigit(c)) digit = true;
			else other = true;
		}
		int alphabetSize = 0;
		if(lower) alphabetSize += LOWER;
		if(upper) alphabetSize += UPPER;
		if(digit) alphabetSize += DIGIT;
		if(other) alphabetSize += OTHER;
		double score = Math.log(Math.pow(alphabetSize, unique.size()));
		return Math.min(1, (float) (score / STRONG));
	}
}

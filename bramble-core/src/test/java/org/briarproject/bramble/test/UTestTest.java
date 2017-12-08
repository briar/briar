package org.briarproject.bramble.test;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.briarproject.bramble.test.UTest.Result.INCONCLUSIVE;
import static org.briarproject.bramble.test.UTest.Result.LARGER;
import static org.briarproject.bramble.test.UTest.Result.SMALLER;
import static org.junit.Assert.assertEquals;

public class UTestTest extends BrambleTestCase {

	private final Random random = new Random();

	@Test
	public void testSmallerLarger() {
		// Create two samples, which may have different sizes
		int aSize = random.nextInt(1000) + 1000;
		int bSize = random.nextInt(1000) + 1000;
		List<Double> a = new ArrayList<>(aSize);
		List<Double> b = new ArrayList<>(bSize);
		// Values in b are significantly larger
		for (int i = 0; i < aSize; i++) a.add(random.nextDouble());
		for (int i = 0; i < bSize; i++) b.add(random.nextDouble() + 0.1);
		// The U test should detect that a is smaller than b
		assertEquals(SMALLER, UTest.test(a, b));
		assertEquals(LARGER, UTest.test(b, a));
	}

	@Test
	public void testSmallerLargerWithTies() {
		// Create two samples, which may have different sizes
		int aSize = random.nextInt(1000) + 1000;
		int bSize = random.nextInt(1000) + 1000;
		List<Double> a = new ArrayList<>(aSize);
		List<Double> b = new ArrayList<>(bSize);
		// Put some tied values in both samples
		addTiedValues(a, b);
		// Values in b are significantly larger
		for (int i = a.size(); i < aSize; i++) a.add(random.nextDouble());
		for (int i = b.size(); i < bSize; i++) b.add(random.nextDouble() + 0.1);
		// The U test should detect that a is smaller than b
		assertEquals(SMALLER, UTest.test(a, b));
		assertEquals(LARGER, UTest.test(b, a));
	}

	@Test
	public void testInconclusive() {
		// Create two samples, which may have different sizes
		int aSize = random.nextInt(1000) + 1000;
		int bSize = random.nextInt(1000) + 1000;
		List<Double> a = new ArrayList<>(aSize);
		List<Double> b = new ArrayList<>(bSize);
		// Values in a and b have the same distribution
		for (int i = 0; i < aSize; i++) a.add(random.nextDouble());
		for (int i = 0; i < bSize; i++) b.add(random.nextDouble());
		// The U test should not detect a difference between a and b
		assertEquals(INCONCLUSIVE, UTest.test(a, b));
		assertEquals(INCONCLUSIVE, UTest.test(b, a));
	}

	@Test
	public void testInconclusiveWithTies() {
		// Create two samples, which may have different sizes
		int aSize = random.nextInt(1000) + 1000;
		int bSize = random.nextInt(1000) + 1000;
		List<Double> a = new ArrayList<>(aSize);
		List<Double> b = new ArrayList<>(bSize);
		// Put some tied values in both samples
		addTiedValues(a, b);
		// Values in a and b have the same distribution
		for (int i = a.size(); i < aSize; i++) a.add(random.nextDouble());
		for (int i = b.size(); i < bSize; i++) b.add(random.nextDouble());
		// The U test should not detect a difference between a and b
		assertEquals(INCONCLUSIVE, UTest.test(a, b));
		assertEquals(INCONCLUSIVE, UTest.test(b, a));
	}

	private void addTiedValues(List<Double> a, List<Double> b) {
		for (int i = 0; i < 10; i++) {
			double tiedValue = random.nextDouble();
			int numTies = random.nextInt(5) + 1;
			for (int j = 0; j < numTies; j++) {
				if (random.nextBoolean()) a.add(tiedValue);
				else b.add(tiedValue);
			}
		}
	}
}

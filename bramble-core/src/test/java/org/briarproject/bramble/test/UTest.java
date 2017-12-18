package org.briarproject.bramble.test;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import static org.briarproject.bramble.test.UTest.Result.INCONCLUSIVE;
import static org.briarproject.bramble.test.UTest.Result.LARGER;
import static org.briarproject.bramble.test.UTest.Result.SMALLER;

public class UTest {

	public enum Result {

		/**
		 * The first sample has significantly smaller values than the second.
		 */
		SMALLER,

		/**
		 * There is no significant difference between the samples.
		 */
		INCONCLUSIVE,

		/**
		 * The first sample has significantly larger values than the second.
		 */
		LARGER
	}

	/**
	 * Critical z value for P = 0.01, two-tailed test.
	 */
	public static final double Z_CRITICAL_0_01 = 2.576;

	/**
	 * Critical z value for P = 0.05, two-tailed test.
	 */
	public static final double Z_CRITICAL_0_05 = 1.960;

	/**
	 * Critical z value for P = 0.1, two-tailed test.
	 */
	public static final double Z_CRITICAL_0_1 = 1.645;

	/**
	 * Performs a two-tailed Mann-Whitney U test on the given samples using the
	 * critical z value for P = 0.01.
	 * <p/>
	 * The method used here is explained at
	 * http://faculty.vassar.edu/lowry/ch11a.html
	 */
	public static Result test(List<Double> a, List<Double> b) {
		return test(a, b, Z_CRITICAL_0_01);
	}

	/**
	 * Performs a two-tailed Mann-Whitney U test on the given samples using the
	 * given critical z value.
	 * <p/>
	 * The method used here is explained at
	 * http://faculty.vassar.edu/lowry/ch11a.html
	 * <p/>
	 * Critical z values for two-tailed tests can be found at
	 * http://sphweb.bumc.bu.edu/otlt/mph-modules/bs/bs704_hypothesistest-means-proportions/bs704_hypothesistest-means-proportions3.html
	 */
	public static Result test(List<Double> a, List<Double> b,
			double zCritical) {
		int nA = a.size(), nB = b.size();
		if (nA < 5 || nB < 5)
			throw new IllegalArgumentException("Too few values for U test");

		// Sort the values, keeping track of which sample they belong to
		List<Value> sorted = new ArrayList<>(nA + nB);
		for (Double d : a) sorted.add(new Value(d, true));
		for (Double d : b) sorted.add(new Value(d, false));
		Collections.sort(sorted);

		// Assign ranks to the values
		int i = 0, size = sorted.size();
		while (i < size) {
			double value = sorted.get(i).value;
			int ties = 1;
			while (i + ties < size && sorted.get(i + ties).value == value)
				ties++;
			int bottomRank = i + 1;
			int topRank = i + ties;
			double meanRank = (bottomRank + topRank) / 2.0;
			for (int j = 0; j < ties; j++)
				sorted.get(i + j).rank = meanRank;
			i += ties;
		}

		// Calculate the total rank of each sample
		double tA = 0, tB = 0;
		for (Value v : sorted) {
			if (v.a) tA += v.rank;
			else tB += v.rank;
		}

		// The standard deviation of both total ranks is the same
		double sigma = Math.sqrt(nA * nB * (nA + nB + 1.0) / 12.0);

		// Means of the distributions of the total ranks
		double muA = nA * (nA + nB + 1.0) / 2.0;
		double muB = nB * (nA + nB + 1.0) / 2.0;

		// Calculate z scores
		double zA, zB;
		if (tA > muA) zA = (tA - muA - 0.5) / sigma;
		else zA = (tA - muA + 0.5) / sigma;
		if (tB > muB) zB = (tB - muB - 0.5) / sigma;
		else zB = (tB - muB + 0.5) / sigma;

		// Compare z scores to critical value
		if (zA > zCritical) return LARGER;
		else if (zB > zCritical) return SMALLER;
		else return INCONCLUSIVE;
	}

	public static void main(String[] args) {
		if (args.length < 2 || args.length > 3)
			die("usage: UTest <file1> <file2> [zCritical]");

		List<Double> a = readFile(args[0]);
		List<Double> b = readFile(args[1]);
		int nA = a.size(), nB = b.size();
		if (nA < 5 || nB < 5) die("Too few values for U test\n");

		double zCritical;
		if (args.length == 3) zCritical = Double.valueOf(args[2]);
		else zCritical = Z_CRITICAL_0_01;

		switch (test(a, b, zCritical)) {
			case SMALLER:
				System.out.println(args[0] + " is smaller");
				break;
			case INCONCLUSIVE:
				System.out.println("No significant difference");
				break;
			case LARGER:
				System.out.println(args[0] + " is larger");
				break;
		}
	}

	private static void die(String message) {
		System.err.println(message);
		System.exit(1);
	}

	private static List<Double> readFile(String filename) {
		List<Double> values = new ArrayList<>();
		try {
			BufferedReader in;
			in = new BufferedReader(new FileReader(filename));
			String s;
			while ((s = in.readLine()) != null) values.add(new Double(s));
			in.close();
		} catch (FileNotFoundException fnf) {
			die(filename + " not found");
		} catch (IOException io) {
			die("Error reading from " + filename);
		} catch (NumberFormatException nf) {
			die("Invalid data in " + filename);
		}
		return values;
	}

	private static class Value implements Comparable<Value> {

		private final double value;
		private final boolean a;

		private double rank;

		private Value(double value, boolean a) {
			this.value = value;
			this.a = a;
		}

		@Override
		public int compareTo(@Nonnull Value v) {
			if (value < v.value) return -1;
			if (value > v.value) return 1;
			return 0;
		}
	}
}

package org.briarproject.bramble.db;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.system.SystemClock;
import org.briarproject.bramble.test.TestDatabaseConfig;
import org.briarproject.bramble.test.UTest;

import java.io.IOException;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import static org.briarproject.bramble.test.TestUtils.deleteTestDirectory;
import static org.briarproject.bramble.test.TestUtils.getMean;
import static org.briarproject.bramble.test.TestUtils.getMedian;
import static org.briarproject.bramble.test.TestUtils.getSecretKey;
import static org.briarproject.bramble.test.TestUtils.getStandardDeviation;
import static org.briarproject.bramble.test.UTest.Z_CRITICAL_0_01;

public abstract class DatabasePerformanceComparisonTest
		extends DatabasePerformanceTest {

	/**
	 * How many blocks of each condition to compare.
	 */
	private static final int COMPARISON_BLOCKS = 10;
	private SecretKey databaseKey = getSecretKey();

	abstract Database<Connection> createDatabase(boolean conditionA,
			DatabaseConfig databaseConfig, Clock clock);

	@Override
	protected void benchmark(String name,
			BenchmarkTask<Database<Connection>> task) throws Exception {
		List<Double> aDurations = new ArrayList<>();
		List<Double> bDurations = new ArrayList<>();
		boolean aFirst = true;
		for (int i = 0; i < COMPARISON_BLOCKS; i++) {
			// Alternate between running the A and B benchmarks first
			if (aFirst) {
				aDurations.addAll(benchmark(true, task).durations);
				bDurations.addAll(benchmark(false, task).durations);
			} else {
				bDurations.addAll(benchmark(false, task).durations);
				aDurations.addAll(benchmark(true, task).durations);
			}
			aFirst = !aFirst;
		}
		// Compare the results using a small P value, which increases our
		// chance of getting an inconclusive result, making this a conservative
		// test for performance differences
		UTest.Result comparison = UTest.test(aDurations, bDurations,
				Z_CRITICAL_0_01);
		writeResult(name, aDurations, bDurations, comparison);
	}

	private SteadyStateResult benchmark(boolean conditionA,
			BenchmarkTask<Database<Connection>> task) throws Exception {
		deleteTestDirectory(testDir);
		Database<Connection> db = openDatabase(conditionA);
		populateDatabase(db);
		db.close();
		db = openDatabase(conditionA);
		// Measure blocks of iterations until we reach a steady state
		SteadyStateResult result = measureSteadyState(db, task);
		db.close();
		return result;
	}

	private Database<Connection> openDatabase(boolean conditionA)
			throws DbException {
		Database<Connection> db = createDatabase(conditionA,
				new TestDatabaseConfig(testDir), new SystemClock());
		db.open(databaseKey, null);
		return db;
	}

	private void writeResult(String name, List<Double> aDurations,
			List<Double> bDurations, UTest.Result comparison)
			throws IOException {
		String result = String.format("%s\t%,d\t%,d\t%,d\t%,d\t%,d\t%,d\t%s",
				name, (long) getMean(aDurations), (long) getMedian(aDurations),
				(long) getStandardDeviation(aDurations),
				(long) getMean(bDurations), (long) getMedian(bDurations),
				(long) getStandardDeviation(bDurations),
				comparison.name());
		writeResult(result);
	}
}

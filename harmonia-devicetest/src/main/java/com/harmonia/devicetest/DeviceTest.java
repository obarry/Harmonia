package com.harmonia.devicetest;

/**
 * An interactive test run with a real Wavestation SR: Harmonia sends and reads, the tester looks
 * at the device and answers questions. Results go to the report through {@link TestContext}.
 */
public interface DeviceTest {

    /** Short identifier, e.g. "T02". */
    String id();

    /** Title, e.g. "Sélection des performances". */
    String title();

    /** What the test verifies and why (one or two sentences, shown before running it). */
    String purpose();

    /** Runs the test. Exceptions are recorded as errors by the runner. */
    void run(TestContext ctx) throws Exception;
}

package synoptic.tests.units;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import synoptic.invariants.CExamplePath;
import synoptic.invariants.fsmcheck.TracingStateSet;
import synoptic.model.Partition;
import synoptic.tests.PynopticTest;
import synoptic.util.time.ITime;
import synoptic.util.time.ITotalTime;

/**
 * Tests for finding counter-example paths using TracingStateSets for
 * constrained temporal invariants
 * 
 * @author Tony Ohmann (ohmann@cs.umass.edu)
 */
public class ConstrainedTracingSetTests extends PynopticTest {

    // Events for AP tests. Traces 1 and 2 are common, and trace 3 causes there
    // to be no "x AFby z" invariant to ensure that AP tracing sets are truly
    // modeling AP FSMs
    protected String[] eventsAP = { "x 0", "y 11", "z 71", "w 72", "--",
            "x 100", "y 160", "z 171", "w 172", "--", "x 200", "u 201" };

    // Events for AFby tests. Traces 1 and 2 are common, and trace 3 causes
    // there to be no "x AP z" invariant to ensure that AFby tracing sets are
    // truly modeling AFby FSMs
    protected String[] eventsAFby = { "x 0", "y 11", "z 71", "w 72", "--",
            "x 100", "y 160", "z 171", "w 172", "--", "v 200", "z 201", "w 202" };

    /**
     * Get partitions corresponding to the A and B predicates of the current
     * constrained invariant and the terminal partition. This method is only
     * guaranteed to give _some_ partition with the label of A and of B, so
     * behavior is not guaranteed if there exists more than one A or B partition
     * in the partition graph.
     * 
     * @return 3-element array of partitions: [0] is A, [1] is B, [2] is
     *         terminal
     */
    private Partition[] getPartitions() {

        Partition[] partitions = new Partition[3];

        // Get partition of first invariant predicate ("A")
        for (Partition part : graph.getNodes()) {
            if (part.getEType().equals(inv.getFirst())) {
                partitions[0] = part;
                break;
            }
        }

        // Get partition of second invariant predicate ("B")
        for (Partition part : graph.getNodes()) {
            if (part.getEType().equals(inv.getSecond())) {
                partitions[1] = part;
                break;
            }
        }

        // Get terminal partition
        for (Partition part : graph.getNodes()) {
            if (part.isTerminal()) {
                partitions[2] = part;
                break;
            }
        }

        return partitions;
    }

    /**
     * Check that APUpperTracingSet reaches a failure state when (and only when)
     * the time constraint is violated
     */
    @Test
    public void APUpperFailureStateTest() throws Exception {

        // Get tracing sets and partitions corresponding to A and B events
        Map<Partition, TracingStateSet<Partition>> tracingSets = genConstrTracingSets(
                eventsAP, "x AP z upper", TracingSet.APUpper);
        Partition[] partitions = getPartitions();

        // State machine should be at an accept state at partition 'x'
        assertFalse(tracingSets.get(partitions[0]).isFail());

        // State machine should be at a failure state at partition 'z'
        assertTrue(tracingSets.get(partitions[1]).isFail());
    }

    /**
     * Check that AFbyUpperTracingSet reaches a failure state when (and only
     * when) the time constraint is violated
     */
    @Test
    public void AFbyUpperFailureStateTest() throws Exception {

        // Get tracing sets and partitions corresponding to A and B events
        Map<Partition, TracingStateSet<Partition>> tracingSets = genConstrTracingSets(
                eventsAFby, "x AFby z upper", TracingSet.AFbyUpper);
        Partition[] partitions = getPartitions();

        // State machine should be at an accept state at partition 'x'
        assertFalse(tracingSets.get(partitions[0]).isFail());

        // State machine should be at a failure state at partition 'z'
        assertTrue(tracingSets.get(partitions[1]).isFail());
    }

    /**
     * Check that APUpperTracingSet returns the correct counter-example path
     * when the time constraint is violated
     */
    @Test
    public void APUpperCounterExamplePathTest() throws Exception {

        // Get tracing sets and partitions corresponding to A and B events and
        // terminal
        Map<Partition, TracingStateSet<Partition>> tracingSets = genConstrTracingSets(
                eventsAP, "x AP z upper", TracingSet.APUpper);
        Partition[] partitions = getPartitions();

        // Get counter-example path at the terminal partition
        CExamplePath<Partition> cExPath = tracingSets.get(partitions[2])
                .failpath().toCounterexample(inv);

        // Shorter variable aliases for cleaner assertions below
        List<Partition> path = cExPath.path;
        int vStart = cExPath.violationStart;
        int vEnd = cExPath.violationEnd;

        // Counter-example path should be (INIT -> x -> y -> z -> w -> TERM)
        assertTrue(path.size() == 6);

        // Violation subpath should start at 'x' and end at 'z'
        assertTrue(path.get(vStart).equals(partitions[0]));
        assertTrue(path.get(vEnd).equals(partitions[1]));

        // Violation subpath should be two abstract transitions long
        // (x -> y -> z)
        assertTrue(vEnd - vStart == 2);

        // Counter-example path should store time at the end of violation
        // subpath as 120 after
        // taking all max time transitions ( x --60--> y --60--> z )
        ITime t120 = new ITotalTime(120);
        assertTrue(cExPath.tDeltas.get(vEnd).compareTo(t120) == 0);
    }

    /**
     * Check that AFbyUpperTracingSet returns the correct counter-example path
     * when the time constraint is violated
     */
    @Test
    public void AFbyUpperCounterExamplePathTest() throws Exception {

        // Get tracing sets and partitions corresponding to A and B events and
        // terminal
        Map<Partition, TracingStateSet<Partition>> tracingSets = genConstrTracingSets(
                eventsAFby, "x AFby z upper", TracingSet.AFbyUpper);
        Partition[] partitions = getPartitions();

        // Get counter-example path at the terminal partition
        CExamplePath<Partition> cExPath = tracingSets.get(partitions[2])
                .failpath().toCounterexample(inv);

        // Shorter variable aliases for cleaner assertions below
        List<Partition> path = cExPath.path;
        int vStart = cExPath.violationStart;
        int vEnd = cExPath.violationEnd;

        // Counter-example path should be (INIT -> x -> y -> z -> w -> TERM)
        assertTrue(path.size() == 6);

        // Violation subpath should start at 'x' and end at 'z'
        assertTrue(path.get(vStart).equals(partitions[0]));
        assertTrue(path.get(vEnd).equals(partitions[1]));

        // Violation subpath should be two abstract transitions long
        // (x -> y -> z)
        assertTrue(vEnd - vStart == 2);

        // Counter-example path should store time at the end of violation
        // subpath as 120 after
        // taking all max time transitions ( x --60--> y --60--> z )
        ITime t120 = new ITotalTime(120);
        assertTrue(cExPath.tDeltas.get(vEnd).compareTo(t120) == 0);
    }
}

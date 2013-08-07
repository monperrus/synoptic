package synoptic.tests.integration;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import junit.framework.Assert;

import synoptic.main.SynopticMain;
import synoptic.main.parser.ParseException;
import synoptic.tests.SynopticTest;

/**
 * Tests main with a variety of known input log files from the traces/
 * directory. The test oracle is successful execution -- no crashes.
 * 
 * <pre>
 * TODO: Extend the oracle to expected output, which can be
 *       pre-generated for all the tested log files.
 * </pre>
 */
@RunWith(value = Parameterized.class)
public class EndToEndMainTests extends SynopticTest {
    String[] args;

    @Override
    public void setUp() throws ParseException {
        // Do not call to super, so that a new Synoptic Main is not allocated.
        return;
    }

    @Parameters
    public static Collection<Object[]> data() {
        String tracesBasePath = File.separator + "traces" + File.separator
                + "EndToEndTests" + File.separator;
        // Two paths that we will test to find traces/args files.
        List<String> possibleTracesPaths = Arrays.asList(new String[] {
                "." + tracesBasePath, ".." + tracesBasePath });

        Collection<Object[]> argsList = new LinkedList<Object[]>();

        // List of input sub-dirs that contains end-to-end test examples.
        String[] testPaths = { "mid_branching", "osx-login-example",
                "shopping-cart-example", "ticket-reservation-example" };

        // Determine where the input traces/args are located -- try two options:
        String tracesPath = findWorkingPath(possibleTracesPaths, testPaths[0]
                + File.separator + "args.txt");

        // Compose a set of args to Synoptic for each end-to-end test case.
        for (String tPath : testPaths) {
            // Check that the specific input files for this test exists.
            String argsFilename = tracesPath + tPath + File.separator
                    + "args.txt";
            String traceFilename = tracesPath + tPath + File.separator
                    + "trace.txt";
            File f1 = new File(argsFilename);
            File f2 = new File(traceFilename);
            if (!f1.exists() || !f2.exists()) {
                Assert.fail("Unable to find trace/argument inputs for EndtoEndMainTest with tracesPath="
                        + tracesPath);
                return argsList;
            }

            String outputPrefix = testOutputDir + tPath;
            Object[] testCase = { new String[] { "-o", outputPrefix, "-c",
                    argsFilename, traceFilename } };
            argsList.add(testCase);
        }

        return argsList;
    }

    public EndToEndMainTests(String... argsParam) {
        this.args = argsParam;
    }

    @Test
    public void mainTest() throws Exception {
        SynopticMain.instance = null;
        SynopticMain.main(args);
    }

}

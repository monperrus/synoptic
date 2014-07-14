/*
 * This code is in part based on the code from Clemens Hammacher's
 * implementation of a partition refinement algorithm for Bisimulation
 * minimization.
 * 
 * Source: https://ccs.hammacher.name
 * 
 * License: Eclipse Public License v1.0.
 */

package synoptic.algorithms.bisim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import synoptic.algorithms.graph.IOperation;
import synoptic.algorithms.graph.PartitionMerge;
import synoptic.algorithms.graph.PartitionMultiSplit;
import synoptic.algorithms.graph.PartitionSplit;
import synoptic.benchmarks.PerformanceMetrics;
import synoptic.benchmarks.TimedTask;
import synoptic.invariants.CExamplePath;
import synoptic.invariants.ITemporalInvariant;
import synoptic.invariants.TemporalInvariantSet;
import synoptic.main.Main;
import synoptic.model.ChainsTraceGraph;
import synoptic.model.EventNode;
import synoptic.model.Partition;
import synoptic.model.PartitionGraph;
import synoptic.model.interfaces.ITransition;
import synoptic.util.InternalSynopticException;

/**
 * This class implements the algorithm BisimH (
 * {@code Bisimulation.splitPartitions}), and a modified version of the
 * algorithm kTail ({@code Bisimulation.mergePartitions}) that considers state
 * labels instead of state transitions.
 */
public abstract class Bisimulation {
    public static Logger logger = Logger.getLogger("Bisimulation");
    /**
     * If true, a partition may be split on incoming/outgoing edges at once, if
     * both splits resolve the invariant (when considered separately). TODO:
     * implement this
     */
    // private static final boolean fourWaySplit = true;
    /**
     * Set to combine all candidate splits for each partition into a multi
     * split.
     */
    private static final boolean combineCandidates = false;
    /**
     * Consider incoming transitions for splitting TODO: expose this as a
     * command line option
     */
    private static boolean incomingTransitionSplit = true;
    /**
     * Coarsen the representation after each successful split (i.e. each split
     * that caused a previously unsatisfied invariant to become satisfied. This
     * may or may not speed up operation.
     */
    private static boolean interleavedMerging = false;

    private Bisimulation() throws InstantiationException {
        throw new InstantiationException(this.getClass().getCanonicalName()
                + " is supposed to be used as static class only.");
    }

    /**
     * Attempts to perform the splitOp on the pGraph to see whether or not the
     * resulting graph has no other counter-examples for the invariant inv (i.e.
     * whether or not the graph satisfies inv).
     * 
     * @param inv
     *            The invariant to check for satisfiability after the splitOp.
     * @param pGraph
     *            The partition graph to apply to the splitOp to.
     * @param splitsToDoByPartition
     *            The HashMap recording splits by partition -- will be updated
     *            if the split makes the graph satisfy the invariant.
     * @param partitionBeingSplit
     *            The partition being split by splitOp.
     * @param splitOp
     *            The split operation to apply to pGraph
     * @return true if the split makes the graph satisfy the invariant, and
     *         false otherwise.
     */
    public static boolean tryAndRecordSplitOp(ITemporalInvariant inv,
            PartitionGraph pGraph,
            HashMap<Partition, PartitionMultiSplit> splitsToDoByPartition,
            Partition partitionBeingSplit, PartitionMultiSplit splitOp) {

        // Perform the split.
        IOperation rewindOperation = pGraph.apply(splitOp);

        // See if splitting resolved the violation.
        CExamplePath<Partition> violation = TemporalInvariantSet
                .getCounterExample(inv, pGraph);

        // Undo the split (rewind) to get back the input graph.
        pGraph.apply(rewindOperation);

        // The invariant has more violations after the split.
        if (violation != null) {
            return false;
        }

        // Otherwise, there are no more violations -- we made progress
        if (splitsToDoByPartition.containsKey(partitionBeingSplit)) {
            // If we already have a split for that partition,
            // incorporate the new split into it.
            splitsToDoByPartition.get(partitionBeingSplit).incorporate(splitOp);
            logger.info("Incorporating new split by partition: "
                    + splitOp.toString());
        } else {
            // Otherwise, record this split as the only one for this partition
            splitsToDoByPartition.put(partitionBeingSplit, splitOp);
            logger.info("New split by partition: " + splitOp.toString());
        }
        // The split has no other violations once the split is performed.
        return true;
    }

    private static IOperation tryAndRecordCandidateSplits(
            List<CExamplePath<Partition>> counterexampleTraces,
            PartitionGraph pGraph,
            HashMap<Partition, PartitionMultiSplit> splitsToDoByPartition,
            Set<ITemporalInvariant> newlySatisfiedInvariants) {

        IOperation arbitrarySplit = null;

        // TODO: we are considering counter-example traces in an arbitrary order
        // there's probably room for a heuristic here.
        for (CExamplePath<Partition> counterexampleTrace : counterexampleTraces) {

            logger.fine("Considering counterexample: "
                    + counterexampleTrace.toString());

            // Get the possible splits that might resolve this counter-example.
            List<PartitionSplit> candidateSplits = getSplits(
                    counterexampleTrace, pGraph);

            // Permute the list of candidates.
            Collections.shuffle(candidateSplits, Main.random);

            logger.fine("candidateSplits are: " + candidateSplits.toString());

            if (combineCandidates) {
                PartitionMultiSplit combinedSplit = null;

                for (PartitionSplit candidateSplit : candidateSplits) {
                    if (candidateSplit == null || !candidateSplit.isValid()) {
                        logger.fine("Skipping invalid source: "
                                + candidateSplit);
                        continue;
                    }

                    if (combinedSplit == null) {
                        combinedSplit = new PartitionMultiSplit(candidateSplit);
                    } else {
                        combinedSplit.incorporate(candidateSplit);
                    }
                }

                if (combinedSplit == null) {
                    logger.fine("No valid sources available.");
                    return null;
                }
                if (!combinedSplit.isValid()) {
                    logger.fine("Combined split is invalid.");
                    return null;
                }

                if (arbitrarySplit == null) {
                    arbitrarySplit = combinedSplit;
                }

                if (tryAndRecordSplitOp(counterexampleTrace.invariant, pGraph,
                        splitsToDoByPartition, combinedSplit.getPartition(),
                        combinedSplit)) {
                    // Remember that we can resolve this invariant violation.
                    newlySatisfiedInvariants.add(counterexampleTrace.invariant);
                    logger.fine("newlySatInvariants: "
                            + newlySatisfiedInvariants.toString());
                }

            } else {
                // TODO: why is this a loop, and not just a single choice of
                // candidate split?

                // (mostly performance, but also sub-optimality) BUG: I think I
                // understand -- this is
                // used to try all
                // potential
                // splits in the case that one of them makes the invariant true.
                // BUT, we don't need to incorporate multiple ones if they both
                // result in making the invariant true!

                for (PartitionSplit candidateSplit : candidateSplits) {
                    if (Main.options.performExtraChecks) {
                        // getSplits() should never generate invalid splits.
                        if (!candidateSplit.isValid()) {
                            throw new InternalSynopticException(
                                    "getSplits() generated an invalid split.");
                        }
                    }

                    if (arbitrarySplit == null) {
                        arbitrarySplit = candidateSplit;
                    }

                    if (tryAndRecordSplitOp(counterexampleTrace.invariant,
                            pGraph, splitsToDoByPartition,
                            candidateSplit.getPartition(),
                            new PartitionMultiSplit(candidateSplit))) {
                        // Remember that we can resolve this invariant
                        // violation.
                        newlySatisfiedInvariants
                                .add(counterexampleTrace.invariant);
                    }
                }
            }
        }
        return arbitrarySplit;
    }

    public static int performOneSplitPartitionsStep(int numSplitSteps,
            PartitionGraph pGraph,
            List<CExamplePath<Partition>> counterExampleTraces) {

        // Stores all splits that cause an invariant to be satisfied, indexed by
        // partition to which they are applied.
        LinkedHashMap<Partition, PartitionMultiSplit> splitsToDoByPartition = new LinkedHashMap<Partition, PartitionMultiSplit>();

        // If we have no counterexamples, then we are done.
        if (counterExampleTraces == null || counterExampleTraces.size() == 0) {
            return numSplitSteps;
        }

        // Permute the counter-examples, but do so deterministically for the
        // same random seed argument.
        Collections.shuffle(counterExampleTraces, Main.random);

        logger.fine("" + counterExampleTraces.size()
                + " unsatisfied invariants and counter-examples: "
                + counterExampleTraces);

        // The set of all invariants for which we have a split that makes the
        // graph satisfy the invariant.
        LinkedHashSet<ITemporalInvariant> newlySatisfiedInvariants = new LinkedHashSet<ITemporalInvariant>();

        // Contains the first valid split, which will be performed if no other
        // split (that would resolve an invariant) is available.
        IOperation arbitrarySplit;

        arbitrarySplit = tryAndRecordCandidateSplits(counterExampleTraces,
                pGraph, splitsToDoByPartition, newlySatisfiedInvariants);

        String logStr = "";
        if (splitsToDoByPartition.size() == 0) {
            // We have no splits that resolve invariants. See if we have an
            // arbitrary split.
            if (arbitrarySplit == null) {
                logger.fine("no valid split available, exiting.");
                return numSplitSteps;
            }
            logStr += "split[" + numSplitSteps + "] : arbitrary split: "
                    + arbitrarySplit;

            pGraph.apply(arbitrarySplit);

        } else {
            // We have splits that resolve invariants, perform them.
            int i = 0;
            for (PartitionMultiSplit currentSplit : splitsToDoByPartition
                    .values()) {
                pGraph.apply(currentSplit);
                logger.fine("split[" + numSplitSteps + "." + i + "] : "
                        + currentSplit);
                i++;
            }

            // Handle interleaved merging.
            // if (interleavedMerging) {
            // logger.fine("interleavedMerging: recompressing...");
            // Bisimulation.mergePartitions(pGraph,
            // new TemporalInvariantSet(satisfiedInvariants));
            // }

            logStr += "split[" + numSplitSteps + "] " + "new invs satisfied: "
                    + newlySatisfiedInvariants.size();
        }

        logger.fine(logStr);

        if (Main.options.dumpIntermediateStages) {
            Main.exportNonInitialGraph(
                    Main.getIntermediateDumpFilename("r", numSplitSteps + 1),
                    pGraph);
        }

        return numSplitSteps + 1;

    }

    /**
     * Splits the partitions in {@code pGraph} until all synoptic.invariants
     * returned by {@code pGraph.getInvariants()} are satisfied.
     * 
     * @param pGraph
     *            the partition graph to refine\split
     */
    public static void splitPartitions(PartitionGraph pGraph) {
        // TODO: assert that the pGraph represents totally ordered traces.

        TimedTask refinement = PerformanceMetrics.createTask("refinement",
                false);

        if (Main.options.dumpIntermediateStages) {
            Main.exportNonInitialGraph(
                    Main.getIntermediateDumpFilename("r", 0), pGraph);
        }

        int numSplitSteps = 0;
        int prevNumSplitSteps = 0;

        Set<ITemporalInvariant> unsatisfiedInvariants = new LinkedHashSet<ITemporalInvariant>();
        unsatisfiedInvariants.addAll(pGraph.getInvariants().getSet());
        Set<ITemporalInvariant> satisfiedInvariants = new LinkedHashSet<ITemporalInvariant>();

        List<CExamplePath<Partition>> counterExampleTraces = null;

        while (true) {
            // Recompute the counter-examples for the unsatisfied invariants.
            counterExampleTraces = new TemporalInvariantSet(
                    unsatisfiedInvariants).getAllCounterExamples(pGraph);

            if (counterExampleTraces == null
                    || counterExampleTraces.size() == 0) {
                logger.fine("Invariants satisfied. Stopping.");
                break;
            }

            // /////////
            // Update the sets with satisfied/unsatisfied invariants.

            // NOTE: By performing a split we might satisfy more than just the
            // invariant the split was intended to satisfy. Therefore, by just
            // considering these invariants we would be under-approximating the
            // invariants we've satisfied, and over-approximating unsatisfied
            // invariants. Instead, we re-compute counter-examples AFTER all the
            // splits and rely on these for determining the set of
            // satisfied/unsatisfied invariants.

            unsatisfiedInvariants.clear();
            for (CExamplePath<Partition> relPath : counterExampleTraces) {
                unsatisfiedInvariants.add(relPath.invariant);
            }
            satisfiedInvariants.clear();
            satisfiedInvariants.addAll(pGraph.getInvariants().getSet());
            satisfiedInvariants.removeAll(unsatisfiedInvariants);
            // /////////
            logger.fine("New graph size: " + pGraph.getNodes().size()
                    + ", unsat invs remaining: " + unsatisfiedInvariants.size());

            // Perform the splitting.
            prevNumSplitSteps = numSplitSteps;
            numSplitSteps = performOneSplitPartitionsStep(numSplitSteps,
                    pGraph, counterExampleTraces);

            if (numSplitSteps == prevNumSplitSteps) {
                // No splits were performed, which means that we could not
                // eliminate the present counter-examples. Since this function
                // should only be applied to totally ordered traces, this is a
                // bug (this is known to be possible for partially ordered
                // traces).

                throw new InternalSynopticException(
                        "Could not satisfy invariants: "
                                + unsatisfiedInvariants);
            }

        }

        if (Main.options.dumpIntermediateStages) {
            Main.exportNonInitialGraph(
                    Main.getIntermediateDumpFilename("r", numSplitSteps),
                    pGraph);
        }

        PerformanceMetrics.get().record("numOfSplitSteps", numSplitSteps);
        refinement.stop();
    }

    /**
     * Compute possible splits to remove the path counterexampleTrace from
     * pGraph. This is done by following the path in the original (event) graph
     * and determining the point where the partition graph allows a transition
     * it should not allow. The event graph is accessed via the events stored by
     * the partitions.
     * 
     * @param counterexampleTrace
     *            - the path to remove
     * @param pGraph
     *            - the graph from which the path shall be removed
     * @return a list of partition splits that remove relPath
     */
    private static List<PartitionSplit> getSplits(
            CExamplePath<Partition> counterexampleTrace, PartitionGraph pGraph) {
        /**
         * Holds the return values.
         */
        List<PartitionSplit> candidateSplits = new ArrayList<PartitionSplit>();
        /**
         * The messages (i.e. nodes in the original graph) that are on the
         * counterexampleTrace.
         */
        LinkedHashSet<EventNode> hot = new LinkedHashSet<EventNode>();
        hot.addAll(counterexampleTrace.path.get(0).getEventNodes());
        Partition prevPartition = null;
        Partition nextPartition = null;
        Partition curPartition = null;
        // logger.fine("" + counterexampleTrace.path);
        // Walk along the path
        for (Partition part : counterexampleTrace.path) {
            if (part == null) {
                throw new InternalSynopticException(
                        "Relation path with a null Partition");
            }
            prevPartition = curPartition;
            curPartition = nextPartition;
            nextPartition = part;
            hot.retainAll(part.getEventNodes());
            // If we cannot follow further, then we found the partition we need
            // to split.
            if (hot.size() == 0) {
                break;
            }
            // Compute the valid successor messages in the original trace.
            LinkedHashSet<EventNode> successorEvents = new LinkedHashSet<EventNode>();
            String relation = counterexampleTrace.invariant.getRelation();
            for (EventNode m : hot) {
                successorEvents.addAll(m.getSuccessors(relation));
            }
            hot = successorEvents;
        }
        ITransition<Partition> outgoingTransition = curPartition.getTransition(
                nextPartition, counterexampleTrace.invariant.getRelation());
        ITransition<Partition> incomingTransition = null;
        if (prevPartition != null) {
            incomingTransition = prevPartition.getTransition(curPartition,
                    counterexampleTrace.invariant.getRelation());
        }
        if (outgoingTransition != null) {
            // logger.fine("outgoingTrans:" + outgoingTransition);
            PartitionSplit newSplit = curPartition
                    .getCandidateSplitBasedOnOutgoing(outgoingTransition);
            // logger.fine("outgoingSplit:" + newSplit);
            if (newSplit != null) {
                candidateSplits.add(newSplit);
            }

        }
        if (incomingTransition != null && incomingTransitionSplit) {
            // logger.fine("incomingTrans:" + incomingTransition);
            PartitionSplit newSplit = curPartition
                    .getCandidateSplitBasedOnIncoming(prevPartition,
                            incomingTransition.getRelation());
            // logger.fine("incomingSplit:" + newSplit);
            if (newSplit != null) {
                candidateSplits.add(newSplit);
            }
        }
        return candidateSplits;
    }

    /**
     * Construct a partition graph from {@code graph} (by partitioning by
     * label), calls {@code splitPartitions} on it, and returns the refined
     * graph.
     * 
     * @param graph
     *            the graph from which should be used as initial graph
     * @return the refined graph
     */
    public static PartitionGraph getSplitGraph(ChainsTraceGraph graph,
            TemporalInvariantSet invariants) {
        PartitionGraph g = new PartitionGraph(graph, true, invariants);
        splitPartitions(g);
        return g;
    }

    /**
     * Works as {@code mergePartitions} but fixes synoptic.invariants to
     * {@code pGraph.getInvariants()}
     * 
     * @param pGraph
     */
    public static void mergePartitions(PartitionGraph pGraph) {
        TemporalInvariantSet invariants = pGraph.getInvariants();
        mergePartitions(pGraph, invariants);
    }

    /**
     * Works as {@code mergePartitions} but fixes k to 0.
     */
    public static void mergePartitions(PartitionGraph pGraph,
            TemporalInvariantSet invariants) {
        mergePartitions(pGraph, invariants, 0);
    }

    /**
     * Wrapper to mergePartitions without invariant preservation.
     * 
     * @param pGraph
     *            the graph to reduce (merge {@code k}-equivalent partitions)
     * @param k
     *            parameter for equality
     */
    public static void kReduce(PartitionGraph pGraph, int k) {
        mergePartitions(pGraph, null, k);
    }

    /**
     * Merge partitions if they are {@code k} equal, while maintaining
     * {@code synoptic.invariants}
     * 
     * @param pGraph
     *            the graph to coarsen
     * @param invariants
     *            the invariants to maintain during merge, can be null
     * @param k
     *            the k parameter for k-equality
     */
    private static void mergePartitions(PartitionGraph pGraph,
            TemporalInvariantSet invariants, int k) {
        TimedTask coarsening = PerformanceMetrics.createTask("coarsening",
                false);
        int outerItters = 0;
        int numAttemptedMerges = 0;

        // The blacklist keeps a history of partitions we've attempted to merge
        // and
        // which did not work out because they resulted in invariant violations.
        LinkedHashMap<Partition, LinkedHashSet<Partition>> blacklist = new LinkedHashMap<Partition, LinkedHashSet<Partition>>();
        outerWhile:
        while (true) {

            if (Main.options.dumpIntermediateStages) {
                Main.exportNonInitialGraph(
                        Main.getIntermediateDumpFilename("c", outerItters),
                        pGraph);
            }
            outerItters++;

            ArrayList<Partition> partitions = new ArrayList<Partition>();
            partitions.addAll(pGraph.getNodes());

            // Attempt to merge all pairs of partitions in the current graph.
            for (Partition p : partitions) {
                for (Partition q : partitions) {
                    // 1. Can't merge a partition with itself
                    if (p == q) {
                        continue;
                    }

                    // 2. Only merge partitions that are k-equivalent
                    if (!KTails.kEquals(p, q, k, false)) {
                        continue;
                    }

                    // 3. Ignore partition pairs that were previously tried (are
                    // in blacklist)
                    if (blacklist.containsKey(p)
                            && blacklist.get(p).contains(q)
                            || blacklist.containsKey(q)
                            && blacklist.get(q).contains(p)) {
                        continue;
                    }

                    logger.fine("Attempting merge: " + p + " + " + q);

                    Set<Partition> parts = new LinkedHashSet<Partition>();
                    parts.addAll(pGraph.getNodes());
                    IOperation rewindOperation = pGraph
                            .apply(new PartitionMerge(p, q));
                    numAttemptedMerges++;

                    CExamplePath<Partition> vio = null;

                    if (invariants != null) {
                        vio = invariants.getFirstCounterExample(pGraph);
                    }

                    if (vio != null) {
                        // The merge created a violation. Remember this pair of
                        // partitions so that we don't try it again.
                        if (!blacklist.containsKey(p)) {
                            blacklist.put(p, new LinkedHashSet<Partition>());
                        }
                        blacklist.get(p).add(q);
                        // Undo the merge.
                        pGraph.apply(rewindOperation);

                        if (Main.options.performExtraChecks) {
                            pGraph.checkSanity();
                        }

                        // We cannot change the partition sets because we are
                        // iterating over the partitions. Therefore, check that
                        // the resulting partition set is the same as the
                        // original partition set.
                        if (!(parts.containsAll(pGraph.getNodes()) && pGraph
                                .getNodes().containsAll(parts))) {
                            throw new InternalSynopticException(
                                    "partition set changed due to rewind: "
                                            + rewindOperation);
                        }

                    } else {
                        logger.fine("Merge maintains invs, continuing");
                        continue outerWhile;
                    }
                }
            }

            // Unable to find any k-equivalent partitions; we're done.
            break;
        }

        if (Main.options.dumpIntermediateStages) {
            Main.exportNonInitialGraph(
                    Main.getIntermediateDumpFilename("c", outerItters), pGraph);
        }

        PerformanceMetrics.get().record("numOfMergeSteps", numAttemptedMerges);
        coarsening.stop();
    }
}
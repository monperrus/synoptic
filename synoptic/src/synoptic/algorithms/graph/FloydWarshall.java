package synoptic.algorithms.graph;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import synoptic.model.EventNode;
import synoptic.model.interfaces.IGraph;
import synoptic.model.interfaces.ITransition;

public class FloydWarshall {
    /**
     * Warshall's Algorithm.
     * 
     * <pre>
     * TODO: this algorithm cannot be used for invariant
     * mining as is because it includes the TERMINAL node in the computation of
     * the transitive closure, whereas the algorithms assume that this node is
     * not present.
     * </pre>
     */
    public static TransitiveClosure warshallAlg(IGraph<EventNode> graph,
            String relation) {
        Set<EventNode> allNodes = graph.getNodes();

        TransitiveClosure transClosure = new TransitiveClosure(relation);
        Map<EventNode, Set<EventNode>> tc = transClosure.getTC();

        // Maps a node to its parents in the transitive closure.
        HashMap<EventNode, HashSet<EventNode>> tcParents = new HashMap<EventNode, HashSet<EventNode>>();

        // Logger logger = Logger.getLogger("TransitiveClosure Logger");
        for (EventNode m : allNodes) {
            if (m.getEType().isSpecialEventType()) {
                continue;
            }

            // logger.fine("tc map is: " + tc.toString());
            // logger.fine("Handling node " + m.toString());
            Iterator<? extends ITransition<EventNode>> transIter = m
                    .getTransitionsIterator(relation);
            /**
             * Iterate through all children of m and for each child do 2 things:
             * 
             * <pre>
             * 1. create a tc link between m and child and add m to tcParents[child]
             * 2. create a tc link between m and every node n to which child is already
             *    linked to in tc and add m to tcParents[n]
             * </pre>
             */
            while (transIter.hasNext()) {
                // Create new tc map for node m.
                if (!tc.containsKey(m)) {
                    tc.put(m, new LinkedHashSet<EventNode>());
                }

                EventNode child = transIter.next().getTarget();

                if (!tcParents.containsKey(child)) {
                    tcParents.put(child, new HashSet<EventNode>());
                }

                // Link m to c
                tc.get(m).add(child);
                tcParents.get(child).add(m);

                // Link m to all nodes that c is linked to in tc
                if (tc.containsKey(child)) {
                    // m can reach nodes the child can reach transitively:
                    tc.get(m).addAll(tc.get(child));
                    // nodes that child can reach have m as a tc parent:
                    for (EventNode n : tc.get(child)) {
                        if (!tcParents.containsKey(n)) {
                            tcParents.put(n, new HashSet<EventNode>());
                        }
                        tcParents.get(n).add(m);
                    }
                }
            }

            /**
             * Now that we're done compiling the downward transitive closure of
             * m, its time to push that information to m's parent nodes. For
             * each tc parent p of m we do 2 things:
             * 
             * <pre>
             * 1. For each node n in tc of m, add tc link between p and n
             * 2. For each node n in tc of m, add p to tcParents[n]
             * </pre>
             */
            if (tcParents.containsKey(m) && tc.containsKey(m)) {
                for (EventNode p : tcParents.get(m)) {
                    // P has a tc entry because its already part of
                    // tcParents of m (so we've already processed it)
                    // previously.
                    tc.get(p).addAll(tc.get(m));
                    for (EventNode n : tc.get(m)) {
                        // n has a tcParents entry because m is a tc parent
                        // of n and it must have been set above.
                        tcParents.get(n).add(p);
                    }
                }
            }
        }
        // logger.fine("FINAL tc map is: " + tc.toString());
        return transClosure;
    }
}
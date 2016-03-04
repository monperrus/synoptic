package synoptic.invariants.fsmcheck;

import synoptic.invariants.BinaryInvariant;
import synoptic.model.event.EventType;
import synoptic.model.interfaces.INode;

/**
 * Represents a "A always followed by B" synoptic.invariants to simulate,
 * recording the shortest historical path to reach a particular state. This
 * finite state machine enters a failure state when A is encountered, and enters
 * a success state when B is encountered. This means that the failure state upon
 * encountering a final node indicates which, of A and B, was last encountered.
 * NOTE: ensure this documentation stays consistent with AlwaysFollowedSet.
 * 
 * @param <T>
 *            The node type, used as an input, and stored in path-history.
 * @see AFbyInvFsms
 * @see TracingStateSet
 */
public class AFbyTracingSet<T extends INode<T>> extends TracingStateSet<T> {
    HistoryNode<T> wasA; // Indicates that A was seen more recently than B (failing
    // state)
    HistoryNode<T> wasB; // Indicates that B was seen more recently than A
    EventType a, b;

    public AFbyTracingSet(EventType a, EventType b) {
        this.a = a;
        this.b = b;
    }

    public AFbyTracingSet(BinaryInvariant inv) {
        this(inv.getFirst(), inv.getSecond());
    }

    @Override
    public void setInitial(T x) {
        HistoryNode<T> newHistory = new HistoryNode<T>(x, null, 1);
        boolean isA = x.hasEType(a);
        boolean isB = x.hasEType(b);
        wasA = null;
        wasB = null;
        if (isA) {
            wasA = newHistory;
        }
        if (!isA || isB) {
            wasB = newHistory;
        }
    }

    @Override
    public void transition(T x) {
        boolean isA = x.hasEType(a);
        boolean isB = x.hasEType(b);
        if (isA && isB) {
            HistoryNode<T> historyB = preferShorter(wasA, wasB);
            wasA = preferShorter(wasB, wasA);
            wasB = historyB;
        } else {
            if (isA) {
                wasA = preferShorter(wasB, wasA);
                wasB = null;
            } else if (isB) {
                wasB = preferShorter(wasA, wasB);
                wasA = null;
            }
        }
        wasA = extend(x, wasA);
        wasB = extend(x, wasB);
    }

    @Override
    public HistoryNode<T> failpath() {
        return wasA;
    }

    @Override
    public AFbyTracingSet<T> copy() {
        AFbyTracingSet<T> result = new AFbyTracingSet<T>(a, b);
        result.wasA = wasA;
        result.wasB = wasB;
        return result;
    }

    @Override
    public void mergeWith(TracingStateSet<T> other) {
        AFbyTracingSet<T> casted = (AFbyTracingSet<T>) other;
        wasA = preferShorter(wasA, casted.wasA);
        wasB = preferShorter(wasB, casted.wasB);
    }

    @Override
    public boolean isSubset(TracingStateSet<T> other) {
        AFbyTracingSet<T> casted = (AFbyTracingSet<T>) other;
        if (casted.wasA == null) {
            if (wasA != null) {
                return false;
            }
        }
        if (casted.wasB == null) {
            if (wasB != null) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append("AFby: ");
        appendWNull(result, wasA); // Failure case first.
        result.append(" | ");
        appendWNull(result, wasB);
        return result.toString();
    }
}

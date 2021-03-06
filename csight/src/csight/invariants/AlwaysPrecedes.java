package csight.invariants;

import java.util.List;

import csight.invariants.checkers.APChecker;

import synoptic.model.event.DistEventType;

/** A CSight representation of the AP invariant. */
public class AlwaysPrecedes extends BinaryInvariant {

    public AlwaysPrecedes(DistEventType typeFirst, DistEventType typeSecond) {
        super(typeFirst, typeSecond, "AP");

        // It is impossible to mine x AP x in a linear trace.
        assert !typeFirst.equals(typeSecond);
    }

    @Override
    public String scmBadStateQRe() {
        checkInitialized();

        // There is a 'b' that was never preceded by an 'a'. This 'b' is
        // followed by any number of a's or b's.
        return secondSynthEventsQRe() + "^+ . " + someSynthEventsQRe();
    }

    @Override
    public String promelaNeverClaim() {
        // The never claim is only accepted if b was never preceded by a.
        // If we see a before b, the never claim will never be accepted.
        // We redirect to a safe loop when that happens.
        String ret = "";
        ret += String.format("never { /* !(<>(%s)) -> (!(%s) U (%s)))*/\n",
                second.toPromelaString(), second.toPromelaString(),
                first.toPromelaString());

        ret += "State_need_a:\n";
        ret += "    do\n";
        // Accept the claim if we see b without seeing a.
        ret += String.format("      :: %s -> goto wait_end;\n",
                secondNeverEvent());
        ret += String.format("      :: (!%s && !%s)-> goto State_need_a;\n",
                firstNeverEvent(), secondNeverEvent()); // Haven't seen a or b.

        // We've seen a, so b is now safe.
        // We don't match this. If a never claim can't match a step, it is
        // considered safe.
        ret += "    od;\n";

        ret += "wait_end:\n";
        ret += "    do\n";
        ret += "       :: (ENDSTATECHECK && EMPTYCHANNELCHECK) -> break;\n";
        ret += "       :: skip;\n";
        ret += "    od;\n";
        ret += "}\n";
        return ret;
    }

    @Override
    public boolean satisfies(List<DistEventType> eventsPath) {
        // Whether or not we've seen 'first' so far.
        boolean seenFirst = false;
        for (DistEventType e : eventsPath) {
            if (!seenFirst && e.equals(first)) {
                seenFirst = true;
                continue;
            }

            // If we did not see 'first', and e is 'second' then this instance
            // of 'second' is not preceded by a 'first' => ~(first AP second).
            if (!seenFirst && e.equals(second)) {
                return false;
            }
        }

        // Either we never saw 'second', or we saw a 'first' before a first
        // instance of 'second' => first AP second.
        return true;
    }

    @Override
    public APChecker newChecker() {
        return new APChecker(this);
    }
}

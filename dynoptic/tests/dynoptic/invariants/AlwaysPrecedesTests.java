package dynoptic.invariants;

import org.junit.Test;

public class AlwaysPrecedesTests extends InvTesting {

    @Test
    public void create() {
        AlwaysPrecedes inv = new AlwaysPrecedes(e1, e2);
        logger.info(inv.toString());
    }

    @Test
    public void scmBadStatesString() {
        AlwaysPrecedes inv = new AlwaysPrecedes(e1, e2);

        inv.setFirstSynthTracers(fSynth1, fSynth2);
        inv.setSecondSynthTracers(sSynth1, sSynth2);

        logger.info(inv.scmBadStateQRe());
    }
}

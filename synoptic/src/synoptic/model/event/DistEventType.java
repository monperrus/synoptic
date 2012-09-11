package synoptic.model.event;

import java.util.List;

import synoptic.model.channelid.ChannelId;

/**
 * Implements an EventType for a partially ordered log. Here, the event type is
 * a unique string, that is also associated with an abstract "process" via some
 * identifier. The pid doesn't have to be a physical process id. For example, it
 * could also be interpreted as a role that a host performs in the system (e.g.
 * replica role id).
 */
public class DistEventType extends EventType {
    private String eType;
    private String processId;
    private final static String syntheticEventPID = "-1";

    // ////////////////////////////////////////////////////////////////
    // Dynoptic-specific class members:
    // ////////////////////////////////////////////////////////////////

    /**
     * An event type represents a possible transition/action that the CFSM, or
     * one of the FSMs that make up a CFSM can handle. Event types that are
     * communicating events (send, receive) are associated with a channel Id,
     * while local events are associated with a process id of the corresponding
     * process.
     */

    public static DistEventType LocalEvent(String event, int pid) {
        return new DistEventType(event, pid, EventClass.LOCAL, null);

    }

    public static DistEventType SendEvent(String event, ChannelId channel) {
        return new DistEventType(event, channel.getSrcPid(), EventClass.SEND,
                channel);
    }

    public static DistEventType RecvEvent(String event, ChannelId channel) {
        return new DistEventType(event, channel.getDstPid(), EventClass.RECV,
                channel);
    }

    // Used to represent the Synoptic's INITIAL event type, which is a synthetic
    // Synoptic event used for checking "INITIAL AFby X" invariant types.
    public final static DistEventType INITIALEventType = LocalEvent("INITIAL",
            -1);

    // LOCAL : event that is local to a process
    // SEND : an enqueue of a message from a channel event
    // RECV : a dequeue of a message from a channel
    // SYNTH_SEND : a synthetic event that is an enqueue on a synthetic channel
    // that is used for augmenting CFSMs with invariants.
    private enum EventClass {
        LOCAL, SEND, RECV, SYNTH_SEND
    }

    protected EventClass eventCls = null;

    // LOCAL event types are associated with an integer iPid:
    // SEND/RECV events have this iPid set to the id of the sender/receiver
    // process.
    protected int iPid = -1;

    // SEND and RECV event types are associated with a channel id (i.e., a
    // sender and a receiver pid):
    protected ChannelId channelId = null;

    /**
     * Used by Dynoptic.<br/>
     * <br/>
     * Interprets/parses the String eType into either a local, a send, or a
     * receive event. Can only be called once. For send/receive events uses
     * channelIds to determine the channelId associated with the event. <br/>
     * <br/>
     * Returns null on success. Returns a non-null error message on error.
     * 
     * @param channelIds
     * @return
     */
    public String interpretEType(List<ChannelId> channelIds) {
        assert channelIds != null;
        assert channelId == null;
        assert eventCls == null;
        assert iPid == -1;

        // The following characters conflict with SCM's regular-expressions
        // output format.
        assert !eType.contains("*");
        assert !eType.contains(".");
        assert !eType.contains("^");
        assert !eType.contains(")");
        assert !eType.contains("(");
        assert !eType.contains("#");
        assert !eType.contains("|");
        assert !eType.contains("+");

        // There are some other disallowed symbols, as well:
        assert !eType.contains("'");

        // Either '?' for receive event or '!' for send event.
        String delim;

        if (eType.contains("?")) {
            delim = "?";
            this.eventCls = EventClass.RECV;
        } else if (eType.contains("!")) {
            delim = "!";
            this.eventCls = EventClass.SEND;
        } else {
            // A local event type.
            this.iPid = Integer.parseInt(getPID());
            this.eventCls = EventClass.LOCAL;
            return null;
        }

        // Now handle message send (!) or message receive (?) event types.
        String[] splitType = eType.split("\\" + delim);
        if (!(splitType.length == 2)) {
            return "Event type'" + eType + "' contains multiple '" + delim
                    + "' chars.";
        }
        String chName = splitType[0];
        eType = splitType[1];

        for (ChannelId cid : channelIds) {
            if (cid.getName().equals(chName)) {
                channelId = cid;
            }
        }

        if (channelId == null) {
            return "Channel name '"
                    + chName
                    + "' in the log is not specified in the channel specification.";
        }

        if (this.eventCls == EventClass.SEND) {
            this.iPid = channelId.getSrcPid();
        } else {
            this.iPid = channelId.getDstPid();
        }

        return null;
    }

    public static DistEventType SynthSendEvent(DistEventType eToTrace,
            ChannelId channel, boolean isFirstTracer) {
        String event = eToTrace.getScmEventFullString();
        if (isFirstTracer) {
            event += "TR0";
        } else {
            event += "TR1";
        }

        return new DistEventType(event, channel.getDstPid(),
                EventClass.SYNTH_SEND, channel);
    }

    public int getEventPid() {
        return iPid;
    }

    public ChannelId getChannelId() {
        return channelId;
    }

    public boolean isCommEvent() {
        return eventCls == EventClass.SEND || eventCls == EventClass.RECV
                || eventCls == EventClass.SYNTH_SEND;
    }

    public boolean isRecvEvent() {
        return eventCls == EventClass.RECV;
    }

    public boolean isSendEvent() {
        return eventCls == EventClass.SEND;
    }

    public boolean isSynthSendEvent() {
        return eventCls == EventClass.SYNTH_SEND;
    }

    public boolean isLocalEvent() {
        return eventCls == EventClass.LOCAL;
    }

    public String getScmEventString() {
        if (isLocalEvent()) {
            return getScmEventFullString();
        }
        return eType;

    }

    public String getScmEventFullString() {
        if (isSynthSendEvent()) {
            // The internal string is the exact SCM event
            // representation.
            return eType;
        } else if (isSendEvent()) {
            return "ch" + channelId.getScmId() + "S" + eType;
        } else if (isRecvEvent()) {
            return "ch" + channelId.getScmId() + "R" + eType;
        }
        return eType + "p" + Integer.toString(iPid) + "L";
    }

    public String toString(String cidString, char separator) {
        if (isSendEvent() || isSynthSendEvent()) {
            return cidString + separator + "!" + separator
                    + getScmEventString();
        } else if (isRecvEvent()) {
            return cidString + separator + "?" + separator
                    + getScmEventString();
        }
        return getScmEventString();
    }

    /**
     * Creates a new DistEventType that is a non-INITIAL and non-TERMINAL with a
     * pid.
     */
    public DistEventType(String type, int pid, EventClass eventCls,
            ChannelId cid) {
        this(type, Integer.toString(pid), false, false);
        this.iPid = pid;
        this.eventCls = eventCls;
        this.channelId = cid;
    }

    // ////////////////////////////////////////////////////////////////
    // /Dynoptic-specific class members
    // ////////////////////////////////////////////////////////////////

    private DistEventType(String eType, String pid, boolean isInitialEventType,
            boolean isTerminalEventType) {
        super(isInitialEventType, isTerminalEventType);
        this.eType = eType;
        processId = pid;
    }

    /**
     * Creates a new DistEventType that is a non-INITIAL and non-TERMINAL with a
     * pid.
     */
    public DistEventType(String type, String pid) {
        this(type, pid, false, false);
    }

    /**
     * Creates a new DistEventType that is a non-INITIAL and non-TERMINAL
     * without a pid.
     */
    public DistEventType(String type) {
        // We set pid to "-1" to indicate that it is not initialized.
        this(type, "-1", false, false);
    }

    /**
     * Creates a new DistEventType that is an INITIAL.
     */
    static public DistEventType newInitialDistEventType() {
        return new DistEventType(EventType.initialNodeLabel, syntheticEventPID,
                true, false);
    }

    /**
     * Creates a new DistEventType that is an TERMINAL.
     */
    static public DistEventType newTerminalDistEventType() {
        return new DistEventType(EventType.terminalNodeLabel,
                syntheticEventPID, false, true);
    }

    // ///////////////////////////////////////////////////////////////////////

    public String getEType() {
        return eType;
    }

    public String getPID() {
        return processId;
    }

    public String setPID(String pid) {
        return processId = pid;
    }

    @Override
    public int compareTo(EventType eother) {
        int baseCmp = super.compareTo(eother);
        if (baseCmp != 0) {
            return baseCmp;
        }
        DistEventType dother = (DistEventType) eother;
        int eTypeCmp = eType.compareTo(dother.eType);
        if (eTypeCmp != 0) {
            return eTypeCmp;
        }
        return processId.compareTo(dother.processId);
    }

    @Override
    public boolean equals(Object other) {
        if (!super.equals(other)) {
            return false;
        }

        if (!(other instanceof DistEventType)) {
            return false;
        }

        DistEventType otherE = (DistEventType) other;
        if (!otherE.getEType().equals(eType)) {
            return false;
        }

        if (!otherE.getPID().equals(processId)) {
            return false;
        }

        if (otherE.getEventPid() != iPid) {
            return false;
        }

        if (this.eventCls != null) {

            if (otherE.isCommEvent() != this.isCommEvent()) {
                return false;
            }

            if (otherE.isSynthSendEvent() != this.isSynthSendEvent()) {
                return false;
            }

            if (this.isCommEvent()) {
                if (otherE.isRecvEvent() != isRecvEvent()) {
                    return false;
                }
                if (otherE.isSendEvent() != isSendEvent()) {
                    return false;
                }
                if (!otherE.getChannelId().equals(this.getChannelId())) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public String toString() {
        // Old DistEventType:
        //
        // return eType + "_" + processId;

        if (channelId != null) {
            return toString(channelId.toString(), ' ');
        }
        return toString("", ' ');
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + eType.hashCode();
        result = 31 * result + processId.hashCode();
        result = 31 * result + (eventCls == null ? 0 : eventCls.hashCode());
        result = 31 * result + (channelId == null ? 0 : channelId.hashCode());
        return result;
    }
}

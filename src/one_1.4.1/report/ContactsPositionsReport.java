package report;

import core.*;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Contacts locations report. Reports the location (cooredinates) of contacts between the nodes.
 * The reporting interval can be configured.
 */
public class ContactsPositionsReport extends Report implements ConnectionListener, UpdateListener {
    /** Reporting granularity -setting id ({@value}).
     * Defines the interval how often (seconds) a new snapshot of contact
     * locations is created */
    public static final String GRANULARITY = "granularity";

    /** value of the granularity setting */
    protected final double granularity;
    /** time of last update*/
    protected double lastUpdate;
    /** Connections that are reported */
    protected HashMap<ConnectionInfo, ConnectionInfo> connections;
    protected int nrofActiveNodes;


    /**
     * Constructor. Reads the settings and initializes the report module.
     */
    public ContactsPositionsReport() {
        Settings settings = getSettings();
        this.lastUpdate = 0;
        if (settings.contains(GRANULARITY)) {
            this.granularity = settings.getDouble(GRANULARITY);
        }
        else {
            this.granularity = 1.0;
        }

        init();
    }

    @Override
    protected void init() {
        super.init();
        this.connections = new HashMap<ConnectionInfo, ConnectionInfo>();
    }

    @Override
    public void hostsConnected(DTNHost host1, DTNHost host2) {
        if (isWarmup()) {
            return;
        }
        ConnectionInfo ci = addConnection(host1, host2);
        dumpLine();
        System.out.println("[START] [" + (int) getSimTime() + "] " + connections.size() + " " + ci.toString());
    }

    @Override
    public void hostsDisconnected(DTNHost host1, DTNHost host2) {
        newEvent();
        ConnectionInfo ci = removeConnection(host1, host2);

        if (ci == null) {
            return; /* the connection was started during the warm up period */
        }

        ci.connectionEnd();
        dumpLine();
        System.out.println("[END]   [" + (int) getSimTime() + "] " + connections.size() + " " + nrofActiveNodes + " " + ci.toString());

    }

    protected ConnectionInfo removeConnection(DTNHost host1, DTNHost host2) {
        ConnectionInfo ci = new ConnectionInfo(host1, host2);
        ci = connections.remove(ci);
        return ci;
    }

    @Override
    public void updated(List<DTNHost> hosts) {
        double simTime = getSimTime();
        int nrofActiveHosts = 0;
        for (DTNHost host : hosts) {
            if (host.isActive()) {
                nrofActiveHosts++;
            }
        }
        this.nrofActiveNodes = nrofActiveHosts;

        /* creates a snapshot oif the contacts once every granularity seconds */
        if (simTime - lastUpdate >= granularity) {
            dumpLine();
            this.lastUpdate = simTime - simTime % granularity;
        }

    }

    /**
     * Creates a snapshot of the existing connections
     */
    protected void dumpLine() {
        for (ConnectionInfo c : connections.values()) {
            // Check whether the position of the nodes has changed
            if (c.update()) {
                write((int)getSimTime() + ";" + c.toReportLine()); /* write coordinate and message IDs */
            }
        }
    }

    protected ConnectionInfo addConnection(DTNHost host1, DTNHost host2) {
        ConnectionInfo ci = new ConnectionInfo(host1, host2);

        assert !connections.containsKey(ci) : "Already contained "+
                " a connection of " + host1 + " and " + host2;

        connections.put(ci, ci);
        return ci;
    }

    @Override
    public void done() {
        System.out.println(connections.size() + " " + nrofActiveNodes + "\n" + connections.toString());
        super.done();
    }

    protected class ConnectionInfo {
        private double startTime;
        private double endTime;
        private DTNHost h1;
        private DTNHost h2;
        private Tuple<Coord, Coord> lastReportedPositions;

        public ConnectionInfo (DTNHost h1, DTNHost h2){
            this.h1 = h1;
            this.h2 = h2;
            this.startTime = getSimTime();
            this.endTime = -1;
            this.lastReportedPositions = getHostsPositions();
        }

        /**
         * Should be called when the connection ended to record the time.
         */
        public void connectionEnd() {
            this.endTime = getSimTime();
        }

        /**
         * Returns true if the positions of the hosts has changed since the last reported position
         */
        public boolean update() {
            Coord h1oldLoc = this.lastReportedPositions.getKey();
            Coord h2oldLoc = this.lastReportedPositions.getValue();

            Tuple<Coord, Coord> newPos = getHostsPositions();
            Coord h1newLoc = newPos.getKey();
            Coord h2newLoc = newPos.getValue();

            if (h1oldLoc.equals(h1newLoc) && h2oldLoc.equals(h2newLoc)) {
                return false;
            }

            this.lastReportedPositions = newPos;
            return true;
        }

        /**
         * Returns the positions of hosts 1 and 2, and
         * set the last reported position of the nodes
         */
        public Tuple<Coord, Coord> getHostsPositions() {
            return new Tuple(this.h1.getLocation().clone(), this.h2.getLocation().clone());
        }

        /**
         * Returns true if the other connection info contains the same hosts.
         */
        public boolean equals(Object other) {
            if (!(other instanceof ConnectionInfo)) {
                return false;
            }

            ConnectionInfo ci = (ConnectionInfo)other;

            if ((h1 == ci.h1 && h2 == ci.h2)) {
                return true;
            }
            else if ((h1 == ci.h2 && h2 == ci.h1)) {
                // bidirectional connection the other way
                return true;
            }
            return false;
        }

        /**
         * Returns the same hash for ConnectionInfos that have the
         * same two hosts.
         * @return Hash code
         */
        public int hashCode() {
            String hostString;

            if (this.h1.compareTo(this.h2) < 0) {
                hostString = h1.toString() + "-" + h2.toString();
            }
            else {
                hostString = h2.toString() + "-" + h1.toString();
            }

            return hostString.hashCode();
        }

        /**
         * Returns a string representation of the info object
         * @return a string representation of the info object
         */
        public String toString() {
            return this.h1 + "<->" + this.h2 + " [" + this.startTime
                    +"-"+ (this.endTime > 0 ? this.endTime : "n/a") + "]";
        }

        public String toReportLine() {
            String reportLine = "";
            Coord h1Loc = h1.getLocation();
            Coord h2Loc = h2.getLocation();

            reportLine += h1.getAddress() + ";" + h2.getAddress() + ";" + (int)startTime;
            reportLine += ";" + h1Loc.getX() + ";" + h1Loc.getY();
            reportLine += ";" + h2Loc.getX() + ";" + h2Loc.getY();

            return reportLine;
        }
    }

}


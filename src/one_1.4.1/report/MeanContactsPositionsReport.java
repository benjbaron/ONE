package report;

import core.*;

import java.util.HashMap;
import java.util.List;

/**
 * Contacts locations report. Reports the location (cooredinates) of contacts between the nodes.
 * The reporting interval can be configured.
 */
public class MeanContactsPositionsReport extends Report implements ConnectionListener {
    /** Reporting granularity -setting id ({@value}).
     * Defines the interval how often (seconds) a new snapshot of contact
     * locations is created */
    public static final String GRANULARITY = "granularity";
    /** Connections that are reported */
    protected HashMap<ConnectionInfo, ConnectionInfo> connections;
    private BoundingBox bb;

    /**
     * Constructor. Reads the settings and initializes the report module.
     */
    public MeanContactsPositionsReport() {
        init();
    }

    @Override
    protected void init() {
        super.init();
        this.connections = new HashMap<ConnectionInfo, ConnectionInfo>();
        this.bb = new BoundingBox();
    }

    @Override
    public void hostsConnected(DTNHost host1, DTNHost host2) {
        if (isWarmup()) {
            return;
        }
        if (this.bb.isNull()) {
            this.bb = host1.getBoundingBox();
        }
        addConnection(host1, host2);
    }

    @Override
    public void hostsDisconnected(DTNHost host1, DTNHost host2) {
        newEvent();
        ConnectionInfo ci = removeConnection(host1, host2);

        if (ci == null) {
            return; /* the connection was started during the warm up period */
        }

        ci.connectionEnd();
        write(ci.toReportLine());
    }

    protected ConnectionInfo removeConnection(DTNHost host1, DTNHost host2) {
        ConnectionInfo ci = new ConnectionInfo(host1, host2);
        ci = connections.remove(ci);
        return ci;
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
        super.done();
    }

    protected class ConnectionInfo {
        private double startTime;
        private double endTime;
        private DTNHost h1;
        private DTNHost h2;
        private Tuple<Coord, Coord> initPos;
        private Tuple<Coord, Coord> finalPos;

        public ConnectionInfo (DTNHost h1, DTNHost h2){
            this.h1 = h1;
            this.h2 = h2;
            this.startTime = getSimTime();
            this.endTime = -1;
            this.initPos = this.finalPos = getHostsPositions();
        }

        /**
         * Should be called when the connection ended to record the time.
         */
        public void connectionEnd() {
            this.endTime = getSimTime();
            this.finalPos = getHostsPositions();
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

            /** Get the intial and final coordinates of the two hosts
             * and normalize the positions */
            Coord h1finalLoc = new Coord(finalPos.getKey().getX() + bb.getMinX(), finalPos.getKey().getY() + bb.getMinY());
            Coord h2finalLoc = new Coord(finalPos.getValue().getX() + bb.getMinX(), finalPos.getValue().getY() + bb.getMinY());
            Coord h1initLoc = new Coord(initPos.getKey().getX() + bb.getMinX(), initPos.getKey().getY() + bb.getMinY());
            Coord h2initLoc = new Coord(initPos.getValue().getX() + bb.getMinX(), initPos.getValue().getY() + bb.getMinY());

            reportLine += (int)startTime + ";" + (int)endTime + ";" + (int)(endTime-startTime);
            reportLine += ";" + h1.getAddress() + ";" + h2.getAddress();

            /** Write a bounding box of the locations (WKT format) */
            BoundingBox theBB = new BoundingBox(h1initLoc, h2initLoc);
            theBB.include(new BoundingBox(h1finalLoc, h2finalLoc));
            double x1 = theBB.getMinX();
            double y1 = theBB.getMinY();
            double x2 = theBB.getMaxX();
            double y2 = theBB.getMaxY();

            reportLine += ";POLYGON(("+x1+" "+y1+", "+x1+" "+y2+", "+x2+" "+y2+", "+x2+" "+y1+", "+x1+" "+y1+"))";

            return reportLine;
        }
    }

}


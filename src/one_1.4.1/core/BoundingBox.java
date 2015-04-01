package core;

public class BoundingBox {
    private double minX;
    private double maxX;
    private double minY;
    private double maxY;


    public BoundingBox() {
        this.minX = 0.0;
        this.maxX = -1.0;
        this.minY = 0.0;
        this.maxY = -1.0;
    }

    public BoundingBox(double x1, double x2, double y1, double y2) {
        this.minX = Math.min(x1, x2);
        this.maxX = Math.max(x1, x2);
        this.minY = Math.min(y1, y2);
        this.maxY = Math.max(y1, y2);
    }

    public BoundingBox(Coord c1, Coord c2) {
        this.minX = Math.min(c1.getX(), c2.getX());
        this.maxX = Math.max(c1.getX(), c2.getX());
        this.minY = Math.min(c1.getY(), c2.getY());
        this.maxY = Math.max(c1.getY(), c2.getY());
    }

    public boolean intersects(BoundingBox bounds) {
        if (isNull() || bounds.isEmpty()) {
            return false;
        }

        return !(bounds.getMinX() > this.getMaxX() || bounds.getMaxX() < this.getMinX()
                || bounds.getMinY() > this.getMaxY() || bounds.getMaxY() < this.getMinY());
    }

    public boolean contains(BoundingBox bounds) {
        if (isEmpty() || bounds.isEmpty()) {
            return false;
        }
        return bounds.getMinX() >= this.getMinX() && bounds.getMaxX() <= this.getMaxX()
                && bounds.getMinY() >= this.getMinY() && bounds.getMaxY() <= this.getMaxY();
    }

    public boolean contains(Coord location) {
        if (isEmpty()) {
            return false;
        }
        return location.getX() >= getMinX() && location.getX() <= getMaxX()
                && location.getY() >= getMinY() && location.getY() <= getMaxY();
    }

    public void setBounds(BoundingBox bounds) {
        this.minX = bounds.getMinX();
        this.minY = bounds.getMinY();
        this.maxX = bounds.getMaxX();
        this.maxY = bounds.getMaxY();
    }

    public void include(BoundingBox bounds) {
        if (bounds.isEmpty()) {
            return;
        }
        if (isNull()) {
            setBounds(bounds);
        } else {
            if (bounds.getMinX() < getMinX()) {
                this.minX = bounds.getMinX();
            }
            if (bounds.getMaxX() > getMaxX()) {
                this.maxX = bounds.getMaxX();
            }
            if (bounds.getMinY() < getMinY()) {
                this.minY = bounds.getMinY();
            }
            if (bounds.getMaxY() > getMaxY()) {
                this.maxY = bounds.getMaxY();
            }
        }
    }

    public void include(Coord c) {
        if (isNull()) {
            this.minX = this.maxX = c.getX();
            this.minY = this.maxY = c.getY();
        } else {
            if (c.getX() < getMinX()) {
                this.minX = c.getX();
            }
            else if (c.getX() > getMaxX()) {
                this.maxX = c.getX();
            }
            if (c.getY() < getMinY()) {
                this.minY = c.getY();
            }
            else if (c.getY() > getMaxY()) {
                this.maxY = c.getY();
            }
        }
    }

    /**
     * Checks if this coordinate's location is equal to other coordinate's
     * @param c The other coordinate
     * @return True if locations are the same
     */
    public boolean equals(BoundingBox c) {
        if (c == this) {
            return true;
        }
        else {
            return (minX == c.minX && maxX == c.maxX && minY == c.minY && maxY == c.maxY);
        }
    }

    @Override
    public boolean equals(Object o) {
        return equals((BoundingBox) o);
    }

    public boolean isNull() {
        return (minX == 0.0 && minY == 0.0 && maxX < 0.0 && maxY < 0.0);
    }

    public boolean isEmpty() {
        return ((maxX - minX) == 0.0 && (maxY - minY == 0.0));
    }

    public double getMinX() {
        return minX;
    }

    public double getMaxX() {
        return maxX;
    }

    public double getMinY() {
        return minY;
    }

    public double getMaxY() {
        return maxY;
    }

    public String toString() {
        return "(" + minX + ", " + minY + "), (" + maxX + ", " + maxY + ")";
    }
}

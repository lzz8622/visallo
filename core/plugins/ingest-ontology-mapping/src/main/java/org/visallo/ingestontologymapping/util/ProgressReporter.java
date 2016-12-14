package org.visallo.ingestontologymapping.util;

public abstract class ProgressReporter {
    public abstract void finishedRow(int row, int totalRows);
}

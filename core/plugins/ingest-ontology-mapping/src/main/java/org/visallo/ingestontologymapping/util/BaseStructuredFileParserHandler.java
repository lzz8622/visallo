package org.visallo.ingestontologymapping.util;

import java.util.List;
import java.util.Map;

public class BaseStructuredFileParserHandler {
    private int totalRows = -1;
    public void newSheet(String name) {
    }

    public void addColumn(String title) {
    }

    public boolean addRow(Map<String,String> row, int rowNum) {
        return true;
    }

    public void setTotalRows(int rows) {
        this.totalRows = rows;
    }

    public int getTotalRows() {
        return totalRows;
    }
}

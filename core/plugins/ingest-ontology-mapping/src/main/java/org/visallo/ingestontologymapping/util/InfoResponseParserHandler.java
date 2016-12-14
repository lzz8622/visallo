package org.visallo.ingestontologymapping.util;

import org.visallo.ingestontologymapping.model.StructuredFileInfoResponse;

import java.util.List;
import java.util.Map;

public class InfoResponseParserHandler extends BaseStructuredFileParserHandler {
    private StructuredFileInfoResponse result = new StructuredFileInfoResponse();
    private StructuredFileInfoResponse.Sheet currentSheet;

    public StructuredFileInfoResponse getResult() {
        return this.result;
    }

    @Override
    public void newSheet(String name) {
        currentSheet = new StructuredFileInfoResponse.Sheet();
        currentSheet.name = name;
        result.sheets.add(currentSheet);
    }

    @Override
    public void addColumn(String name) {
        StructuredFileInfoResponse.Column column = new StructuredFileInfoResponse.Column();
        column.name = name;
        currentSheet.columns.add(column);
    }

    @Override
    public boolean addRow(Map<String, String> row, int rowNum) {
        StructuredFileInfoResponse.ParsedRow parsedRow = new StructuredFileInfoResponse.ParsedRow();
        parsedRow.columns.addAll(row.values());
        currentSheet.parsedRows.add(parsedRow);
        return currentSheet.parsedRows.size() < 10;
    }
}

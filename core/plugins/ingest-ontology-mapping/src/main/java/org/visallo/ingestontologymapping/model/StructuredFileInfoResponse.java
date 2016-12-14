package org.visallo.ingestontologymapping.model;

import org.visallo.web.clientapi.model.ClientApiObject;

import java.util.ArrayList;
import java.util.List;

public class StructuredFileInfoResponse implements ClientApiObject {
    public List<Sheet> sheets = new ArrayList<>();

    public static class Sheet {
        public String name;
        public List<Column> columns = new ArrayList<>();
        public List<String> rawRows = new ArrayList<>();
        public List<ParsedRow> parsedRows = new ArrayList<>();
    }

    public static class Column {
        public String name;
    }

    public static class ParsedRow {
        public List<Object> columns = new ArrayList<>();
    }
}

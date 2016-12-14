package org.visallo.ingestontologymapping.model;

import org.visallo.ingestontologymapping.util.mapping.PropertyMapping;
import org.visallo.web.clientapi.model.ClientApiObject;

import java.util.ArrayList;
import java.util.List;

public class ParseErrors implements ClientApiObject {
    public List<ParseError> errors = new ArrayList<>();

    public static class ParseError {
        public String rawPropertyValue;
        public PropertyMapping propertyMapping;
        public String message;
        public int sheetIndex;
        public int rowIndex;
    }
}

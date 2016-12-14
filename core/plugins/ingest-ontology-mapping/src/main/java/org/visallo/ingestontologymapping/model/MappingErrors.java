package org.visallo.ingestontologymapping.model;

import org.visallo.ingestontologymapping.util.mapping.EdgeMapping;
import org.visallo.ingestontologymapping.util.mapping.PropertyMapping;
import org.visallo.ingestontologymapping.util.mapping.VertexMapping;
import org.visallo.web.clientapi.model.ClientApiObject;

import java.util.ArrayList;
import java.util.List;

public class MappingErrors implements ClientApiObject {
    public List<MappingError> mappingErrors = new ArrayList<>();

    public static class MappingError {
        public PropertyMapping propertyMapping;
        public VertexMapping vertexMapping;
        public EdgeMapping edgeMapping;
        public String attribute;
        public String message;
    }
}

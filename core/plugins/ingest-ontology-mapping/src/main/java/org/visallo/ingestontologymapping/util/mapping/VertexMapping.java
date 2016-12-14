package org.visallo.ingestontologymapping.util.mapping;

import org.apache.commons.lang.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.vertexium.Authorizations;
import org.vertexium.Visibility;
import org.visallo.core.model.ontology.OntologyRepository;
import org.visallo.core.security.VisibilityTranslator;
import org.visallo.ingestontologymapping.model.MappingErrors;
import org.visallo.web.clientapi.model.VisibilityJson;

import java.util.ArrayList;
import java.util.List;

public class VertexMapping {
    public static final String PROPERTY_MAPPING_PROPERTIES_KEY = "properties";
    public static final String PROPERTY_MAPPING_VISIBILITY_KEY = "visibilitySource";

    public List<PropertyMapping> propertyMappings = new ArrayList<>();
    public VisibilityJson visibilityJson;
    public Visibility visibility;

    public VertexMapping(OntologyRepository ontologyRepository, VisibilityTranslator visibilityTranslator, String workspaceId, JSONObject vertexMapping) {
        String visibilitySource = vertexMapping.optString(PROPERTY_MAPPING_VISIBILITY_KEY);
        if(!StringUtils.isBlank(visibilitySource)) {
            visibilityJson = new VisibilityJson(visibilitySource);
            visibilityJson.addWorkspace(workspaceId);
            visibility = visibilityTranslator.toVisibility(visibilityJson).getVisibility();
        }

        JSONArray jsonPropertyMappings = vertexMapping.getJSONArray(PROPERTY_MAPPING_PROPERTIES_KEY);
        for (int i = 0; i < jsonPropertyMappings.length(); i++) {
            propertyMappings.add(PropertyMapping.fromJSON(ontologyRepository, visibilityTranslator, workspaceId, jsonPropertyMappings.getJSONObject(i)));
        }
    }

    public MappingErrors validate(Authorizations authorizations) {
        MappingErrors errors = new MappingErrors();

        if(visibility != null && !authorizations.canRead(visibility)) {
            MappingErrors.MappingError mappingError = new MappingErrors.MappingError();
            mappingError.vertexMapping = this;
            mappingError.attribute = PROPERTY_MAPPING_VISIBILITY_KEY;
            mappingError.message = "Invalid visibility specified.";
            errors.mappingErrors.add(mappingError);
        }

        for (PropertyMapping propertyMapping : propertyMappings) {
            MappingErrors propertyMappingErrors = propertyMapping.validate(authorizations);
            errors.mappingErrors.addAll(propertyMappingErrors.mappingErrors);
        }

        return errors;
    }
}

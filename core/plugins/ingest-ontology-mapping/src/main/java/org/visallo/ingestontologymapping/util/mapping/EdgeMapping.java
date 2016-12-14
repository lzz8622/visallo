package org.visallo.ingestontologymapping.util.mapping;

import org.apache.commons.lang.StringUtils;
import org.json.JSONObject;
import org.vertexium.Authorizations;
import org.vertexium.Visibility;
import org.visallo.core.security.VisibilityTranslator;
import org.visallo.ingestontologymapping.model.MappingErrors;
import org.visallo.web.clientapi.model.VisibilityJson;

public class EdgeMapping {
    public static final String PROPERTY_MAPPING_VISIBILITY_KEY = "visibilitySource";
    public static final String PROPERTY_MAPPING_IN_VERTEX_KEY = "inVertex";
    public static final String PROPERTY_MAPPING_OUT_VERTEX_KEY = "outVertex";
    public static final String PROPERTY_MAPPING_LABEL_KEY = "label";

    public int inVertexIndex;
    public int outVertexIndex;
    public String label;
    public VisibilityJson visibilityJson;
    public Visibility visibility;

    public EdgeMapping(VisibilityTranslator visibilityTranslator, String workspaceId, JSONObject edgeMapping) {
        this.inVertexIndex = edgeMapping.getInt(PROPERTY_MAPPING_IN_VERTEX_KEY);
        this.outVertexIndex = edgeMapping.getInt(PROPERTY_MAPPING_OUT_VERTEX_KEY);
        this.label = edgeMapping.getString(PROPERTY_MAPPING_LABEL_KEY);

        String visibilitySource = edgeMapping.optString(PROPERTY_MAPPING_VISIBILITY_KEY);
        if(!StringUtils.isBlank(visibilitySource)) {
            visibilityJson = new VisibilityJson(visibilitySource);
            visibilityJson.addWorkspace(workspaceId);
            visibility = visibilityTranslator.toVisibility(visibilityJson).getVisibility();
        }
    }

    public MappingErrors validate(Authorizations authorizations) {
        MappingErrors errors = new MappingErrors();

        if(visibility != null && !authorizations.canRead(visibility)) {
            MappingErrors.MappingError mappingError = new MappingErrors.MappingError();
            mappingError.edgeMapping = this;
            mappingError.attribute = PROPERTY_MAPPING_VISIBILITY_KEY;
            mappingError.message = "Invalid visibility specified.";
            errors.mappingErrors.add(mappingError);
        }

        return errors;
    }
}

package org.visallo.ingestontologymapping.model;

import org.json.JSONObject;
import org.vertexium.Authorizations;
import org.visallo.core.util.ClientApiConverter;
import org.visallo.ingestontologymapping.util.ParseOptions;

public class ImportStructuredDataQueueItem {
    private String mapping;
    private String workspaceId;
    private String userId;
    private String[] authorizations;
    private String vertexId;
    private ParseOptions parseOptions;
    private String type;

    public ImportStructuredDataQueueItem() {

    }

    public ImportStructuredDataQueueItem(String workspaceId, String mapping, String userId, String vertexId, String type, ParseOptions options, Authorizations authorizations) {
        this.workspaceId = workspaceId;
        this.mapping = mapping;
        this.userId = userId;
        this.vertexId = vertexId;
        this.type = type;
        this.authorizations = authorizations.getAuthorizations();
        this.parseOptions = options;
    }

    public String getType() {
        return type;
    }

    public String getUserId() {
        return userId;
    }

    public String getVertexId() {
        return vertexId;
    }

    public String getMapping() {
        return mapping;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public ParseOptions getParseOptions() {
        return parseOptions;
    }

    public String[] getAuthorizations() {
        return authorizations;
    }

    public JSONObject toJson() {
        return new JSONObject(ClientApiConverter.clientApiToString(this));
    }
}

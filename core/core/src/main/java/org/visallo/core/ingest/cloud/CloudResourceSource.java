package org.visallo.core.ingest.cloud;

import org.json.JSONObject;

import java.util.Collection;

public interface CloudResourceSource {

    public Collection<CloudResourceSourceItem> getItems(JSONObject configuration);

}

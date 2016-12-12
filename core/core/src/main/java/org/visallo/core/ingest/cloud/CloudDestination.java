package org.visallo.core.ingest.cloud;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;

public interface CloudDestination {

    public Collection<CloudDestinationItem> getItems(JSONObject configuration);

    public void putFiles(OutputStream outputStream);
}

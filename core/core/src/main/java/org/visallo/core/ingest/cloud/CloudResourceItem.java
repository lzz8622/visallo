package org.visallo.core.ingest.cloud;

import java.io.InputStream;

public interface CloudResourceItem {

    InputStream getInputStream();
    String getName();
    Long getSize();

}

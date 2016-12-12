package org.visallo.core.ingest.cloud;

import java.io.InputStream;

public interface CloudDestinationItem {

    InputStream getInputStream();
    String getName();

}

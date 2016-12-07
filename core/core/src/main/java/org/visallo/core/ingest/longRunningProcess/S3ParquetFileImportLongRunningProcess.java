package org.visallo.core.ingest.longRunningProcess;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.format.converter.ParquetMetadataConverter;
import org.apache.parquet.hadoop.ParquetFileReader;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.metadata.ParquetMetadata;
import org.apache.parquet.tools.json.JsonRecordFormatter;
import org.apache.parquet.tools.read.SimpleReadSupport;
import org.apache.parquet.tools.read.SimpleRecord;
import org.json.JSONObject;
import org.visallo.core.model.longRunningProcess.LongRunningProcessWorker;

import java.io.IOException;

public class S3ParquetFileImportLongRunningProcess extends LongRunningProcessWorker {
    @Override
    public boolean isHandled(JSONObject longRunningProcessQueueItem) {
        return false;
    }

    @Override
    protected void processInternal(JSONObject longRunningProcessQueueItem) {
        String input = longRunningProcessQueueItem.getString("file");
        ParquetMetadata metadata = null;
        ParquetReader<SimpleRecord> reader = null;
        try {
            reader = ParquetReader.builder(new SimpleReadSupport(), new Path(input)).build();
            for (SimpleRecord value = reader.read(); value != null; value = reader.read()) {
                System.out.println(value);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

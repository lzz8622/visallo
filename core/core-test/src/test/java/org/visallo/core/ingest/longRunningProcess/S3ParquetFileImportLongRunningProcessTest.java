package org.visallo.core.ingest.longRunningProcess;

import org.apache.hadoop.fs.Path;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.metadata.ParquetMetadata;
import org.apache.parquet.tools.read.SimpleReadSupport;
import org.apache.parquet.tools.read.SimpleRecord;
import org.json.JSONObject;
import org.junit.Test;
import org.visallo.core.model.longRunningProcess.LongRunningProcessWorker;

import java.io.IOException;

public class S3ParquetFileImportLongRunningProcessTest {

    @Test
    public void ttest(){
        S3ParquetFileImportLongRunningProcess process = new S3ParquetFileImportLongRunningProcess();

        JSONObject obj = new JSONObject()
                .put("file", "/home/ryan/repos/schema.org-visallo/performance_applications_4_3.parquet/part-r-00000-954b67a2-066a-48ac-bb30-bbf63eaf9056.snappy.parquet");
        process.processInternal(obj);
    }
}

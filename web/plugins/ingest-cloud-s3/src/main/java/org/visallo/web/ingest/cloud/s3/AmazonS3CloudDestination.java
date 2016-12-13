package org.visallo.web.ingest.cloud.s3;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import org.json.JSONArray;
import org.json.JSONObject;
import org.visallo.core.ingest.cloud.CloudDestination;
import org.visallo.core.ingest.cloud.CloudDestinationItem;
import org.visallo.core.util.JSONUtil;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.stream.Collectors;

public class AmazonS3CloudDestination implements CloudDestination {

    @Override
    public Collection<CloudDestinationItem> getItems(JSONObject configuration) {
        JSONObject credentials = configuration.getJSONObject("credentials");
        String accessKey = credentials.getString("accessKey");
        String secret = credentials.getString("secret");
        AmazonS3 s3 = new AmazonS3Client(new BasicAWSCredentials(accessKey, secret));

        String bucket = configuration.getString("bucket");
        JSONArray paths = configuration.getJSONArray("paths");

        return JSONUtil.toList(paths)
                .stream()
                .map(key -> new AmazonS3CloudDestinationItem(s3, bucket, (String) key))
                .collect(Collectors.toList());
    }

    @Override
    public void putFiles(OutputStream outputStream) {

    }

    static class AmazonS3CloudDestinationItem implements CloudDestinationItem {
        private S3Object object;
        private AmazonS3 s3;
        private String bucket;
        private String key;

        AmazonS3CloudDestinationItem(AmazonS3 s3, String bucket, String key) {
            this.s3 = s3;
            this.bucket = bucket;
            this.key = key.replaceAll("^\\/", "");
        }

        @Override
        public InputStream getInputStream() {
            return getObject().getObjectContent();
        }

        @Override
        public String getName() {
            int last = key.lastIndexOf("/");
            String name = key;

            if (last >= 0) {
                name = name.substring(last + 1);
            }

            return name;
        }

        @Override
        public Long getSize() {
            return getObject().getObjectMetadata().getContentLength();
        }

        private synchronized S3Object getObject() {
            if (object == null) {
                object = s3.getObject(new GetObjectRequest(bucket, key));
            }
            return object;
        }
    }
}

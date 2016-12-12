package org.visallo.core.ingest.cloud;

import com.google.common.io.Files;
import com.google.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.vertexium.Authorizations;
import org.vertexium.Graph;
import org.visallo.core.bootstrap.InjectHelper;
import org.visallo.core.config.Configuration;
import org.visallo.core.exception.VisalloException;
import org.visallo.core.ingest.FileImport;
import org.visallo.core.model.longRunningProcess.LongRunningProcessRepository;
import org.visallo.core.model.longRunningProcess.LongRunningProcessWorker;
import org.visallo.core.model.user.UserRepository;
import org.visallo.core.model.workQueue.Priority;
import org.visallo.core.model.workspace.Workspace;
import org.visallo.core.model.workspace.WorkspaceRepository;
import org.visallo.core.user.User;
import org.visallo.core.util.ClientApiConverter;
import org.visallo.web.clientapi.model.ClientApiImportProperty;

import java.io.File;
import java.io.InputStream;
import java.util.Collection;

public class CloudImportLongRunningProcessWorker extends LongRunningProcessWorker {
    private final Configuration configuration;
    private final FileImport fileImport;
    private final Graph graph;
    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final LongRunningProcessRepository longRunningProcessRepository;

    @Inject
    public CloudImportLongRunningProcessWorker(
            Graph graph,
            Configuration configuration,
            FileImport fileImport,
            UserRepository userRepository,
            WorkspaceRepository workspaceRepository,
            LongRunningProcessRepository longRunningProcessRepository
    ) {
        this.graph = graph;
        this.configuration = configuration;
        this.fileImport = fileImport;
        this.userRepository = userRepository;
        this.workspaceRepository = workspaceRepository;
        this.longRunningProcessRepository = longRunningProcessRepository;
    }

    @Override
    public boolean isHandled(JSONObject longRunningProcessQueueItem) {
        return longRunningProcessQueueItem.getString("type").equals("org-visallo-ingest-cloud");
    }

    @Override
    protected void processInternal(JSONObject longRunningProcessQueueItem) {
        CloudImportLongRunningProcessQueueItem item = ClientApiConverter.toClientApi(longRunningProcessQueueItem, CloudImportLongRunningProcessQueueItem.class);
        CloudDestination destination = getDestination(item.getDestination());

        if (destination == null) {
            longRunningProcessQueueItem.put("error", "No cloud destination configured for :" + item.getDestination());
        } else {
            try {
                download(destination, item);
            } catch (Exception e) {
                throw new VisalloException("Unable to download from cloud destination", e);
            }
        }
    }

    private CloudDestination getDestination(String className) {
        Collection<CloudDestination> destinations = InjectHelper.getInjectedServices(CloudDestination.class, configuration);
        for (CloudDestination destination : destinations) {
            if (destination.getClass().getName().equals(className)) {
                return destination;
            }
        }
        return null;
    }

    private void download(CloudDestination destination, CloudImportLongRunningProcessQueueItem item) throws Exception {
        Authorizations authorizations = graph.createAuthorizations(item.getAuthorizations());
        String visibilitySource = "";
        User user = userRepository.findById(item.getUserId());
        String conceptId = null;
        Priority priority = Priority.NORMAL;
        Workspace workspace = workspaceRepository.findById(item.getWorkspaceId(), user);
        ClientApiImportProperty[] properties = null;
        boolean queueDefaults = false;
        boolean findExistingByFileHash = true;

        File tempDir = Files.createTempDir();
        try {
            for (CloudDestinationItem cloudDestinationItem : destination.getItems(new JSONObject(item.getConfiguration()))) {
                String fileName = cloudDestinationItem.getName();
                InputStream inputStream = cloudDestinationItem.getInputStream();

                if (fileName == null) throw new VisalloException("Cloud destination item name must not be null");
                if (inputStream == null) throw new VisalloException("Cloud destination input stream must not be null");

                File file = new File(tempDir, cloudDestinationItem.getName());

                // TODO: switch to method with progress
                // https://commons.apache.org/proper/commons-net/apidocs/org/apache/commons/net/io/Util.html
                FileUtils.copyInputStreamToFile(inputStream, file);

                fileImport.importFile(
                        file,
                        queueDefaults,
                        conceptId,
                        properties,
                        visibilitySource,
                        workspace,
                        findExistingByFileHash,
                        priority,
                        user,
                        authorizations
                );
            }
        } finally {
            FileUtils.deleteDirectory(tempDir);
        }
    }

}

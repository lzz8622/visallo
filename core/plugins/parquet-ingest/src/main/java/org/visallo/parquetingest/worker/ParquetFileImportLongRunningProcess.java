package org.visallo.parquetingest.worker;

import com.google.common.collect.Maps;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.tools.read.SimpleReadSupport;
import org.apache.parquet.tools.read.SimpleRecord;
import org.json.JSONObject;
import org.vertexium.Authorizations;
import org.vertexium.Graph;
import org.vertexium.Vertex;
import org.vertexium.property.StreamingPropertyValue;
import org.vertexium.util.StreamUtils;
import org.visallo.core.exception.VisalloException;
import org.visallo.core.model.longRunningProcess.LongRunningProcessRepository;
import org.visallo.core.model.longRunningProcess.LongRunningProcessWorker;
import org.visallo.core.model.ontology.OntologyRepository;
import org.visallo.core.model.properties.VisalloProperties;
import org.visallo.core.model.user.UserRepository;
import org.visallo.core.model.workspace.WorkspaceHelper;
import org.visallo.core.model.workspace.WorkspaceRepository;
import org.visallo.core.security.VisibilityTranslator;
import org.visallo.core.user.User;
import org.visallo.core.util.ClientApiConverter;
import org.visallo.ingestontologymapping.model.ImportStructuredDataQueueItem;
import org.visallo.ingestontologymapping.util.GraphBuilderParserHandler;
import org.visallo.ingestontologymapping.util.ParseOptions;
import org.visallo.ingestontologymapping.util.ProgressReporter;
import org.visallo.ingestontologymapping.util.mapping.ParseMapping;

import javax.inject.Inject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public class ParquetFileImportLongRunningProcess extends LongRunningProcessWorker {
    public static final String TYPE = ParquetFileImportLongRunningProcess.class.getCanonicalName();

    private final OntologyRepository ontologyRepository;
    private final VisibilityTranslator visibilityTranslator;
    private final UserRepository userRepository;
    private final LongRunningProcessRepository longRunningProcessRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceHelper workspaceHelper;
    private final Graph graph;

    @Inject
    public ParquetFileImportLongRunningProcess(OntologyRepository ontologyRepository,
                                                 VisibilityTranslator visibilityTranslator,
                                                 UserRepository userRepository,
                                                 LongRunningProcessRepository longRunningProcessRepository,
                                                 WorkspaceRepository workspaceRepository,
                                                 WorkspaceHelper workspaceHelper,
                                                 Graph graph){
        this.ontologyRepository = ontologyRepository;
        this.visibilityTranslator = visibilityTranslator;
        this.userRepository = userRepository;
        this.longRunningProcessRepository = longRunningProcessRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceHelper = workspaceHelper;
        this.graph = graph;
    }

    @Override
    public boolean isHandled(JSONObject longRunningProcessQueueItem) {
        return TYPE.equals(longRunningProcessQueueItem.optString("type"));
    }

    @Override
    protected void processInternal(JSONObject longRunningProcessQueueItem) {
        ImportStructuredDataQueueItem importStructuredDataQueueItem = ClientApiConverter.toClientApi(longRunningProcessQueueItem.toString(), ImportStructuredDataQueueItem.class);
        ParseMapping parseMapping = new ParseMapping(ontologyRepository, visibilityTranslator, importStructuredDataQueueItem.getWorkspaceId(), importStructuredDataQueueItem.getMapping());
        Authorizations authorizations = graph.createAuthorizations(importStructuredDataQueueItem.getAuthorizations());
        Vertex vertex = graph.getVertex(importStructuredDataQueueItem.getVertexId(), authorizations);
        User user = userRepository.findById(importStructuredDataQueueItem.getUserId());
        StreamingPropertyValue rawPropertyValue = VisalloProperties.RAW.getPropertyValue(vertex);

        ProgressReporter reporter = new ProgressReporter() {
            public void finishedRow(int row, int totalRows) {
                if (totalRows != -1) {
                    longRunningProcessRepository.reportProgress(longRunningProcessQueueItem, ((float) row) / ((float) totalRows), "Row " + row + " of " + totalRows);
                }
            }
        };

        GraphBuilderParserHandler parserHandler = new GraphBuilderParserHandler(
                graph,
                user,
                visibilityTranslator,
                authorizations,
                workspaceRepository,
                workspaceHelper,
                importStructuredDataQueueItem.getWorkspaceId(),
                vertex,
                parseMapping,
                reporter);


        longRunningProcessRepository.reportProgress(longRunningProcessQueueItem, 0, "Deleting previous imports");
        parserHandler.cleanUpExistingImport();

        parserHandler.dryRun = false;
        parserHandler.reset();
        try {
            parse(rawPropertyValue, parserHandler, importStructuredDataQueueItem.getParseOptions(), importStructuredDataQueueItem);
        } catch (Exception e) {
           // throw new VisalloException("unable to parse vertex " + vertex, e);
            e.printStackTrace();
        }
    }

    private void parse(StreamingPropertyValue rawPropertyValue, GraphBuilderParserHandler parserHandler, ParseOptions parseOptions, ImportStructuredDataQueueItem item) {
        try (InputStream in = rawPropertyValue.getInputStream()) {
            File tempFile = File.createTempFile("parquet", "p");
            StreamUtils.copy(in, new FileOutputStream(tempFile));

            ParquetReader<SimpleRecord> reader = ParquetReader.builder(new SimpleReadSupport(), new Path(tempFile.getAbsolutePath())).build();

            parserHandler.newSheet("");
            int rowNum = 0;

            for (SimpleRecord value = reader.read(); value != null; value = reader.read()) {
                Map<String, String> row = Maps.newHashMap();
                for (SimpleRecord.NameValue nameValue : value.getValues()) {
                    row.put(nameValue.getName(), nameValue.getValue().toString());
                }

                parserHandler.addRow(row, rowNum++);
            }
        } catch (IOException e) {
           throw new VisalloException("Error parsing parquet file", e);
        }
    }
}

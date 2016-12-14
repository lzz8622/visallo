package org.visallo.ingestontologymapping.util;

import org.vertexium.*;
import org.visallo.core.exception.VisalloException;
import org.visallo.core.model.graph.GraphRepository;
import org.visallo.core.model.properties.VisalloProperties;
import org.visallo.core.model.properties.types.PropertyMetadata;
import org.visallo.core.model.workQueue.Priority;
import org.visallo.core.model.workspace.Workspace;
import org.visallo.core.model.workspace.WorkspaceHelper;
import org.visallo.core.model.workspace.WorkspaceRepository;
import org.visallo.core.security.VisibilityTranslator;
import org.visallo.core.user.User;
import org.visallo.core.util.SandboxStatusUtil;
import org.visallo.core.util.VisalloLogger;
import org.visallo.core.util.VisalloLoggerFactory;
import org.visallo.ingestontologymapping.StructuredFileOntology;
import org.visallo.ingestontologymapping.model.ParseErrors;
import org.visallo.ingestontologymapping.util.mapping.EdgeMapping;
import org.visallo.ingestontologymapping.util.mapping.ParseMapping;
import org.visallo.ingestontologymapping.util.mapping.PropertyMapping;
import org.visallo.ingestontologymapping.util.mapping.VertexMapping;
import org.visallo.web.clientapi.model.SandboxStatus;
import org.visallo.web.clientapi.model.VisibilityJson;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.visallo.core.model.properties.VisalloProperties.VISIBILITY_JSON_METADATA;

public class GraphBuilderParserHandler extends BaseStructuredFileParserHandler {
    private static final VisalloLogger LOGGER = VisalloLoggerFactory.getLogger(GraphBuilderParserHandler.class);

    private static final String MULTI_KEY = "SFIMPORT";
    private static final String SKIPPED_VERTEX_ID = "SKIPPED_VERTEX";

    private final Graph graph;
    private final User user;
    private final VisibilityTranslator visibilityTranslator;
    private final Authorizations authorizations;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceHelper workspaceHelper;
    private final Workspace workspace;
    private final Vertex structuredFileVertex;
    private final PropertyMetadata propertyMetadata;
    private final VisibilityJson visibilityJson;
    private final Visibility visibility;
    private final ParseMapping parseMapping;
    private final ProgressReporter progressReporter;

    private int sheetNumber = -1;

    public int maxParseErrors = 10;
    public boolean dryRun = true;
    public ParseErrors parseErrors = new ParseErrors();

    public GraphBuilderParserHandler(
            Graph graph,
            User user,
            VisibilityTranslator visibilityTranslator,
            Authorizations authorizations,
            WorkspaceRepository workspaceRepository,
            WorkspaceHelper workspaceHelper,
            String workspaceId,
            Vertex structuredFileVertex,
            ParseMapping parseMapping,
            ProgressReporter progressReporter
    ) {
        this.graph = graph;
        this.user = user;
        this.visibilityTranslator = visibilityTranslator;
        this.authorizations = authorizations;
        this.workspaceRepository = workspaceRepository;
        this.workspaceHelper = workspaceHelper;
        this.workspace = workspaceRepository.findById(workspaceId, user);
        this.structuredFileVertex = structuredFileVertex;
        this.parseMapping = parseMapping;
        this.progressReporter = progressReporter;

        if (workspace == null) {
            throw new VisalloException("Unable to find vertex with ID: " + workspaceId);
        }

        visibilityJson = VisalloProperties.VISIBILITY_JSON.getPropertyValue(structuredFileVertex);
        checkNotNull(visibilityJson);
        visibilityJson.addWorkspace(workspaceId);
        visibility = visibilityTranslator.toVisibility(visibilityJson).getVisibility();

        propertyMetadata = new PropertyMetadata(
                new Date(),
                user,
                GraphRepository.SET_PROPERTY_CONFIDENCE,
                visibilityJson,
                visibilityTranslator.getDefaultVisibility()
        );
    }

    public void reset() {
        parseErrors.errors.clear();
        sheetNumber = -1;
    }

    public boolean hasErrors() {
        return !parseErrors.errors.isEmpty();
    }

    @Override
    public void newSheet(String name) {
        // Right now, it will parse all of the columns in the first sheet since that's
        // what the interface shows. In the future, if they can select a different sheet
        // this code will need to be updated.
        sheetNumber++;
    }

    @Override
    public boolean addRow(Map<String, String> row, int rowNum) {
        Visibility defaultVisibility = visibilityTranslator.getDefaultVisibility();

        // Since we only handle the first sheet currently, bail if this isn't it.
        if (sheetNumber != 0) {
            return false;
        }

        try {
            List<String> newVertexIds = new ArrayList<>();
            List<VertexBuilder> vertexBuilders = new ArrayList<>();
            List<String> workspaceUpdates = new ArrayList<>();
            for (VertexMapping vertexMapping : parseMapping.vertexMappings) {
                VertexBuilder vertexBuilder = createVertex(vertexMapping, row, rowNum);
                if (vertexBuilder != null) {
                    vertexBuilders.add(vertexBuilder);
                    newVertexIds.add(vertexBuilder.getVertexId());
                    workspaceUpdates.add(vertexBuilder.getVertexId());
                } else {
                    newVertexIds.add(SKIPPED_VERTEX_ID);
                }
            }

            List<EdgeBuilderByVertexId> edgeBuilders = new ArrayList<>();
            for (EdgeMapping edgeMapping : parseMapping.edgeMappings) {
                EdgeBuilderByVertexId edgeBuilder = createEdge(edgeMapping, newVertexIds);
                if (edgeBuilder != null) {
                    edgeBuilders.add(edgeBuilder);
                }
            }

            if (!dryRun) {
                for (VertexBuilder vertexBuilder : vertexBuilders) {
                    Vertex newVertex = vertexBuilder.save(authorizations);
                    EdgeBuilder hasSourceEdgeBuilder = graph.prepareEdge(
                            newVertex,
                            structuredFileVertex,
                            StructuredFileOntology.ELEMENT_HAS_SOURCE_IRI,
                            visibility
                    );
                    VisalloProperties.VISIBILITY_JSON.setProperty(hasSourceEdgeBuilder, visibilityJson, defaultVisibility);
                    VisalloProperties.MODIFIED_BY.setProperty(hasSourceEdgeBuilder, user.getUserId(), defaultVisibility);
                    VisalloProperties.MODIFIED_DATE.setProperty(hasSourceEdgeBuilder, new Date(), defaultVisibility);
                    hasSourceEdgeBuilder.save(authorizations);
                }

                for (EdgeBuilderByVertexId edgeBuilder : edgeBuilders) {
                    edgeBuilder.save(authorizations);
                }

                graph.flush();

                if (workspaceUpdates.size() > 0) {
                    workspaceRepository.updateEntitiesOnWorkspace(workspace, workspaceUpdates, user);
                }
            }
        } catch (SkipRowException sre) {
            // Skip the row and keep going
        }

        if (progressReporter != null) {
            progressReporter.finishedRow(rowNum, getTotalRows());
        }

        return !dryRun || maxParseErrors <= 0 || parseErrors.errors.size() < maxParseErrors;
    }

    public boolean canCleanUpExistingImport() {
        Iterable<Vertex> vertices = structuredFileVertex.getVertices(
                Direction.IN,
                StructuredFileOntology.ELEMENT_HAS_SOURCE_IRI,
                authorizations
        );
        for (Vertex vertex : vertices) {
            SandboxStatus sandboxStatus = SandboxStatusUtil.getSandboxStatus(vertex, workspace.getWorkspaceId());
            if (sandboxStatus == SandboxStatus.PUBLIC) {
                return false;
            }
        }
        return true;
    }

    public boolean cleanUpExistingImport() {
        Iterable<Vertex> vertices = structuredFileVertex.getVertices(
                Direction.IN,
                StructuredFileOntology.ELEMENT_HAS_SOURCE_IRI,
                authorizations
        );
        if (canCleanUpExistingImport()) {
            for (Vertex vertex : vertices) {
                workspaceHelper.deleteVertex(
                        vertex,
                        workspace.getWorkspaceId(),
                        false,
                        Priority.HIGH,
                        authorizations,
                        user
                );
            }
        }
        return true;
    }

    private EdgeBuilderByVertexId createEdge(EdgeMapping edgeMapping, List<String> newVertexIds) {
        Visibility defaultVisibility = visibilityTranslator.getDefaultVisibility();
        String inVertexId = newVertexIds.get(edgeMapping.inVertexIndex);
        String outVertexId = newVertexIds.get(edgeMapping.outVertexIndex);

        if (inVertexId.equals(SKIPPED_VERTEX_ID) || outVertexId.equals(SKIPPED_VERTEX_ID)) {
            // TODO: handle edge errors properly?
            return null;
        }

        VisibilityJson edgeVisibilityJson = visibilityJson;
        Visibility edgeVisibility = visibility;
        if (edgeMapping.visibilityJson != null) {
            edgeVisibilityJson = edgeMapping.visibilityJson;
            edgeVisibility = edgeMapping.visibility;
        }

        EdgeBuilderByVertexId m = graph.prepareEdge(outVertexId, inVertexId, edgeMapping.label, edgeVisibility);
        VisalloProperties.VISIBILITY_JSON.setProperty(m, edgeVisibilityJson, defaultVisibility);
        VisalloProperties.MODIFIED_DATE.setProperty(m, propertyMetadata.getModifiedDate(), defaultVisibility);
        VisalloProperties.MODIFIED_BY.setProperty(m, propertyMetadata.getModifiedBy().getUserId(), defaultVisibility);
        return m;
    }

    private VertexBuilder createVertex(VertexMapping vertexMapping, Map<String, String> row, int rowNum) {
        VisibilityJson vertexVisibilityJson = visibilityJson;
        Visibility vertexVisibility = visibility;
        if (vertexMapping.visibilityJson != null) {
            vertexVisibilityJson = vertexMapping.visibilityJson;
            vertexVisibility = vertexMapping.visibility;
        }

        VertexBuilder m = graph.prepareVertex(vertexVisibility);
        Visibility defaultVisibility = visibilityTranslator.getDefaultVisibility();
        VisalloProperties.VISIBILITY_JSON.setProperty(
                m,
                vertexVisibilityJson,
                defaultVisibility
        );

        for (PropertyMapping propertyMapping : vertexMapping.propertyMappings) {
            Metadata metadata = propertyMetadata.createMetadata();
            if (VisalloProperties.CONCEPT_TYPE.getPropertyName().equals(propertyMapping.name)) {
                VisibilityJson propertyVisibilityJson = propertyMapping.visibilityJson;
                if (propertyVisibilityJson == null) {
                    propertyVisibilityJson = vertexVisibilityJson;
                }

                VisalloProperties.CONCEPT_TYPE.setProperty(m, propertyMapping.value, defaultVisibility);
                VisalloProperties.VISIBILITY_JSON.setProperty(m, propertyVisibilityJson, defaultVisibility);
                VisalloProperties.MODIFIED_DATE.setProperty(m, propertyMetadata.getModifiedDate(), defaultVisibility);
                VisalloProperties.MODIFIED_BY.setProperty(m, propertyMetadata.getModifiedBy().getUserId(), defaultVisibility);
            } else {
                try {
                    VisalloProperties.SOURCE_FILE_OFFSET_METADATA.setMetadata(metadata, Long.valueOf(rowNum), defaultVisibility);
                    setPropertyValue(m, row, propertyMapping, vertexVisibility, metadata);
                } catch (Exception e) {
                    LOGGER.error("Error parsing property.", e);

                    ParseErrors.ParseError pe = new ParseErrors.ParseError();
                    pe.rawPropertyValue = propertyMapping.extractRawValue(row);
                    pe.propertyMapping = propertyMapping;
                    pe.message = e.getMessage();
                    pe.sheetIndex = sheetNumber;
                    pe.rowIndex = rowNum;

                    if (!dryRun) {
                        if (propertyMapping.errorHandlingStrategy == PropertyMapping.ErrorHandlingStrategy.SKIP_ROW) {
                            throw new SkipRowException("Error parsing property.", e);
                        } else if (propertyMapping.errorHandlingStrategy == PropertyMapping.ErrorHandlingStrategy.SKIP_VERTEX) {
                            return null;
                        } else if (propertyMapping.errorHandlingStrategy == PropertyMapping.ErrorHandlingStrategy.SET_CELL_ERROR_PROPERTY) {
                            String multiKey = sheetNumber + "_" + rowNum;
                            StructuredFileOntology.ERROR_MESSAGE_PROPERTY.addPropertyValue(
                                    m,
                                    multiKey,
                                    pe.message,
                                    metadata,
                                    vertexVisibility
                            );
                            StructuredFileOntology.RAW_CELL_VALUE_PROPERTY.addPropertyValue(
                                    m,
                                    multiKey,
                                    pe.rawPropertyValue,
                                    metadata,
                                    vertexVisibility
                            );
                            StructuredFileOntology.TARGET_PROPERTY.addPropertyValue(
                                    m,
                                    multiKey,
                                    pe.propertyMapping.name,
                                    metadata,
                                    vertexVisibility
                            );
                            StructuredFileOntology.SHEET_PROPERTY.addPropertyValue(
                                    m,
                                    multiKey,
                                    String.valueOf(sheetNumber),
                                    metadata,
                                    vertexVisibility
                            );
                            StructuredFileOntology.ROW_PROPERTY.addPropertyValue(
                                    m,
                                    multiKey,
                                    String.valueOf(rowNum),
                                    metadata,
                                    vertexVisibility
                            );
                        } else if (propertyMapping.errorHandlingStrategy != PropertyMapping.ErrorHandlingStrategy.SKIP_CELL) {
                            throw new VisalloException("Unhandled mapping error. Please provide a strategry.");
                        }
                    } else if (propertyMapping.errorHandlingStrategy == null) {
                        parseErrors.errors.add(pe);
                    }
                }
            }
        }

        return m;
    }

    private void setPropertyValue(
            VertexBuilder m, Map<String, String> row, PropertyMapping propertyMapping, Visibility vertexVisibility,
            Metadata metadata
    ) throws Exception {
        Visibility propertyVisibility = vertexVisibility;
        if (propertyMapping.visibility != null) {
            propertyVisibility = propertyMapping.visibility;
            VISIBILITY_JSON_METADATA.setMetadata(
                    metadata, propertyMapping.visibilityJson, visibilityTranslator.getDefaultVisibility());
        }

        Object propertyValue = propertyMapping.decodeValue(row);
        if (propertyValue != null) {
            m.addPropertyValue(MULTI_KEY, propertyMapping.name, propertyValue, metadata, propertyVisibility);
        }
    }
}

package org.visallo.parquetingest.worker;

import com.google.common.collect.Lists;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.vertexium.*;
import org.vertexium.property.StreamingPropertyValue;
import org.visallo.core.model.longRunningProcess.LongRunningProcessRepository;
import org.visallo.core.model.ontology.OntologyProperty;
import org.visallo.core.model.ontology.OntologyRepository;
import org.visallo.core.model.properties.VisalloProperties;
import org.visallo.core.model.user.UserRepository;
import org.visallo.core.model.workspace.Workspace;
import org.visallo.core.model.workspace.WorkspaceHelper;
import org.visallo.core.model.workspace.WorkspaceRepository;
import org.visallo.core.security.VisalloVisibility;
import org.visallo.core.security.VisibilityTranslator;
import org.visallo.core.user.User;
import org.visallo.ingestontologymapping.StructuredFileOntology;
import org.visallo.web.clientapi.model.PropertyType;
import org.visallo.web.clientapi.model.VisibilityJson;

import java.io.IOException;
import java.io.InputStream;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ParquetFileImportLongRunningProcessTest {

    private OntologyRepository ontologyRepository;
    private VisibilityTranslator visibilityTranslator;
    private UserRepository userRepository;
    private LongRunningProcessRepository longRunningProcessRepository;
    private WorkspaceRepository workspaceRepository;
    private WorkspaceHelper workspaceHelper;
    private Graph graph;
    private ParquetFileImportLongRunningProcess testSubject;
    private OntologyProperty ontologyProperty;
    private Workspace workspace;
    private VertexBuilder vertexBuilder;
    private User user;
    private EdgeBuilder edgeBuilder;
    private EdgeBuilderByVertexId edgeBuilderByVertexId;

    @Before
    public void before(){
        ontologyRepository = mock(OntologyRepository.class);
        visibilityTranslator = mock(VisibilityTranslator.class);
        userRepository = mock(UserRepository.class);
        longRunningProcessRepository = mock(LongRunningProcessRepository.class);
        workspaceRepository = mock(WorkspaceRepository.class);
        workspaceHelper = mock(WorkspaceHelper.class);
        graph = mock(Graph.class);

        ontologyProperty = mock(OntologyProperty.class);
        when(ontologyProperty.getDataType()).thenReturn(PropertyType.STRING);
        when(ontologyRepository.getPropertyByIRI(anyString())).thenReturn(ontologyProperty);

        workspace = mock(Workspace.class);

        when(workspaceRepository.findById(anyString(), any(User.class))).thenReturn(workspace);
        when(visibilityTranslator.toVisibility(any(VisibilityJson.class))).thenReturn(new VisalloVisibility());

        when(visibilityTranslator.getDefaultVisibility()).thenReturn(Visibility.EMPTY);

        testSubject = new ParquetFileImportLongRunningProcess(
                ontologyRepository,
                visibilityTranslator,
                userRepository,
                longRunningProcessRepository,
                workspaceRepository,
                workspaceHelper,
                graph);
    }

    @Test
    public void test() throws IOException {
        final InputStream resourceAsStream = ParquetFileImportLongRunningProcess.class.getResourceAsStream("/test-parquet-file.snappy.parquet");
        String vertexId = "vertex1";

        Vertex v = mock(Vertex.class);

        when(v.getPropertyValue(eq(VisalloProperties.RAW.getPropertyName()))).thenReturn(new StreamingPropertyValue(){
            @Override
            public InputStream getInputStream() {
                return resourceAsStream;
            }
        });

        when(graph.getVertex(eq(vertexId), any(Authorizations.class))).thenReturn(v);
        when(v.getPropertyValue(VisalloProperties.VISIBILITY_JSON.getPropertyName())).thenReturn(new VisibilityJson());

        vertexBuilder = mock(VertexBuilder.class);
        when(vertexBuilder.getVertexId()).thenReturn("vertexbuild1");
        edgeBuilder = mock(EdgeBuilder.class);
        edgeBuilderByVertexId = mock(EdgeBuilderByVertexId.class);

        when(graph.prepareVertex(any(Visibility.class))).thenReturn(vertexBuilder);
        when(v.getVertices(eq(Direction.IN),eq(StructuredFileOntology.ELEMENT_HAS_SOURCE_IRI), any(Authorizations.class))).thenReturn(Lists.newArrayList());

        user = mock(User.class);
        when(graph.prepareEdge(anyString(), anyString(), anyString(), any(Visibility.class))).thenReturn(edgeBuilderByVertexId);
        when(graph.prepareEdge(any(Vertex.class), any(Vertex.class), eq(StructuredFileOntology.ELEMENT_HAS_SOURCE_IRI), any(Visibility.class))).thenReturn(edgeBuilder);

        when(user.getUserId()).thenReturn("user1");

        when(userRepository.findById(anyString())).thenReturn(user);

        testSubject.processInternal(createObject());
    }

    private JSONObject createObject(){
        final String MAPPING = "{\"vertices\":[{\"visibilitySource\":\"\",\"properties\":[{\"name\":\"http://visallo.org#conceptType\",\"value\":\"http://visallo.org/dev#person\"},{\"name\":\"http://visallo.org/dev#firstName\",\"column\":0,\"visibilitySource\":\"\"},{\"name\":\"http://visallo.org/dev#lastName\",\"column\":1,\"visibilitySource\":\"\"}]},{\"visibilitySource\":\"\",\"properties\":[{\"name\":\"http://visallo.org#conceptType\",\"value\":\"http://visallo.org/dev#organization\"},{\"name\":\"http://visallo.org#title\",\"column\":2,\"visibilitySource\":\"\"}]}],\"edges\":[{\"inVertex\":1,\"outVertex\":0,\"label\":\"http://visallo.org/dev#personIsMemberOfOrganization\",\"visibilitySource\":\"\"}], \"type\":\"S3 Parquet\"}";
        return new JSONObject().put("vertexId", "vertex1").put("mapping", MAPPING);
    }
}

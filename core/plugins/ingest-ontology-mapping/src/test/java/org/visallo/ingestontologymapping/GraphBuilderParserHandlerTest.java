package org.visallo.ingestontologymapping;

import com.beust.jcommander.internal.Lists;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.v5analytics.simpleorm.SimpleOrmSession;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.semanticweb.owlapi.model.IRI;
import org.vertexium.Authorizations;
import org.vertexium.Vertex;
import org.vertexium.VertexBuilder;
import org.vertexium.Visibility;
import org.vertexium.id.QueueIdGenerator;
import org.vertexium.inmemory.InMemoryAuthorizations;
import org.vertexium.inmemory.InMemoryGraph;
import org.vertexium.inmemory.InMemoryGraphConfiguration;
import org.vertexium.search.DefaultSearchIndex;
import org.visallo.core.config.Configuration;
import org.visallo.core.config.HashMapConfigurationLoader;
import org.visallo.core.exception.VisalloException;
import org.visallo.core.model.graph.GraphRepository;
import org.visallo.core.model.lock.NonLockingLockRepository;
import org.visallo.core.model.notification.UserNotificationRepository;
import org.visallo.core.model.ontology.OntologyRepository;
import org.visallo.core.model.properties.VisalloProperties;
import org.visallo.core.model.termMention.TermMentionRepository;
import org.visallo.core.model.user.*;
import org.visallo.core.model.workQueue.WorkQueueRepository;
import org.visallo.core.model.workspace.WorkspaceDiffHelper;
import org.visallo.core.model.workspace.WorkspaceHelper;
import org.visallo.core.model.workspace.WorkspaceRepository;
import org.visallo.core.security.DirectVisibilityTranslator;
import org.visallo.core.security.VisibilityTranslator;
import org.visallo.core.user.User;
import org.visallo.ingestontologymapping.model.ParseErrors;
import org.visallo.ingestontologymapping.util.GraphBuilderParserHandler;
import org.visallo.ingestontologymapping.util.mapping.ParseMapping;
import org.visallo.ingestontologymapping.util.mapping.PropertyMapping;
import org.visallo.vertexium.model.ontology.InMemoryOntologyRepository;
import org.visallo.vertexium.model.user.VertexiumUserRepository;
import org.visallo.vertexium.model.workspace.VertexiumWorkspaceRepository;
import org.visallo.web.clientapi.model.VisibilityJson;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.visallo.ingestontologymapping.mapping.MappingTestHelpers.createIndexedMap;

@SuppressWarnings("ConstantConditions")
@RunWith(MockitoJUnitRunner.class)
public class GraphBuilderParserHandlerTest {
    private static final String WORKSPACE_ID = "testWorkspaceId";

    private static final String OWL_BASE_URI = "http://visallo.org/structured-file-test";
    private static final String PERSON_CONCEPT_TYPE = OWL_BASE_URI + "#person";
    private static final String TX_CONCEPT_TYPE = OWL_BASE_URI + "#transaction";
    private static final String PERSON_NAME_NAME = OWL_BASE_URI + "#name";
    private static final String TX_DATE_NAME = OWL_BASE_URI + "#transactionDate";
    private static final String TX_FRAUD_NAME = OWL_BASE_URI + "#suspectedFraud";

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    @Mock
    private SimpleOrmSession simpleOrmSession;

    @Mock
    private UserSessionCounterRepository userSessionCounterRepository;

    @Mock
    private WorkQueueRepository workQueueRepository;

    @Mock
    private UserNotificationRepository userNotificationRepository;

    @Mock
    private WorkspaceDiffHelper workspaceDiff;

    private QueueIdGenerator idGenerator = new QueueIdGenerator();

    Authorizations authorizations = new InMemoryAuthorizations(WORKSPACE_ID);

    private InMemoryGraph graph;

    private ParseMapping parseMapping;

    private GraphBuilderParserHandler parserHandler;

    private UserPropertyAuthorizationRepository authorizationRepository;
    private UserPropertyPrivilegeRepository privilegeRepository;
    private GraphRepository graphRepository;

    @Before
    public void before() throws Exception {
        InMemoryGraphConfiguration config = new InMemoryGraphConfiguration(new HashMap<>());
        graph = InMemoryGraph.create(config, idGenerator, new DefaultSearchIndex(config));
        HashMap configMap = new HashMap();
        configMap.put("org.visallo.core.model.user.UserPropertyAuthorizationRepository.defaultAuthorizations", "");
        Configuration visalloConfiguration = new HashMapConfigurationLoader(configMap).createConfiguration();
        NonLockingLockRepository lockRepository = new NonLockingLockRepository();

        OntologyRepository ontologyRepository = new InMemoryOntologyRepository(
                graph,
                visalloConfiguration,
                lockRepository
        );
        GraphAuthorizationRepository graphAuthorizationRepository = new InMemoryGraphAuthorizationRepository();

        authorizationRepository = new UserPropertyAuthorizationRepository(
                graph,
                ontologyRepository,
                visalloConfiguration,
                userNotificationRepository,
                workQueueRepository,
                graphAuthorizationRepository
        );

        privilegeRepository = new UserPropertyPrivilegeRepository(
                ontologyRepository,
                visalloConfiguration,
                userNotificationRepository,
                workQueueRepository
        ) {
            @Override
            protected Iterable<PrivilegesProvider> getPrivilegesProviders(Configuration configuration) {
                return Lists.newArrayList();
            }
        };

        VertexiumUserRepository userRepository = new VertexiumUserRepository(
                visalloConfiguration,
                simpleOrmSession,
                graphAuthorizationRepository,
                graph,
                ontologyRepository,
                userSessionCounterRepository,
                workQueueRepository,
                lockRepository,
                authorizationRepository,
                privilegeRepository
        );

        VisibilityTranslator visibilityTranslator = new DirectVisibilityTranslator();

        TermMentionRepository termMentionRepository = new TermMentionRepository(graph, graphAuthorizationRepository);
        graphRepository = new GraphRepository(
                graph,
                visibilityTranslator,
                termMentionRepository,
                workQueueRepository
        );
        WorkspaceRepository workspaceRepository = new VertexiumWorkspaceRepository(
                graph,
                visalloConfiguration,
                graphRepository,
                userRepository,
                graphAuthorizationRepository,
                workspaceDiff,
                lockRepository,
                visibilityTranslator,
                termMentionRepository,
                ontologyRepository,
                workQueueRepository,
                authorizationRepository
        );

        WorkspaceHelper workspaceHelper = new WorkspaceHelper(
                termMentionRepository,
                workQueueRepository,
                graph,
                ontologyRepository,
                workspaceRepository,
                privilegeRepository,
                authorizationRepository
        );

        byte[] inFileData = IOUtils.toByteArray(this.getClass().getResourceAsStream("sample.owl"));
        ontologyRepository.importFileData(
                inFileData,
                IRI.create("http://visallo.org/structured-file-test"),
                null,
                authorizations
        );

        idGenerator.push("SFVERTEX");
        VertexBuilder structuredFileVertexBuilder = graph.prepareVertex(visibilityTranslator.getDefaultVisibility());
        VisibilityJson visibilityJson = VisibilityJson.updateVisibilitySourceAndAddWorkspaceId(new VisibilityJson(), "", WORKSPACE_ID);
        VisalloProperties.VISIBILITY_JSON.setProperty(structuredFileVertexBuilder, visibilityJson, new Visibility(""));
        Vertex structuredFileVertex = structuredFileVertexBuilder.save(authorizations);

        idGenerator.push("JUNIT_USER");
        idGenerator.push("DEFAULT_WORKSPACE");
        User user = userRepository.findOrAddUser(
                "junit",
                "JUnit",
                "junit@v5analytics.com",
                "password"
        );
        workspaceRepository.add(WORKSPACE_ID, "Default Junit", user);

        InputStream parseMappingJson = this.getClass().getResourceAsStream("parsemapping.json");
        parseMapping = new ParseMapping(ontologyRepository, null, null, IOUtils.toString(parseMappingJson, "UTF-8"));
        parserHandler = new GraphBuilderParserHandler(
                graph,
                user,
                visibilityTranslator,
                graph.createAuthorizations(WORKSPACE_ID),
                workspaceRepository,
                workspaceHelper,
                WORKSPACE_ID,
                structuredFileVertex,
                parseMapping,
                null
        );

        parserHandler.newSheet("SheetA");

        idGenerator.push("PERSON_VERTEX");
        idGenerator.push("TX_VERTEX");
        idGenerator.push("PERSON_HAS_TX_EDGE");
    }

    @Test
    public void testAddRow() throws Exception {
        doParse(false, true, 0, new String[]{"John Smith", "3/13/2015", "yes"});

        Iterable<Vertex> vertices = graph.getVertices(authorizations);
        assertEquals("Expected new vertices to be created", 3, Iterables.size(vertices)); // CSV, PERSON, TX

        Vertex personVertex = graph.getVertex("PERSON_VERTEX", authorizations);
        assertNotNull("Unable to find new person vertex", personVertex);
        assertEquals(
                "Incorrect concept type on person vertex",
                PERSON_CONCEPT_TYPE,
                VisalloProperties.CONCEPT_TYPE.getPropertyValue(personVertex)
        );
        assertEquals("Person name not set properly", "John Smith", personVertex.getPropertyValue(PERSON_NAME_NAME));

        Vertex txVertex = graph.getVertex("TX_VERTEX", authorizations);
        assertNotNull("Unable to find new transaction vertex", txVertex);
        assertEquals(
                "Incorrect concept type on tx vertex",
                TX_CONCEPT_TYPE,
                VisalloProperties.CONCEPT_TYPE.getPropertyValue(txVertex)
        );
        assertEquals(
                "Incorrect transaction date on tx vertex",
                "2015-03-13",
                dateFormat.format(txVertex.getPropertyValue(TX_DATE_NAME))
        );
        assertEquals("Incorrect fraud indicator on tx vertex", Boolean.TRUE, txVertex.getPropertyValue(TX_FRAUD_NAME));
    }

    @Test
    public void testAddRowDryRun() throws Exception {
        doParse(true, true, 0, new String[]{"John Smith", "3/13/2015", "yes"});

        Iterable<Vertex> vertices = graph.getVertices(authorizations);
        assertEquals("Expected no new vertices to be created", 1, Iterables.size(vertices)); // CSV only
    }

    @Test
    public void testAddRowWithTooManyErrors() throws Exception {
        parserHandler.maxParseErrors = 1;

        doParse(true, false, 1, new String[]{"John Smith", "3/13/2015", "you bet"});

        Iterable<Vertex> vertices = graph.getVertices(authorizations);
        assertEquals("Expected no new vertices to be created", 1, Iterables.size(vertices)); // CSV only
    }

    @Test
    public void testAddRowWithUnhandledError() throws Exception {
        try {
            doParse(false, false, 1, new String[]{"John Smith", "3/13/2015", "you bet"});
            fail("An exception should have been thrown.");
        } catch (VisalloException ve) {
            // we expect this
        }
    }

    @Test
    public void testAddRowWithErrorThatSkipsCell() throws Exception {
        PropertyMapping fraudMapping = findPropertyMapping(TX_FRAUD_NAME);
        fraudMapping.errorHandlingStrategy = PropertyMapping.ErrorHandlingStrategy.SKIP_CELL;

        doParse(false, true, 0, new String[]{"John Smith", "3/13/2015", "you bet"});

        Vertex txVertex = graph.getVertex("TX_VERTEX", authorizations);
        assertNull("Incorrect fraud indicator on tx vertex", txVertex.getPropertyValue(TX_FRAUD_NAME));
    }

    @Test
    public void testAddRowWithErrorThatSkipsVertex() throws Exception {
        PropertyMapping fraudMapping = findPropertyMapping(TX_FRAUD_NAME);
        fraudMapping.errorHandlingStrategy = PropertyMapping.ErrorHandlingStrategy.SKIP_VERTEX;

        doParse(false, true, 0, new String[]{"John Smith", "3/13/2015", "you bet"});

        Iterable<Vertex> vertices = graph.getVertices(authorizations);
        assertEquals("Expected new vertices to be created", 2, Iterables.size(vertices)); // CSV, PERSON

        Vertex personVertex = graph.getVertex("PERSON_VERTEX", authorizations);
        assertNotNull("Unable to find new person vertex", personVertex);

        Vertex txVertex = graph.getVertex("TX_VERTEX", authorizations);
        assertNull("Should not have found the transaction vertex", txVertex);
    }

    @Test
    public void testAddRowWithErrorThatSkipsRow() throws Exception {
        PropertyMapping fraudMapping = findPropertyMapping(TX_FRAUD_NAME);
        fraudMapping.errorHandlingStrategy = PropertyMapping.ErrorHandlingStrategy.SKIP_ROW;

        doParse(false, true, 0, new String[]{"John Smith", "3/13/2015", "you bet"});

        Iterable<Vertex> vertices = graph.getVertices(authorizations);
        assertEquals("Expected new vertices to be created", 1, Iterables.size(vertices)); // CSV, PERSON

        Vertex personVertex = graph.getVertex("PERSON_VERTEX", authorizations);
        assertNull("Should not have found new person vertex", personVertex);

        Vertex txVertex = graph.getVertex("TX_VERTEX", authorizations);
        assertNull("Should not have found the transaction vertex", txVertex);
    }

    @Test
    public void testAddRowWithErrorThatSetsErrorProperty() throws Exception {
        PropertyMapping fraudMapping = findPropertyMapping(TX_FRAUD_NAME);
        fraudMapping.errorHandlingStrategy = PropertyMapping.ErrorHandlingStrategy.SET_CELL_ERROR_PROPERTY;

        doParse(false, true, 0, new String[]{"John Smith", "3/13/2015", "you bet"});

        Vertex txVertex = graph.getVertex("TX_VERTEX", authorizations);
        assertNotNull("Unable to find new transaction vertex", txVertex);

        String multiKey = "0_0";
        assertEquals(
                "Incorrect error message on tx vertex",
                "Unrecognized boolean value: you bet",
                StructuredFileOntology.ERROR_MESSAGE_PROPERTY.getPropertyValue(txVertex, multiKey)
        );
        assertEquals(
                "Incorrect raw value on tx vertex",
                "you bet",
                StructuredFileOntology.RAW_CELL_VALUE_PROPERTY.getPropertyValue(txVertex, multiKey)
        );
        assertEquals(
                "Incorrect target property on tx vertex",
                TX_FRAUD_NAME,
                StructuredFileOntology.TARGET_PROPERTY.getPropertyValue(txVertex, multiKey)
        );
        assertEquals(
                "Incorrect sheet on tx vertex",
                "0",
                StructuredFileOntology.SHEET_PROPERTY.getPropertyValue(txVertex, multiKey)
        );
        assertEquals(
                "Incorrect row on tx vertex",
                "0",
                StructuredFileOntology.ROW_PROPERTY.getPropertyValue(txVertex, multiKey)
        );
    }

    @Test
    public void testAddRowWithTooManyErrorsButNotDryRun() throws Exception {
        parserHandler.maxParseErrors = 1;

        PropertyMapping fraudMapping = findPropertyMapping(TX_FRAUD_NAME);
        fraudMapping.errorHandlingStrategy = PropertyMapping.ErrorHandlingStrategy.SKIP_CELL;

        doParse(false, true, 0, new String[]{"John Smith", "3/13/2015", "you bet"});

        Iterable<Vertex> vertices = graph.getVertices(authorizations);
        assertEquals("Expected new vertices to be created", 3, Iterables.size(vertices)); // CSV, PERSON, TX

        Vertex txVertex = graph.getVertex("TX_VERTEX", authorizations);
        assertNotNull("Unable to find new transaction vertex", txVertex);
        assertEquals(
                "Incorrect concept type on tx vertex",
                TX_CONCEPT_TYPE,
                VisalloProperties.CONCEPT_TYPE.getPropertyValue(txVertex)
        );
        assertEquals(
                "Incorrect transaction date on tx vertex",
                "2015-03-13",
                dateFormat.format(txVertex.getPropertyValue(TX_DATE_NAME))
        );
        assertNull(
                "Malformed boolean property value should not have been set",
                txVertex.getPropertyValue(TX_FRAUD_NAME)
        );
    }

    @Test
    public void testAddRowMaxErrorsDisabled() throws Exception {
        parserHandler.maxParseErrors = -1;
        for (int i = 0; i < 100; i++) {
            parserHandler.parseErrors.errors.add(new ParseErrors.ParseError());
        }

        doParse(true, true, 101, new String[]{"John Smith", "3/13/2015", "you bet"});

        Iterable<Vertex> vertices = graph.getVertices(authorizations);
        assertEquals("Expected no new vertices to be created", 1, Iterables.size(vertices)); // CSV only
    }

    @Test
    public void testAddRowThatErrorsAreRecordedProperlyInDryRun() throws Exception {
        doParse(true, true, 2, new String[]{"John Smith", "SUNDAY", "you bet"});

        ParseErrors.ParseError dateError = parserHandler.parseErrors.errors.get(0);
        assertEquals(TX_DATE_NAME, dateError.propertyMapping.name);
        assertEquals("SUNDAY", dateError.rawPropertyValue);
        assertEquals(0, dateError.rowIndex);
        assertEquals(0, dateError.sheetIndex);
        assertEquals("Unrecognized date value: SUNDAY", dateError.message);

        ParseErrors.ParseError booleanError = parserHandler.parseErrors.errors.get(1);
        assertEquals(TX_FRAUD_NAME, booleanError.propertyMapping.name);
        assertEquals("you bet", booleanError.rawPropertyValue);
        assertEquals(0, booleanError.rowIndex);
        assertEquals(0, booleanError.sheetIndex);
        assertEquals("Unrecognized boolean value: you bet", booleanError.message);

        Iterable<Vertex> vertices = graph.getVertices(authorizations);
        assertEquals("Expected no new vertices to be created", 1, Iterables.size(vertices)); // CSV only
    }

    @Test
    public void testCleanUpExistingImport() throws Exception {
        doParse(false, true, 0, new String[]{"John Smith", "3/13/2015", "yes"});

        Iterable<Vertex> vertices = graph.getVertices(authorizations);
        assertEquals("Expected new vertices to be created", 3, Iterables.size(vertices)); // CSV, PERSON, TX

        boolean cleanupResult = parserHandler.cleanUpExistingImport();

        assertTrue("Expected the result of cleaning up to be success", cleanupResult);

        vertices = graph.getVertices(authorizations);
        assertEquals("Expected new vertices to be created", 1, Iterables.size(vertices)); // CSV only
        assertEquals(
                "Only remaining vertex should be the structured file vertex",
                "SFVERTEX",
                Iterables.get(vertices, 0).getId()
        );
    }

    private PropertyMapping findPropertyMapping(String name) {
        for (int i = 0; i < parseMapping.vertexMappings.size(); i++) {
            for (int j = 0; j < parseMapping.vertexMappings.get(i).propertyMappings.size(); j++) {
                PropertyMapping propertyMapping = parseMapping.vertexMappings.get(i).propertyMappings.get(j);
                if (name.equals(propertyMapping.name)) {
                    return propertyMapping;
                }
            }
        }
        fail("Unable to find fraud property mapping: " + name);
        return null;
    }

    private void doParse(boolean dryRun, boolean expectedKeepGoing, int expectedErrors, String[] rowValues) {
        if (!dryRun) {
            parserHandler.dryRun = false;
            idGenerator.push("PERSON_CSV_EDGE");
            idGenerator.push("TX_CSV_EDGE");
        }

        Map<String, String> row = createIndexedMap(rowValues);

        boolean keepGoing = parserHandler.addRow(row, 0);

        assertEquals("Incorrect return value from parserHandler.addRow", expectedKeepGoing, keepGoing);
        assertEquals(
                "Incorrect number of parsing errors recorded.",
                expectedErrors,
                parserHandler.parseErrors.errors.size()
        );
    }
}

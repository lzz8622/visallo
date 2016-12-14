package org.visallo.parquetingest.routes;

import com.v5analytics.webster.ParameterizedHandler;
import com.v5analytics.webster.annotations.Handle;
import com.v5analytics.webster.annotations.Optional;
import com.v5analytics.webster.annotations.Required;
import org.json.JSONArray;
import org.json.JSONObject;
import org.vertexium.Authorizations;
import org.visallo.core.model.longRunningProcess.LongRunningProcessRepository;
import org.visallo.core.user.User;
import org.visallo.ingestontologymapping.model.ImportStructuredDataQueueItem;
import org.visallo.ingestontologymapping.util.ParseOptions;
import org.visallo.parquetingest.worker.ParquetFileImportLongRunningProcess;
import org.visallo.web.VisalloResponse;
import org.visallo.web.clientapi.model.ClientApiObject;
import org.visallo.web.parameterProviders.ActiveWorkspaceId;

import javax.inject.Inject;

public class ParquetFileAnalyze implements ParameterizedHandler {
    private LongRunningProcessRepository longRunningProcessRepository;

    @Inject
    public ParquetFileAnalyze(LongRunningProcessRepository longRunningProcessRepository) {
        this.longRunningProcessRepository = longRunningProcessRepository;
    }

    @Handle
    public ClientApiObject handle(
            User user,
            @ActiveWorkspaceId String workspaceId,
            Authorizations authorizations,
            @Required(name = "graphVertexId") String graphVertexId,
            @Required(name = "mapping") String mapping,
            @Optional(name = "parseOptions") String optionsJson
    ) throws Exception {
//        mapping = createMapping(); Remove this, only there for testing
        ImportStructuredDataQueueItem queueItem = new ImportStructuredDataQueueItem(workspaceId, mapping, user.getUserId(), graphVertexId, ParquetFileImportLongRunningProcess.TYPE, new ParseOptions(optionsJson), authorizations);
        this.longRunningProcessRepository.enqueue(queueItem.toJson(), user, authorizations);
        return VisalloResponse.SUCCESS;
    }

    //Only for testing
    private static String createMapping(){
        return new JSONObject()
                .put("vertices", new JSONArray()
                        .put(withVisConstant(toArrayInKey("properties",
                                conceptTypeMap("http://visallo.org/dev#person"),
                                mapProp("http://visallo.org/dev#street1", "str_address_1"),
                                mapConstant("http://visallo.org/dev#street2", " "),
                                mapProp("http://visallo.org/dev#cityProp", "city"),
                                mapProp("http://visallo.org/dev#stateProp", " "),
                                mapProp("http://visallo.org/dev#zipCodeProp", "zip5"),
                                mapProp("http://visallo.org/dev#firstName", "social_security_number"))))
                        .put(withVisConstant(toArrayInKey("properties",
                                conceptTypeMap("http://visallo.org/dev#emailAddress"),
                                mapProp("http://visallo.org#title", "email_address")))))
                .put("edges", new JSONArray()
                        .put(edge(1, 0, "http://visallo.org/dev#hasEmailAddress"))).toString();
    }

    private static JSONObject edge(int inVertex, int outVertex, String label){
        return withVisConstant(new JSONObject().put("inVertex", inVertex)
                .put("outVertex", outVertex)
                .put("label", label));
    }

    private static JSONObject toArrayInKey(String key, JSONObject... objs){
        JSONArray arr = new JSONArray();
        for(JSONObject obj : objs){
            arr.put(obj);
        }

        return new JSONObject().put(key, arr);
    }

    private static JSONObject mapProp(String name, String key){
        return withVisConstant(new JSONObject().put("name", name)
                .put("key", key));
    }

    private static JSONObject mapConstant(String name, String value){
        return withVisConstant(new JSONObject().put("name", name)
                .put("value", value));
    }

    private static JSONObject conceptTypeMap(String conceptIri){
        return new JSONObject().put("name", "http://visallo.org#conceptType")
                .put("value", conceptIri);
    }

    private static JSONObject withVisConstant(JSONObject obj){
        return obj.put("visibilitySource", "");
    }
}
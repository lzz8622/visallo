package org.visallo.ingestontologymapping.mapping;

import com.google.common.collect.Maps;

import java.util.Map;

public class MappingTestHelpers {
    public static Map<String, String> createIndexedMap(String... values){
        Map<String, String> map = Maps.newHashMap();
        for(int i = 0; i < values.length; i++){
            map.put("" + i, values[i]);
        }
        return map;
    }
}

/*
 * Licensed to Gisaïa under one or more contributor
 * license agreements. See the NOTICE.txt file distributed with
 * this work for additional information regarding copyright
 * ownership. Gisaïa licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.arlas.server.dao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.arlas.server.app.INSPIREConfiguration;
import io.arlas.server.app.OGCConfiguration;
import io.arlas.server.exceptions.*;
import io.arlas.server.model.*;
import io.arlas.server.model.OgcInspireConfigurationParameters;
import io.arlas.server.model.enumerations.AccessConstraintEnum;
import io.arlas.server.model.enumerations.InspireAccessClassificationEnum;
import io.arlas.server.utils.BoundingBox;
import io.arlas.server.utils.CheckParams;
import io.arlas.server.utils.ESTool;
import org.apache.logging.log4j.core.util.IOUtils;
import org.elasticsearch.action.admin.indices.mapping.get.GetFieldMappingsResponse;
import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsResponse;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.geo.builders.CoordinatesBuilder;
import org.elasticsearch.common.geo.builders.PolygonBuilder;
import org.elasticsearch.common.geo.builders.ShapeBuilder;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class ElasticCollectionReferenceDaoImpl implements CollectionReferenceDao {


    Client client = null;
    String arlasIndex = null;
    private static LoadingCache<String, CollectionReference> collections = null;
    private static ObjectMapper mapper;
    private static ObjectReader reader;
    private static final String ARLAS_MAPPING_FILE_NAME = "arlas.mapping.json";
    private static final String ARLAS_INDEX_MAPPING_NAME = "collection";


    static {
        mapper = new ObjectMapper();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES);
        reader = mapper.readerFor(CollectionReferenceParameters.class);
    }

    public ElasticCollectionReferenceDaoImpl(Client client, String arlasIndex, int arlasCacheSize, int arlasCacheTimeout) {
        super();
        this.client = client;
        this.arlasIndex = arlasIndex;
        collections = CacheBuilder.newBuilder()
                .maximumSize(arlasCacheSize)
                .expireAfterWrite(arlasCacheTimeout, TimeUnit.SECONDS)
                .build(
                        new CacheLoader<String, CollectionReference>() {
                            public CollectionReference load(String ref) throws ArlasException {
                                return ESTool.getCollectionReferenceFromES(client, arlasIndex, ARLAS_INDEX_MAPPING_NAME, reader, ref);
                            }
                        });
    }

    @Override
    public void initCollectionDatabase() {
        try {
            client.admin().indices().prepareGetIndex().setIndices(arlasIndex).get();
            ESTool.putExtendedMapping(client, arlasIndex, ARLAS_INDEX_MAPPING_NAME, this.getClass().getClassLoader().getResourceAsStream(ARLAS_MAPPING_FILE_NAME));
        } catch (IndexNotFoundException e) {
            ESTool.createArlasIndex(client, arlasIndex, ARLAS_INDEX_MAPPING_NAME, ARLAS_MAPPING_FILE_NAME);
        }
    }

    @Override
    public CollectionReference getCollectionReference(String ref) throws ArlasException {
        try {
            return collections.get(ref);
        } catch (ExecutionException e) {
            throw new NotFoundException("Collection " + ref + " not found.", e);
        }
    }

    @Override
    public List<CollectionReference> getAllCollectionReferences() throws ArlasException {
        List<CollectionReference> collections = new ArrayList<>();

        try {
            QueryBuilder qb = QueryBuilders.matchAllQuery();
            //Exclude old include_fields for support old collection
            SearchResponse scrollResp = client.prepareSearch(arlasIndex).setFetchSource(null, "include_fields")
                    .addSort(FieldSortBuilder.DOC_FIELD_NAME, SortOrder.ASC).setScroll(new TimeValue(60000))
                    .setQuery(qb).setSize(100).get(); // max of 100 hits will be returned for each scroll
            do {
                for (SearchHit hit : scrollResp.getHits().getHits()) {
                    String source = hit.getSourceAsString();
                    try {
                        collections.add(new CollectionReference(hit.getId(), reader.readValue(source)));
                    } catch (IOException e) {
                        throw new InternalServerErrorException("Can not fetch collection", e);
                    }
                }
                scrollResp = client.prepareSearchScroll(scrollResp.getScrollId()).setScroll(new TimeValue(60000))
                        .execute().actionGet();
            }
            while (scrollResp.getHits().getHits().length != 0); // Zero hits mark the end of the scroll and the while loop.
        } catch (IndexNotFoundException e) {
            throw new InternalServerErrorException("Unreachable collections", e);
        }
        return collections;
    }

    @Override
    public void deleteCollectionReference(String ref) throws NotFoundException, InternalServerErrorException {
        DeleteResponse response = client.prepareDelete(arlasIndex, "collection", ref).get();
        if (response.status().equals(RestStatus.NOT_FOUND)) {
            throw new NotFoundException("collection " + ref + " not found.");
        } else if (!response.status().equals(RestStatus.OK)) {
            throw new InternalServerErrorException("Unable to delete collection : " + response.status().toString());
        } else {
            //explicit clean-up cache
            collections.invalidate(ref);
            collections.cleanUp();
        }
    }

    private void checkCollectionReferenceParameters(CollectionReference collectionReference) throws ArlasException {
        GetMappingsResponse response;
        try {
            //check index
            response = client.admin().indices().prepareGetMappings(collectionReference.params.indexName)/*.setTypes(collectionReference.params.typeName)*/.get();
            if (response.getMappings().isEmpty()) {
                throw new NotFoundException("No types in " + collectionReference.params.indexName + ".");
            }

            //get fields
            List<String> fields = new ArrayList<>();
            if (collectionReference.params.idPath != null)
                fields.add(collectionReference.params.idPath);
            if (collectionReference.params.geometryPath != null)
                fields.add(collectionReference.params.geometryPath);
            if (collectionReference.params.centroidPath != null)
                fields.add(collectionReference.params.centroidPath);
            if (collectionReference.params.timestampPath != null)
                fields.add(collectionReference.params.timestampPath);
            if(collectionReference.params.excludeFields!=null && collectionReference.params.excludeFields!=""){
                List<String> excludeField = Arrays.asList(collectionReference.params.excludeFields.split(","));
                CheckParams.checkExcludeField(excludeField, fields);
            }

            try {
                AccessConstraintEnum.valueOf(collectionReference.params.inspire.inspireLimitationAccess.accessConstraints);
            } catch (IllegalArgumentException e) {
                String listOfAccessConstraintEnum = "";
                for (AccessConstraintEnum ace : AccessConstraintEnum.values()) {
                    listOfAccessConstraintEnum += "'" + ace.name() + "', ";
                }
                throw new InvalidParameterException("accessConstraints is invalid. Please choose one of : " + listOfAccessConstraintEnum);
            }
            try {
                InspireAccessClassificationEnum.valueOf(collectionReference.params.inspire.inspireLimitationAccess.classification);
            } catch (IllegalArgumentException e) {
                String listOfClassificationEnum = "";
                for (InspireAccessClassificationEnum iace : InspireAccessClassificationEnum.values()) {
                    listOfClassificationEnum += "'" + iace.name() + "', ";
                }
                throw new InvalidParameterException("Inspire Access Classification is invalid. Please choose one of : " + listOfClassificationEnum);
            }


            Iterator<String> indeces = response.getMappings().keysIt();
            while (indeces.hasNext()) {
                String index = indeces.next();
                //check type
                try {
                    if (!response.getMappings().get(index).containsKey(collectionReference.params.typeName)) {
                        throw new NotFoundException("Type " + collectionReference.params.typeName + " does not exist in " + collectionReference.params.indexName + ".");
                    }
                    Object properties = response.getMappings().get(index).get(collectionReference.params.typeName).sourceAsMap().get("properties");
                    if (properties == null) {
                        throw new NotFoundException("Unable to find properties from " + collectionReference.params.typeName + " in " + index + ".");
                    }
                } catch (Exception e) {
                    throw new NotFoundException("Unable to get " + collectionReference.params.typeName + " in " + index + ".");
                }

                //check fields
                if (!fields.isEmpty()) {
                    ESTool.checkIndexMappingFields(client, index, collectionReference.params.typeName, fields.toArray(new String[fields.size()]));
                    setTimestampFormatOfCollectionReference(index, collectionReference.params);
                }


            }
        } catch (ArlasException e) {
            throw e;
        } catch (IndexNotFoundException e) {
            throw new NotFoundException("Index " + collectionReference.params.indexName + " does not exist.");
        } catch (Exception e) {
            throw new NotFoundException("Unable to access " + collectionReference.params.typeName + " in " + collectionReference.params.indexName + ".");
        }
    }

    @Override
    public CollectionReference putCollectionReference(CollectionReference collectionReference) throws ArlasException {
        checkCollectionReferenceParameters(collectionReference);
        setDefaultInspireParameters(collectionReference);
        setDefaultDublinCoreParameters(collectionReference);
        IndexResponse response = null;
        try {
            response = client.prepareIndex(arlasIndex, "collection", collectionReference.collectionName)
                    .setSource(mapper.writeValueAsString(collectionReference.params), XContentType.JSON).get();
        } catch (JsonProcessingException e) {
            new InternalServerErrorException("Can not put collection " + collectionReference.collectionName, e);
        }

        if (response.status().getStatus() != RestStatus.OK.getStatus()
                && response.status().getStatus() != RestStatus.CREATED.getStatus()) {
            throw new InternalServerErrorException("Unable to index collection : " + response.status().toString());
        } else {
            //explicit clean-up cache
            collections.invalidate(collectionReference.collectionName);
            collections.cleanUp();

            return collectionReference;
        }
    }

    private void setDefaultInspireParameters(CollectionReference collectionReference) {
        if (collectionReference.params.inspire == null) {
            collectionReference.params.inspire = new Inspire();
        }
        if (collectionReference.params.inspire.inspireURI == null) {
            collectionReference.params.inspire.inspireURI = new InspireURI();
        }
        if (Strings.isNullOrEmpty(collectionReference.params.inspire.inspireURI.code)) {
            collectionReference.params.inspire.inspireURI.code = "ARLAS.INSPIRE." + collectionReference.collectionName.toUpperCase();
        }
        if (Strings.isNullOrEmpty(collectionReference.params.inspire.inspireURI.namespace)) {
            collectionReference.params.inspire.inspireURI.namespace = "ARLAS.INSPIRE." + collectionReference.collectionName.toUpperCase();
        }
    }

    private void setDefaultDublinCoreParameters(CollectionReference collectionReference) {
        if (Strings.isNullOrEmpty(collectionReference.params.dublinCoreElementName.title)) {
            collectionReference.params.dublinCoreElementName.title = DublinCoreElementName.DEFAULT_TITLE + " for " + collectionReference.collectionName;
        }
        if (Strings.isNullOrEmpty(collectionReference.params.dublinCoreElementName.description)) {
            collectionReference.params.dublinCoreElementName.description = DublinCoreElementName.DEFAULT_DESCRIPTION + " for " + collectionReference.collectionName;
        }
    }

    private void setTimestampFormatOfCollectionReference(String index, CollectionReferenceParameters collectionRefParams) {
        String timestampField = collectionRefParams.timestampPath;
        String[] timestampFieldArray = {timestampField};
        GetFieldMappingsResponse response = client.admin().indices().prepareGetFieldMappings(index).setTypes(collectionRefParams.typeName).setFields(timestampFieldArray).get();
        GetFieldMappingsResponse.FieldMappingMetaData data = response.fieldMappings(index, collectionRefParams.typeName, timestampField);
        if (data != null) {
            String[] fields = timestampField.split("\\.");
            String field = fields[fields.length - 1];
            LinkedHashMap<String, Object> timestampMD = (LinkedHashMap) data.sourceAsMap().get(field);
            collectionRefParams.customParams = new HashMap<>();
            if (timestampMD.keySet().contains("format")) {
                collectionRefParams.customParams.put(CollectionReference.TIMESTAMP_FORMAT, timestampMD.get("format").toString());
            } else {
                collectionRefParams.customParams.put(CollectionReference.TIMESTAMP_FORMAT, CollectionReference.DEFAULT_TIMESTAMP_FORMAT);
            }
        }
    }
}

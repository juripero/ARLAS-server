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

package io.arlas.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.arlas.server.app.ArlasServerConfiguration;
import io.arlas.server.model.RasterTileURL;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.core.util.IOUtils;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.AdminClient;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.geojson.LngLatAlt;
import org.geojson.Polygon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DataSetTool {

    static Logger LOGGER = LoggerFactory.getLogger(DataSetTool.class);

    public final static String DATASET_INDEX_NAME="dataset";
    public final static String DATASET_TYPE_NAME="mytype";
    public final static String DATASET_ID_PATH="id";
    public final static String DATASET_GEO_PARAMS="geo_params";
    public final static String DATASET_GEOMETRY_PATH="geo_params.geometry";
    public final static String DATASET_CENTROID_PATH="geo_params.centroid";
    public final static String DATASET_TIMESTAMP_PATH="params.startdate";
    public final static String DATASET_EXCLUDE_FIELDS = "params.ci*";
    public final static String DATASET_TAGGABLE_FIELDS="params.tags,params.job";
    public final static String DATASET_EXCLUDE_WFS_FIELDS="params.country";
    public final static String DATASET_TIMESTAMP_FORMAT = "epoch_millis";
    public static final String DATASET_INSPIRE_LINEAGE = "Dataset loaded for testing";
    public static final String DATASET_INSPIRE_TOPIC_CATEGORY = "biota";
    public static final String DATASET_DUBLIN_CORE_TITLE = "geodata";
    public static final String DATASET_DUBLIN_CORE_DESCRIPTION = "geodata set for testing";
    public static final String DATASET_DUBLIN_CORE_LANGUAGE = "eng";
    public final static RasterTileURL DATASET_TILE_URL = new RasterTileURL(Optional.ofNullable(System.getenv("ARLAS_TILE_URL")).orElse("{id}/{z}/{x}/{y}.png"),5,18,true);
    public static final String[] jobs = {"Actor", "Announcers", "Archeologists", "Architect", "Brain Scientist", "Chemist", "Coach", "Coder", "Cost Estimator", "Dancer", "Drafter"};
    public static final String[] cities = {"Paris", "London", "New York", "Tokyo", "Toulouse", "Marseille", "Lyon", "Bordeaux", "Lille", "Albi", "Calais"};
    public static final String[] countries = {"Afghanistan",
            "Albania",
            "Algeria",
            "Andorra",
            "Angola",
            "Antigua",
            "Barbuda",
            "Argentina",
            "Armenia",
            "Aruba",
            "Australia",
            "Austria"
    };

    public static AdminClient adminClient;
    public static Client client;
    public static boolean ALIASED_COLLECTION;

    static {
        try {
            Settings settings = null;
            List<Pair<String,Integer>> nodes = ArlasServerConfiguration.getElasticNodes(Optional.ofNullable(System.getenv("ARLAS_ELASTIC_NODES")).orElse("localhost:9300"));
            if ("localhost".equals(nodes.get(0).getLeft())) {
                settings = Settings.EMPTY;
            } else {
                settings = Settings.builder().put("cluster.name", "docker-cluster").build();
            }
            client = new PreBuiltTransportClient(settings)
                    .addTransportAddress(new TransportAddress(InetAddress.getByName(nodes.get(0).getLeft()), nodes.get(0).getRight()));
            adminClient = client.admin();
            ALIASED_COLLECTION = Optional.ofNullable(System.getenv("ALIASED_COLLECTION")).orElse("false").equals("true");
            LOGGER.info("Load data in " + nodes.get(0).getLeft() + ":" + nodes.get(0).getRight() + " with ALIASED_COLLECTION=" + ALIASED_COLLECTION);
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    public static void main(String[] args) throws IOException {
        DataSetTool.loadDataSet();
    }

    public static void loadDataSet() throws IOException {
        if(!ALIASED_COLLECTION) {
            //Create a single index with all data
            createIndex(DATASET_INDEX_NAME,"dataset.mapping.json");
            fillIndex(DATASET_INDEX_NAME,-170,170,-80,80);
            LOGGER.info("Index created : " + DATASET_INDEX_NAME);
        } else {
            //Create 2 indeces, split data between them and create an alias above these 2 indeces
            createIndex(DATASET_INDEX_NAME+"_original","dataset.mapping.json");
            fillIndex(DATASET_INDEX_NAME+"_original",-170,0,-80,80);
            createIndex(DATASET_INDEX_NAME+"_alt","dataset.alternate.mapping.json");
            fillIndex(DATASET_INDEX_NAME+"_alt",10,170,-80,80);
            adminClient.indices().prepareAliases().addAlias(DATASET_INDEX_NAME+"*",DATASET_INDEX_NAME).get();
            LOGGER.info("Indeces created : " + DATASET_INDEX_NAME + "_original," + DATASET_INDEX_NAME + "_alt");
            LOGGER.info("Alias created : " + DATASET_INDEX_NAME);
        }
    }

    private static void createIndex(String indexName, String mappingFileName) throws IOException {
        String mapping = IOUtils.toString(new InputStreamReader(DataSetTool.class.getClassLoader().getResourceAsStream(mappingFileName)));
        try {
            adminClient.indices().prepareDelete(indexName).get();
        } catch (Exception e) {
        }
        adminClient.indices().prepareCreate(indexName).addMapping(DATASET_TYPE_NAME, mapping, XContentType.JSON).get();
    }

    private static void fillIndex(String indexName, int lonMin, int lonMax, int latMin, int latMax) throws JsonProcessingException {
        Data data;
        ObjectMapper mapper = new ObjectMapper();

        for (int i = lonMin; i <= lonMax; i += 10) {
            for (int j = latMin; j <= latMax; j += 10) {
                data = new Data();
                data.id = String.valueOf("ID_" + i + "_" + j + "DI").replace("-", "_");
                data.fullname = "My name is " + data.id;
                data.params.age = Math.abs(i * j);
                data.params.startdate = 1l * (i + 1000) * (j + 1000);
                if (data.params.startdate >= 1013600) {
                    data.params.weight = (i + 10) * (j + 10);
                }
                data.params.stopdate = 1l * (i + 1000) * (j + 1000) + 100;
                data.geo_params.centroid = j + "," + i;
                data.params.job = jobs[((Math.abs(i) + Math.abs(j)) / 10) % (jobs.length - 1)];
                data.params.country = countries[((Math.abs(i) + Math.abs(j)) / 10) % (countries.length - 1)];
                data.params.city = cities[((Math.abs(i) + Math.abs(j)) / 10) % (cities.length - 1)];
                List<LngLatAlt> coords = new ArrayList<>();
                coords.add(new LngLatAlt(i - 1, j + 1));
                coords.add(new LngLatAlt(i + 1, j + 1));
                coords.add(new LngLatAlt(i + 1, j - 1));
                coords.add(new LngLatAlt(i - 1, j - 1));
                coords.add(new LngLatAlt(i - 1, j + 1));
                data.geo_params.geometry = new Polygon(coords);

                IndexResponse response = client.prepareIndex(indexName, DATASET_TYPE_NAME, "ES_ID_TEST" + data.id)
                        .setSource(mapper.writer().writeValueAsString(data), XContentType.JSON)
                        .get();
            }
        }
    }

    public static void clearDataSet() {
        if(!ALIASED_COLLECTION) {
            adminClient.indices().prepareDelete(DATASET_INDEX_NAME).get();
        } else {
            adminClient.indices().prepareDelete(DATASET_INDEX_NAME+"_original").get();
            adminClient.indices().prepareDelete(DATASET_INDEX_NAME+"_alt").get();
        }
    }

    public static void close() {
        client.close();
    }
}
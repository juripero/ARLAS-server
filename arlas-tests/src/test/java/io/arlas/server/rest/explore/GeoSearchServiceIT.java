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

package io.arlas.server.rest.explore;

import io.arlas.server.model.request.MultiValueFilter;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import org.hamcrest.Matcher;

import java.util.Arrays;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class GeoSearchServiceIT extends AbstractXYZTiledTest {

    @Override
    public String getUrlPath(String collection) {
        return arlasPath + "explore/" + collection + "/_geosearch";
    }

    @Override
    public String getXYZUrlPath(String collection, int z, int x, int y) {
        return getUrlPath(collection) + "/" + z + "/" + x + "/" + y;
    }

    @Override
    protected void handleNotMatchingRequest(ValidatableResponse then) {
        then.statusCode(200)
                .body("type", equalTo("FeatureCollection"))

                .body("$", not(hasKey("features")));
    }


    //----------------------------------------------------------------
    //----------------------- FILTER PART ----------------------------
    //----------------------------------------------------------------
    @Override
    protected RequestSpecification givenFilterableRequestParams() {
        return given();
    }

    @Override
    protected RequestSpecification givenFilterableRequestBody() {
        return given().contentType("application/json");
    }

    @Override
    public void handleComplexFilter(ValidatableResponse then) throws Exception {
        then.statusCode(200)
                .body("features[0].properties.params.job", equalTo("Architect"))
                .body("features[0].properties.params.startdate", equalTo(1009800))
                .body("features[0].properties.geo_params.centroid", equalTo("20,-10"));
    }

    @Override
    protected void handleFieldFilter(ValidatableResponse then, int nbResults, String... values) throws Exception {
        then.statusCode(200)
                .body("features.properties.params.job", everyItem(isOneOf(values)));
    }

    @Override
    protected void handleMatchingQueryFilter(ValidatableResponse then, int nbResults) throws Exception {
        then.statusCode(200)
                .body("features.size()", equalTo(Math.min(nbResults, 10)));//get only default sized result array
    }

    @Override
    protected void handleMatchingTimestampRangeFilter(ValidatableResponse then, int start, int end, int size) throws Exception {
        then.statusCode(200)
                .body("features.size()", equalTo(size))
                .body("features.properties.params.startdate", everyItem(greaterThan(start)))
                .body("features.properties.params.startdate", everyItem(lessThan(end)));
    }

    @Override
    protected void handleMatchingStringRangeFilter(ValidatableResponse then, String start, String end, int size) throws Exception {
        then.statusCode(200)
                .body("features.properties.params.job", everyItem(greaterThan(start)))
                .body("features.properties.params.job", everyItem(lessThan(end)));
    }

    @Override
    protected void handleMatchingGeometryFilter(ValidatableResponse then, int nbResults, Matcher<?> centroidMatcher) throws Exception {
        then.statusCode(200)
                .body("features.size()", equalTo(Math.min(nbResults, 10)))
                .body("features.properties.geo_params.centroid", centroidMatcher);
    }

    //----------------------------------------------------------------
    //----------------------- SIZE PART ------------------------------
    //----------------------------------------------------------------
    @Override
    protected RequestSpecification givenBigSizedRequestParams() {
        return given().param("q", "My name is");
    }

    @Override
    protected RequestSpecification givenBigSizedRequestParamsPost() {
        search.filter.q = Arrays.asList(new MultiValueFilter<>("My name is"));
        return given().contentType("application/json");
    }

    @Override
    protected int getBigSizedResponseSize() {
        return 595;
    }

    @Override
    protected void handleSizeParameter(ValidatableResponse then, int size) {
        if (size > 0) {
            then.statusCode(200)
                    .body("features.size()", equalTo(size));
        } else {
            then.statusCode(200)
                    .body("$", not(hasKey("features")));
        }
    }

    //----------------------------------------------------------------
    //----------------------- PROJECTION PART ------------------------------
    //----------------------------------------------------------------

    @Override
    protected void handleHiddenParameter(ValidatableResponse then, List<String> hidden) throws Exception {
        then.statusCode(200);
        for (String key : hidden) {
            String path = "features.properties";
            String lastKey = key;
            if (key.contains(".")) {
                path += ("." + key.substring(0, key.lastIndexOf(".")));
                lastKey = key.substring(key.lastIndexOf(".") + 1, key.length());
            }
            then.body(path, everyItem(not(hasKey(lastKey))));
        }
    }

    @Override
    protected void handleDisplayedParameter(ValidatableResponse then, List<String> displayed) throws Exception {
        then.statusCode(200);
        for (String key : displayed) {
            String path = "features.properties";
            String lastKey = key;
            if (key.contains(".")) {
                path += ("." + key.substring(0, key.lastIndexOf(".")));
                lastKey = key.substring(key.lastIndexOf(".") + 1, key.length());
            }
            then.body(path, everyItem(hasKey(lastKey)));
        }
    }

    //----------------------------------------------------------------
    //----------------------- SORT PART ------------------------------
    //----------------------------------------------------------------

    @Override
    protected void handleSortParameter(ValidatableResponse then, String firstElement) throws Exception {
        then.statusCode(200)
                .body("features[0].properties.params.job", equalTo(firstElement));
    }

    @Override
    protected void handleGeoSortParameter(ValidatableResponse then, String firstElement) throws Exception {
        then.statusCode(200)
                .body("features[0].properties.geo_params.centroid", equalTo(firstElement));
    }

    @Override
    protected void handleInvalidGeoSortParameter(ValidatableResponse then) {
        then.statusCode(400);
    }

    //----------------------- XYZ TILES PART -------------------------
    //----------------------------------------------------------------

    @Override
    protected void handleXYZ(ValidatableResponse then, String bottomLeft, String topRight) throws Exception {
        then.statusCode(200)
                .body("features.properties.geo_params.centroid", everyItem(greaterThanOrEqualTo(bottomLeft)))
                .body("features.properties.geo_params.centroid", everyItem(lessThanOrEqualTo(topRight)));
    }

    @Override
    protected void handleXYZDisjointFromPwithin(ValidatableResponse then) throws Exception {
        then.statusCode(200)
                .body("features", equalTo(null));
    }

    @Override
    protected void handleInvalidXYZ(ValidatableResponse then) {
        then.statusCode(400);
    }
}
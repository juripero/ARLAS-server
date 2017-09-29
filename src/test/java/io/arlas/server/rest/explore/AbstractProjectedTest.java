package io.arlas.server.rest.explore;

import io.arlas.server.model.request.*;
import io.restassured.response.ValidatableResponse;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public abstract class AbstractProjectedTest extends AbstractSizedTest {

    @Before
    public void setUpSearch() {
        search.size = new Size();
        search.filter = new Filter();
        search.projection = new Projection();
    }

    //----------------------------------------------------------------
    //----------------------- SUCCESS TESTS --------------------------
    //----------------------------------------------------------------
    @Test
    public void testIncludeExcludeFilter() throws Exception {
        search.projection.includes = "id,params,geo_params";
        handleHiddenParameter(post(search), Arrays.asList("fullname"));
        handleHiddenParameter(get("include", search.projection.includes), Arrays.asList("fullname"));

        search.projection.includes = "id,geo_params";
        handleDisplayedParameter(post(search), Arrays.asList("params.startdate"));
        handleDisplayedParameter(get("include", search.projection.includes), Arrays.asList("params.startdate"));

        search.projection.includes = null;

        search.projection.excludes = "fullname";
        handleHiddenParameter(post(search), Arrays.asList("fullname"));
        handleHiddenParameter(get("exclude", search.projection.excludes), Arrays.asList("fullname"));

        search.projection.excludes = null;

        search.projection.excludes = "params.job,fullname";
        search.projection.includes = "geo_params.geometry";
        handleDisplayedParameter(post(search), Arrays.asList("id","params.startdate", "geo_params.geometry", "geo_params.centroid"));
        handleDisplayedParameter(givenFilterableRequestParams().param("include", search.projection.includes)
                .param("exclude", search.projection.excludes)
                .when().get(getUrlPath("geodata"))
                .then(), Arrays.asList("id","params.startdate", "geo_params.geometry", "geo_params.centroid"));

        search.projection.includes = null;
        search.projection.excludes = null;
    }

    protected abstract void handleHiddenParameter(ValidatableResponse then, List<String> hidden) throws Exception;
    protected abstract void handleDisplayedParameter(ValidatableResponse then, List<String> displayed) throws Exception;
}
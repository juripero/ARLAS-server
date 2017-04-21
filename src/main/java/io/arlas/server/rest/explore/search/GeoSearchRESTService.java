package io.arlas.server.rest.explore.search;

import com.codahale.metrics.annotation.Timed;
import io.arlas.server.exceptions.ArlasException;
import io.arlas.server.core.FluidSearch;
import io.arlas.server.model.CollectionReference;
import io.arlas.server.rest.explore.ExploreRESTServices;
import io.arlas.server.rest.explore.ExploreServices;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.geojson.Feature;
import org.geojson.FeatureCollection;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class GeoSearchRESTService extends ExploreRESTServices {

    public GeoSearchRESTService(ExploreServices exploreServices) {
        super(exploreServices);
    }

    @Timed
    @Path("{collections}/_geosearch")
    @GET
    @Produces(UTF8JSON)
    @Consumes(UTF8JSON)
    @ApiOperation(
            value="Geoearch",
            produces=UTF8JSON,
            notes = "Search and return the elements found in the collection(s) as features, given the filters",
            consumes=UTF8JSON,
            response = FeatureCollection.class
    )
    @ApiResponses(value = { @ApiResponse(code = 200, message = "Successful operation")})
    public Response geosearch(
            // --------------------------------------------------------
            // -----------------------  PATH    -----------------------
            // --------------------------------------------------------
            @ApiParam(
                    name = "collections",
                    value="collections, comma separated",
                    allowMultiple = false,
                    required=true)
            @PathParam(value = "collections") String collections,
            // --------------------------------------------------------
            // -----------------------  FILTER  -----------------------
            // --------------------------------------------------------
            @ApiParam(name ="f",
                    value="- A triplet for filtering the result. Multiple filter can be provided. " +
                            "The order does not matter. " +
                            "\n \n" +
                            "- A triplet is composed of a field name, a comparison operator and a value. " +
                            "\n \n" +
                            "  The possible values of the comparison operator are : " +
                            "\n \n" +
                            "       Operator   |                   Description                      | value type" +
                            "\n \n" +
                            "       :          |  {fieldName} equals {value}                        | numeric or strings " +
                            "\n \n" +
                            "       :gte:      |  {fieldName} is greater than or equal to  {value}  | numeric " +
                            "\n \n" +
                            "       :gt:       |  {fieldName} is greater than {value}               | numeric " +
                            "\n \n" +
                            "       :lte:      |  {fieldName} is less than or equal to {value}      | numeric " +
                            "\n \n" +
                            "       :lt:       |  {fieldName}  is less than {value}                 | numeric " +
                            "\n \n" +
                            "\n \n" +
                            "- The AND operator is applied between filters having different fieldNames. " +
                            "\n \n" +
                            "- The OR operator is applied on filters having the same fieldName. " +
                            "\n \n" +
                            "- If the fieldName starts with - then a must not filter is used" +
                            "\n \n" +
                            "- If the fieldName starts with - then a must not filter is used" +
                            "\n \n" +
                            "For more details, check https://gitlab.com/GISAIA.ARLAS/ARLAS-server/blob/master/doc/api/API-definition.md "
                    ,

                    allowMultiple = true,
                    required=false)
            @QueryParam(value="f") List<String> f,

            @ApiParam(name ="q", value="A full text search",
                    allowMultiple = false,
                    required=false)
            @QueryParam(value="q") String q,

            @ApiParam(name ="before", value="Any element having its point in time reference before the given timestamp",
                    allowMultiple = false,
                    required=false)
            @QueryParam(value="before") Long before,

            @ApiParam(name ="after", value="Any element having its point in time reference after the given timestamp",
                    allowMultiple = false,
                    required=false)
            @QueryParam(value="after") Long after,

            @ApiParam(name ="pwithin", value="Any element having its centroid contained within the given geometry (WKT)",
                    allowMultiple = true,
                    required=false)
            @QueryParam(value="pwithin") String pwithin,

            @ApiParam(name ="gwithin", value="Any element having its geometry contained within the given geometry (WKT)",
                    allowMultiple = true,
                    required=false)
            @QueryParam(value="gwithin") String gwithin,

            @ApiParam(name ="gintersect", value="Any element having its geometry intersecting the given geometry (WKT)",
                    allowMultiple = true,
                    required=false)
            @QueryParam(value="gintersect") String gintersect,

            @ApiParam(name ="notpwithin", value="Any element having its centroid outside the given geometry (WKT)",
                    allowMultiple = true,
                    required=false)
            @QueryParam(value="notpwithin") String notpwithin,

            @ApiParam(name ="notgwithin", value="Any element having its geometry outside the given geometry (WKT)",
                    allowMultiple = true,
                    required=false)
            @QueryParam(value="notgwithin") String notgwithin,

            @ApiParam(name ="notgintersect", value="Any element having its geometry not intersecting the given geometry (WKT)",
                    allowMultiple = true,
                    required=false)
            @QueryParam(value="notgintersect") String notgintersect,

            // --------------------------------------------------------
            // -----------------------  FORM    -----------------------
            // --------------------------------------------------------
            @ApiParam(name ="pretty", value="Pretty print",
                    allowMultiple = false,
                    defaultValue = "false",
                    required=false)
            @QueryParam(value="pretty") Boolean pretty,

            @ApiParam(name ="human", value="Human readable print",
                    allowMultiple = false,
                    defaultValue = "false",
                    required=false)
            @QueryParam(value="human") Boolean human,

            // --------------------------------------------------------
            // -----------------------  PROJECTION   -----------------------
            // --------------------------------------------------------

            @ApiParam(name ="include", value="List the name patterns of the field to be included in the result. Seperate patterns with a comma.",
                    allowMultiple = true,
                    defaultValue = "*",
                    example = "*",
                    required=false)
            @QueryParam(value="include") String include,

            @ApiParam(name ="exclude", value="List the name patterns of the field to be excluded in the result. Seperate patterns with a comma.",
                    allowMultiple = true,
                    defaultValue = "*",
                    example = "city,state",
                    required=false)
            @QueryParam(value="exclude") String exclude,

            // --------------------------------------------------------
            // -----------------------  SIZE   -----------------------
            // --------------------------------------------------------

            @ApiParam(name ="size", value="The maximum number of entries or sub-entries to be returned. The default value is 10",
                    defaultValue = "10",
                    allowableValues = "range[1, infinity]",
                    required=false)
            @DefaultValue("10")
            @QueryParam(value="size") Integer size,

            @ApiParam(name ="from", value="From index to start the search from. Defaults to 0.",
                    defaultValue = "0",
                    allowableValues = "range[0, infinity]",
                    required=false)
            @DefaultValue("0")
            @QueryParam(value="from") Integer from,

            // --------------------------------------------------------
            // -----------------------  SORT   -----------------------
            // --------------------------------------------------------

            @ApiParam(name ="sort",
                    value="- Sort the result on the given fields ascending or descending. " +
                            "\n \n"+
                            "- Fields can be provided several times by separating them with a comma. The order matters. " +
                            "\n \n"+
                            "- For a descending sort, precede the field with '-'. The sort will be ascending otherwise."+
                            "\n \n"+
                            "- For aggregation, provide the `agg` keyword as the `{field}`.",
                    allowMultiple = true,
                    example = "-country,city",
                    required=false)
            @QueryParam(value="sort") String sort,

            // --------------------------------------------------------
            // -----------------------  EXTRA   -----------------------
            // --------------------------------------------------------
            @ApiParam(value="max-age-cache", required=false)
            @QueryParam(value="max-age-cache") Integer maxagecache
    ) throws InterruptedException, ExecutionException, IOException, NotFoundException, ArlasException {
        String[] collectionsList = collections.split(",");
        FluidSearch fluidSearch = new FluidSearch(exploreServices.getClient());
        for(int i=0; i<collectionsList.length; i++){
            CollectionReference collectionReference = exploreServices.getDaoCollectionReference().getCollectionReference(collectionsList[i]);
            fluidSearch.setCollectionReference(collectionReference);

            if (f != null && !f.isEmpty()){
                fluidSearch = fluidSearch.filter(f);
            }
            if (q != null){
                fluidSearch = fluidSearch.filterQ(q);
            }
            if (after != null){
                fluidSearch = fluidSearch.filterAfter(after);
            }
            if (before != null){
                fluidSearch = fluidSearch.filterBefore(before);
            }
            if (pwithin != null && !pwithin.isEmpty()){
                fluidSearch = fluidSearch.filterPWithin(pwithin);
            }
            if (gwithin != null && !gwithin.isEmpty()){
                fluidSearch = fluidSearch.filterGWithin(gwithin);
            }
            if (gintersect != null && !gintersect.isEmpty()){
                fluidSearch = fluidSearch.filterGIntersect(gintersect);
            }
            if (notpwithin != null && !notpwithin.isEmpty()){
                fluidSearch = fluidSearch.filterNotPWithin(notpwithin);
            }
            if (notgwithin != null && !notgwithin.isEmpty()){
                fluidSearch = fluidSearch.filterNotGWithin(notgwithin);
            }
            if (notgintersect != null && !notgintersect.isEmpty()){
                fluidSearch = fluidSearch.filterNotGIntersect(notgintersect);
            }
            if (include != null){
                fluidSearch = fluidSearch.include(include);
            }
            if (exclude != null){
                fluidSearch = fluidSearch.exclude(exclude);
            }
            if (size != null){
                if (from != null){
                    fluidSearch = fluidSearch.filterSize(size,from);
                }
                else fluidSearch = fluidSearch.filterSize(size,0);
            }
            if (sort != null){
                fluidSearch = fluidSearch.sort(sort);
            }
        }
        FeatureCollection fc = new FeatureCollection();
        SearchHits searchHits = fluidSearch.exec().getHits();
        Long sizeHits = searchHits.totalHits();
        SearchHit[] results = searchHits.getHits();
        String json = "";
        //TODO: feature.setGeometry
        for(SearchHit hit : results){
            Feature feature = new Feature();
            //feature.setGeometry(actualObj);
            feature.setProperties(hit.getSource());
            fc.add(feature);

            json += hit.getSourceAsString()+ "\n ---------------- \n";
        }
        Response resp = null;
        if(results!=null) {
            resp = Response.ok(fc).build();
        } else {
            resp = Response.status(Response.Status.NOT_FOUND).entity("NO RESULTS").type(MediaType.TEXT_PLAIN).build();
        }
        return resp;
        //return Response.ok("search").build();
        //return Response.ok("search").build();
    }
}

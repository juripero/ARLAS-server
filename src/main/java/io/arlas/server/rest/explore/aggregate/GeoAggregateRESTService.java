package io.arlas.server.rest.explore.aggregate;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.arlas.server.core.FluidSearch;
import io.arlas.server.exceptions.ArlasException;
import io.arlas.server.exceptions.InvalidParameterException;
import io.arlas.server.model.ArlasAggregation;
import io.arlas.server.model.ArlasError;
import io.arlas.server.model.CollectionReference;
import io.arlas.server.model.TimedFeatureCollection;
import io.arlas.server.rest.explore.ExploreRESTServices;
import io.arlas.server.rest.explore.ExploreServices;
import io.arlas.server.utils.CheckParams;
import io.dropwizard.jersey.params.IntParam;
import io.dropwizard.jersey.params.LongParam;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.elasticsearch.common.geo.GeoPoint;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.geojson.*;
import org.geojson.jackson.CrsType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class GeoAggregateRESTService extends ExploreRESTServices {

    static Logger LOGGER = LoggerFactory.getLogger(GeoAggregateRESTService.class);

    public GeoAggregateRESTService(ExploreServices exploreServices) {
        super(exploreServices);
    }

    @Timed
    @Path("{collection}/_geoaggregate")
    @GET
    @Produces(UTF8JSON)
    @Consumes(UTF8JSON)
    @ApiOperation(value = "Aggregate", produces = UTF8JSON, notes = "Aggregate the elements in the collection(s), given the filters and the aggregation parameters", consumes = UTF8JSON, response = FeatureCollection.class

    )
    @ApiResponses(value = { @ApiResponse(code = 200, message = "Successful operation", response = FeatureCollection.class, responseContainer = "FeatureCollection" ),
            @ApiResponse(code = 500, message = "Arlas Server Error.", response = ArlasError.class), @ApiResponse(code = 400, message = "Bad request.", response = ArlasError.class) })
    public Response geoaggregate(
            // --------------------------------------------------------
            // ----------------------- PATH -----------------------
            // --------------------------------------------------------
            @ApiParam(
                    name = "collection",
                    value="collection",
                    allowMultiple = false,
                    required=true)
            @PathParam(value = "collection") String collection,

            // --------------------------------------------------------
            // ----------------------- AGGREGATION -----------------------
            // --------------------------------------------------------
            @ApiParam(name ="agg",
                    value="- The agg parameter should be given in the following formats:  " +
                            "\n \n" +
                            "       {type}:{field}:interval-{interval}:collect_field-{collect_field}:collect_fct-{function}:order-{order}:on-{on} " +
                            "\n \n" +
                            "Where :" +
                            "\n \n" +
                            "   - **{type}:{field}:interval-{interval}** part is mandatory. " +
                            "\n \n" +
                            "   - (**collect_field**,**collect_fct**) is optional for all aggregation types." +
                            "\n \n" +
                            "   - (**order**,**on**) is optional for all aggregation types." +
                            "\n \n" +
                            "- {type} possible value is **geohash**. " +
                            "\n \n" +
                            "- {interval} is the geohash length. It's an integer between 1 and 12. Lower the length, greater is the surface of aggregation." +
                            "\n \n" +
                            "- {collect_fct} is the aggregation function to apply to collections on the specified {collect_field}. " +
                            "\n \n" +
                            "  {collect_fct} possible values are : "+
                            "\n \n" +
                            "       avg,cardinality,max,min,sum" +
                            "\n \n" +
                            "- {order} is set to sort the aggregation result on the field name or on the result itself. " +
                            "It's values are 'asc' or 'desc'. " +
                            "\n \n" +
                            "- {on} is set to specify whether the {order} is on the field name or the result. It's values are 'field' or 'result'. " +
                            "\n \n" +
                            "agg parameter is not multiple. Subaggregations are not allowed. "+
                            "\n \n" +
                            "For more details, check https://gitlab.com/GISAIA.ARLAS/ARLAS-server/blob/master/doc/api/API-definition.md "
                    ,
                    allowMultiple = false,
                    required=true)
            @QueryParam(value="agg") List<String> agg,

            // --------------------------------------------------------
            // ----------------------- FILTER -----------------------
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
            @QueryParam(value="before") LongParam before,

            @ApiParam(name ="after", value="Any element having its point in time reference after the given timestamp",
                    allowMultiple = false,
                    required=false)
            @QueryParam(value="after") LongParam after,

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
            // ----------------------- FORM -----------------------
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
            // ----------------------- EXTRA -----------------------
            // --------------------------------------------------------
            @ApiParam(value = "max-age-cache", required = false)
            @QueryParam(value = "max-age-cache") Integer maxagecache
    ) throws InterruptedException, ExecutionException, IOException, NotFoundException, ArlasException, JsonProcessingException {
        Long startArlasTime = System.nanoTime();
        FluidSearch fluidSearch = new FluidSearch(exploreServices.getClient());
        CollectionReference collectionReference = exploreServices.getDaoCollectionReference()
                .getCollectionReference(collection);
        if (collectionReference == null) {
            throw new NotFoundException(collection);
        }
        fluidSearch.setCollectionReference(collectionReference);

        if (f != null && !f.isEmpty()) {
            fluidSearch = fluidSearch.filter(f);
        }
        if (q != null) {
            fluidSearch = fluidSearch.filterQ(q);
        }
        if(before != null || after != null) {
            if((before!=null && before.get()<0) || (after != null && after.get()<0)
                    || (before != null && after != null && before.get() < after.get()))
                throw new InvalidParameterException(FluidSearch.INVALID_BEFORE_AFTER);
        }
        if (after != null) {
            fluidSearch = fluidSearch.filterAfter(after.get());
        }
        if (before != null) {
            fluidSearch = fluidSearch.filterBefore(before.get());
        }
        if (pwithin != null && !pwithin.isEmpty()) {
            double[] tlbr = CheckParams.toDoubles(pwithin);
            if (tlbr.length == 4 && tlbr[0]>tlbr[2] && tlbr[2]<tlbr[3]) {
                fluidSearch = fluidSearch.filterPWithin(tlbr[0], tlbr[1], tlbr[2], tlbr[3]);
            } else {
                throw new InvalidParameterException(FluidSearch.INVALID_BBOX);
            }
        }
        if (gwithin != null && !gwithin.isEmpty()) {
            fluidSearch = fluidSearch.filterGWithin(gwithin);
        }
        if (gintersect != null && !gintersect.isEmpty()) {
            fluidSearch = fluidSearch.filterGIntersect(gintersect);
        }
        if (notpwithin != null && !notpwithin.isEmpty()) {
            double[] tlbr = CheckParams.toDoubles(notpwithin);
            if (tlbr.length == 4 && tlbr[0]>tlbr[2] && tlbr[2]<tlbr[3]) {
                fluidSearch = fluidSearch.filterNotPWithin(tlbr[0], tlbr[1], tlbr[2], tlbr[3]);
            } else {
                throw new InvalidParameterException(FluidSearch.INVALID_BBOX);
            }
        }
        if (notgwithin != null && !notgwithin.isEmpty()) {
            fluidSearch = fluidSearch.filterNotGWithin(notgwithin);
        }
        if (notgintersect != null && !notgintersect.isEmpty()) {
            fluidSearch = fluidSearch.filterNotGIntersect(notgintersect);
        }

        FeatureCollection fc;
        MultiBucketsAggregation aggregation = null;
        ArlasAggregation arlasAggregation = new ArlasAggregation();
        if (agg != null && agg.size()>0){
            Long startQuery = System.nanoTime();
            fluidSearch.aggregate(agg, true);
            aggregation = (MultiBucketsAggregation)fluidSearch.exec().getAggregations().asList().get(0);
            arlasAggregation.queryTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startQuery);

        }
        arlasAggregation = fluidSearch.formatAggregationResult(aggregation,arlasAggregation);
        fc = toGeoJson(arlasAggregation);
        //fc.queryTime = arlasAggregation.queryTime;
        //fc.arlasTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startArlasTime);
        return Response.ok(fc).build();
    }

    private FeatureCollection toGeoJson(ArlasAggregation arlasAggregation) throws IOException{
        FeatureCollection fc = new FeatureCollection();
        List<ArlasAggregation> elements = arlasAggregation.elements;
        if (elements != null && elements.size()>0){
            for (ArlasAggregation element : elements){
                Feature feature = new Feature();
                Map<String,Object> properties = new HashMap<>();
                GeoPoint geoPoint = (GeoPoint)element.key;
                properties.put("count", element.count);
                properties.put("geohash", geoPoint.getGeohash());
                properties.put("elements", element.elements);
                feature.setProperties(properties);
                GeoJsonObject g = new Point(geoPoint.getLon(),geoPoint.getLat());
                feature.setGeometry(g);
                fc.add(feature);
            }
        }
        return fc;
    }
}

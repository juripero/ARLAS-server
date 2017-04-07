package io.arlas.server.rest.admin;

import com.codahale.metrics.annotation.Timed;
import io.arlas.server.model.CollectionReference;
import io.swagger.annotations.*;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class CollectionService extends AdminServices {

    @Timed
    @Path("{collection}")
    @GET
    @Produces(UTF8JSON)
    @Consumes(UTF8JSON)
    @ApiOperation(
            value="Get a collection reference",
            produces=UTF8JSON,
            notes = "Get a collection reference in ARLAS",
            consumes=UTF8JSON,
            response = CollectionReference.class

    )
    @ApiResponses(value = { @ApiResponse(code = 200, message = "Successful operation")})

    public Response get(
            @ApiParam(
                    name = "collection",
                    value = "collection",
                    allowMultiple = false,
                    required=true)
            @PathParam(value = "collection") String collection
    ) throws InterruptedException, ExecutionException, IOException {
        return Response.ok("count").build();// TODO : right reponse
    }

    @Timed
    @Path("{collection}") //
    @PUT
    @Produces(UTF8JSON)
    @Consumes(UTF8JSON)
    @ApiOperation(
            value="Add a collection reference",
            produces=UTF8JSON,
            notes = "Add a collection reference in ARLAS",
            consumes=UTF8JSON,
            response = CollectionReference.class
    )
    @ApiResponses(value = { @ApiResponse(code = 200, message = "Successful operation")})
    public Response put(
            @ApiParam(
                    name = "collection",
                    value = "collection",
                    allowMultiple = false,
                    required=true)
            @PathParam(value = "collection") String collection,

            // --------------------------------------------------------
            // -----------------------  COLLECTION REFERENCE    -----------------------
            // --------------------------------------------------------
            @ApiParam(name = "collectionReference",
                        value="collectionReference",
                        required=true)
            CollectionReference collectionReference

    ) throws InterruptedException, ExecutionException, IOException {
        return Response.ok("count").build();// TODO : right response
    }

    @Timed
    @Path("{collection}")
    @DELETE
    @Produces(UTF8JSON)
    @Consumes(UTF8JSON)
    @ApiOperation(
            value="Delete a collection reference",
            produces=UTF8JSON,
            notes = "Delete a collection reference in ARLAS",
            consumes=UTF8JSON
    )
    @ApiResponses(value = { @ApiResponse(code = 200, message = "Successful operation")})

    public Response delete(
            @ApiParam(
                    name = "collection",
                    value = "collection",
                    allowMultiple = false,
                    required=true)
            @PathParam(value = "collection") String collection
    ) throws InterruptedException, ExecutionException, IOException {
        return Response.ok("count").build();//TODO : right response
    }
}
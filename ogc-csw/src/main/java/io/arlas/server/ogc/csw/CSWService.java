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

package io.arlas.server.ogc.csw;

import com.a9.opensearch.OpenSearchDescription;
import com.codahale.metrics.annotation.Timed;
import io.arlas.server.app.CSWConfiguration;
import io.arlas.server.app.OGCConfiguration;
import io.arlas.server.core.FluidSearch;
import io.arlas.server.dao.CollectionReferenceDao;
import io.arlas.server.exceptions.ArlasException;
import io.arlas.server.exceptions.OGCException;
import io.arlas.server.exceptions.OGCExceptionCode;
import io.arlas.server.model.CollectionReference;
import io.arlas.server.model.response.Error;
import io.arlas.server.ns.ATOM;
import io.arlas.server.ogc.common.model.Service;
import io.arlas.server.ogc.common.utils.GeoFormat;
import io.arlas.server.ogc.common.utils.RequestUtils;
import io.arlas.server.ogc.csw.operation.getcapabilities.GetCapabilitiesHandler;
import io.arlas.server.ogc.csw.operation.getrecordbyid.GetRecordsByIdHandler;
import io.arlas.server.ogc.csw.operation.getrecords.GetRecordsHandler;
import io.arlas.server.ogc.csw.operation.opensearch.OpenSearchHandler;
import io.arlas.server.ogc.csw.utils.CSWCheckParam;
import io.arlas.server.ogc.csw.utils.CSWConstant;
import io.arlas.server.ogc.csw.utils.CSWRequestType;
import io.arlas.server.ogc.csw.utils.ElementSetName;
import io.arlas.server.app.Documentation;
import io.arlas.server.utils.BoundingBox;
import io.swagger.annotations.*;
import net.opengis.cat.csw._3.AbstractRecordType;
import net.opengis.cat.csw._3.CapabilitiesType;
import net.opengis.cat.csw._3.GetRecordsResponseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.*;
import javax.xml.bind.JAXBElement;
import javax.xml.datatype.DatatypeConfigurationException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static io.arlas.server.utils.CheckParams.isBboxLatLonInCorrectRanges;

@Path("/ogc")
@Api(value = "/ogc")
public class CSWService {

    protected CollectionReferenceDao dao = null;
    public static final String MIME_TYPE__OPENSEARCH_XML = "application/opensearchdescription+xml";


    public CSWHandler cswHandler;
    private OGCConfiguration ogcConfiguration;
    private CSWConfiguration cswConfiguration;

    private String serverUrl;

    public CSWService(CSWHandler cswHandler) {
        this.cswHandler = cswHandler;
        this.ogcConfiguration = cswHandler.ogcConfiguration;
        this.cswConfiguration = cswHandler.cswConfiguration;
        this.serverUrl = ogcConfiguration.serverUri;
    }

    @Context
    UriInfo uri;

    @Timed
    @Path("/csw")
    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.TEXT_XML, ATOM.APPLICATION_ATOM_XML, MIME_TYPE__OPENSEARCH_XML})
    @ApiOperation(
            value = "CSW",

            produces = MediaType.APPLICATION_XML + "," + MediaType.TEXT_XML + "," + ATOM.APPLICATION_ATOM_XML + "," + MIME_TYPE__OPENSEARCH_XML,
            notes = "CSW"
    )
    @ApiResponses(value = {@ApiResponse(code = 200, message = "Successful operation"),
            @ApiResponse(code = 500, message = "Arlas Server Error.", response = Error.class)})
    public Response doKVP(
            @ApiParam(
                    name = "version",
                    value = "version",
                    allowMultiple = false,
                    required = true)
            @QueryParam(value = "version") String version,
            @ApiParam(
                    name = "acceptversions",
                    value = "acceptversions",
                    allowMultiple = false,
                    required = true)
            @QueryParam(value = "acceptversions") String acceptVersions,
            @ApiParam(
                    name = "service",
                    value = "service",
                    allowMultiple = false,
                    required = true)
            @QueryParam(value = "service") String service,
            @ApiParam(
                    name = "request",
                    value = "request",
                    allowMultiple = false,
                    required = true)
            @QueryParam(value = "request") String request,
            @ApiParam(
                    name = "elementname",
                    value = "elementname",
                    allowMultiple = false,
                    required = true)
            @QueryParam(value = "elementname") String elementName,
            @ApiParam(
                    name = "elementsetname",
                    value = "elementsetname",
                    allowMultiple = false,
                    required = true)
            @QueryParam(value = "elementsetname") String elementSetName,
            @ApiParam(
                    name = "filter",
                    value = "filter",
                    allowMultiple = false,
                    required = true)
            @QueryParam(value = "filter") String filter,
            @ApiParam(
                    name = "startposition",
                    value = "startposition",
                    allowMultiple = false,
                    required = false)
            @QueryParam(value = "startposition") Integer startPosition,
            @ApiParam(
                    name = "maxrecords",
                    value = "maxrecords",
                    allowMultiple = false,
                    required = false)
            @QueryParam(value = "maxrecords") Integer maxRecords,
            @ApiParam(
                    name = "sections",
                    value = "sections",
                    allowMultiple = false,
                    required = false)
            @QueryParam(value = "sections") String sections,
            @ApiParam(
                    name = "acceptformats",
                    value = "acceptformats",
                    allowMultiple = false,
                    required = false)
            @QueryParam(value = "acceptformats") String acceptFormats,
            @ApiParam(
                    name = "q",
                    value = "q",
                    allowMultiple = false,
                    required = false)
            @QueryParam(value = "q") String query,
            @ApiParam(
                    name = "bbox",
                    value = "bbox",
                    allowMultiple = false,
                    required = false)
            @QueryParam(value = "bbox") String bbox,
            @ApiParam(
                    name = "outputformat",
                    value = "outputformat",
                    allowMultiple = false,
                    required = false)
            @QueryParam(value = "outputformat") String outputFormat,
            @ApiParam(
                    name = "outputschema",
                    value = "outputschema",
                    allowMultiple = false,
                    required = false)
            @QueryParam(value = "outputschema") String outputSchema,
            @ApiParam(
                    name = "typenames",
                    value = "typenames",
                    allowMultiple = false,
                    required = false)
            @QueryParam(value = "typenames") String typeNames,
            @ApiParam(
                    name = "recordids",
                    value = "recordids",
                    allowMultiple = false,
                    required = false)
            @QueryParam(value = "recordids") String recordIds,
            @ApiParam(
                    name = "id",
                    value = "id",
                    allowMultiple = false,
                    required = false)
            @QueryParam(value = "id") String id,

            // --------------------------------------------------------
            // ----------------------- FORM -----------------------
            // --------------------------------------------------------
            @ApiParam(name = "pretty", value = Documentation.FORM_PRETTY,
                    allowMultiple = false,
                    defaultValue = "false",
                    required = false)
            @QueryParam(value = "pretty") Boolean pretty,
            @Context HttpHeaders headers
    ) throws ArlasException, DatatypeConfigurationException, IOException {
        String acceptFormatMediaType = MediaType.APPLICATION_XML;
        String outputFormatMediaType = MediaType.APPLICATION_XML;
        for (MediaType mediaType : headers.getAcceptableMediaTypes()) {
            if (mediaType.getSubtype().contains("opensearchdescription")) {
                OpenSearchHandler openSearchHandler = cswHandler.openSearchHandler;
                OpenSearchDescription description = openSearchHandler.getOpenSearchDescription(serverUrl);
                return Response.ok(description).build();
            } else if (mediaType.getSubtype().contains("atom")) {
                outputFormatMediaType = MediaType.APPLICATION_ATOM_XML;
            }
        }
        if (request == null & version == null & service == null) {
            request = "GetCapabilities";
            version = CSWConstant.SUPPORTED_CSW_VERSION;
            service = CSWConstant.CSW;
        }
        String[] sectionList;
        if (sections == null) {
            sectionList = new String[]{"All"};
        } else {
            sectionList = sections.split(",");
            for (String section : Arrays.asList(sectionList)) {
                if (!Arrays.asList(CSWConstant.SECTION_NAMES).contains(section)) {
                    throw new OGCException(OGCExceptionCode.INVALID_PARAMETER_VALUE, "Invalid sections", "sections", Service.CSW);
                }
            }
        }
        if (acceptFormats != null) {
            if (acceptFormats.equals("text/xml")) {
                acceptFormatMediaType = MediaType.TEXT_XML;
            } else if (acceptFormats.equals("application/xml")) {
                acceptFormatMediaType = MediaType.APPLICATION_XML;
            } else {
                throw new OGCException(OGCExceptionCode.INVALID_PARAMETER_VALUE, "Invalid acceptFormats", "acceptFormats", Service.CSW);
            }
        }

        if (outputFormat != null) {
            if (outputFormat.equals("application/xml")) {
                outputFormatMediaType = MediaType.APPLICATION_XML;
            } else if (outputFormat.equals("application/atom+xml")) {
                outputFormatMediaType = MediaType.APPLICATION_ATOM_XML;
            } else {
                throw new OGCException(OGCExceptionCode.INVALID_PARAMETER_VALUE, "Invalid outputFormat", "outputFormat", Service.CSW);
            }
        }

        RequestUtils.checkRequestTypeByName(request, CSWConstant.SUPPORTED_CSW_REQUESTYPE, Service.CSW);
        CSWRequestType requestType = CSWRequestType.valueOf(request);
        CSWCheckParam.checkQuerySyntax(requestType, elementName, elementSetName, acceptVersions, version, service, outputSchema, typeNames, bbox, recordIds, query, id);

        String[] ids = null;
        if (recordIds != null && recordIds.length() > 0) {
            ids = recordIds.split(",");
        } else if (id != null) {
            ids = new String[]{id};
        }
        BoundingBox boundingBox = null;
        if (bbox != null && bbox.length() > 0) {
            // west, south, east, north CSW spec
            double[] bboxList = GeoFormat.toDoubles(bbox, Service.CSW);
            if (!(isBboxLatLonInCorrectRanges(bboxList) && bboxList[3] > bboxList[1]) && bboxList[0] != bboxList[2]) {
                throw new OGCException(OGCExceptionCode.INVALID_PARAMETER_VALUE, FluidSearch.INVALID_BBOX, "bbox", Service.CSW);
            }
            boundingBox = new BoundingBox(bboxList[3], bboxList[1], bboxList[0], bboxList[2]);
        }
        startPosition = Optional.ofNullable(startPosition).orElse(1);
        maxRecords = Optional.ofNullable(maxRecords).orElse(ogcConfiguration.queryMaxFeature.intValue());
        elementSetName = Optional.ofNullable(elementSetName).orElse("summary");
        String[] elements = new String[]{};

        if (elementName != null) {
            elements = new String[elementName.split(",").length];
            int i = 0;
            for (String element : elementName.split(",")) {
                if (element.contains(":")) {
                    elements[i] = elementName.split(":")[1];
                    element = elements[i];
                } else {
                    elements[i] = element;
                }
                if (!Arrays.asList(CSWConstant.DC_FIELDS).contains(element.toLowerCase()))
                    throw new OGCException(OGCExceptionCode.INVALID_PARAMETER_VALUE, "Invalid elementName", "elementName", Service.CSW);
                i++;
            }
        }
        List<CollectionReference> collections = new ArrayList<>();
        switch (requestType) {
            case GetCapabilities:
                GetCapabilitiesHandler getCapabilitiesHandler = cswHandler.getCapabilitiesHandler;
                JAXBElement<CapabilitiesType> getCapabilitiesResponse = getCapabilitiesHandler.getCSWCapabilitiesResponse(Arrays.asList(sectionList),
                        serverUrl + "ogc/csw/?", serverUrl + "ogc/csw/opensearch");
                return Response.ok(getCapabilitiesResponse).type(acceptFormatMediaType).build();
            case GetRecords:
                GetRecordsHandler getRecordsHandler = cswHandler.getRecordsHandler;
                long recordsMatched = dao.countCollectionReferences(ids, query, boundingBox);
                collections = dao.getCollectionReferences(elements, null,
                        maxRecords, startPosition - 1, ids, query, boundingBox);
                if (recordIds != null && recordIds.length() > 0) {
                    if (collections.size() == 0) {
                        throw new OGCException(OGCExceptionCode.NOT_FOUND, "Document not Found", "id", Service.CSW);
                    }
                }
                GetRecordsResponseType getRecordsResponse = getRecordsHandler.getCSWGetRecordsResponse(collections,
                        ElementSetName.valueOf(elementSetName), startPosition - 1, recordsMatched, elements);

                return Response.ok(getRecordsResponse).type(outputFormatMediaType).build();
            case GetRecordById:
                GetRecordsByIdHandler getRecordsByIdHandler = cswHandler.getRecordsByIdHandler;
                collections = dao.getCollectionReferences(elements, null,
                        maxRecords, startPosition - 1, ids, query, boundingBox);
                AbstractRecordType abstractRecordType = getRecordsByIdHandler.getAbstractRecordTypeResponse(collections, ElementSetName.valueOf(elementSetName));
                return Response.ok(abstractRecordType).type(outputFormatMediaType).build();
            default:
                throw new OGCException(OGCExceptionCode.INTERNAL_SERVER_ERROR, "Internal error: Unhandled request '" + request + "'.", Service.CSW);
        }
    }

    @Timed
    @Path("/csw/opensearch")
    @GET
    @Produces({MediaType.APPLICATION_XML, MIME_TYPE__OPENSEARCH_XML})
    @ApiOperation(value = "OpenSearch CSW Description Document",
            produces = MediaType.APPLICATION_XML + "," + MIME_TYPE__OPENSEARCH_XML,
            notes = Documentation.OPENSEARCH_CSW_OPERATION)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "Successful operation"),
            @ApiResponse(code = 500, message = "Arlas Server Error.", response = Error.class), @ApiResponse(code = 400, message = "Bad request.", response = Error.class)})
    public Response opensearch(
            // --------------------------------------------------------
            // -----------------------  EXTRA   -----------------------
            // --------------------------------------------------------
            @ApiParam(value = "max-age-cache", required = false)
            @QueryParam(value = "max-age-cache") Integer maxagecache
    ) throws ArlasException {
        OpenSearchHandler openSearchHandler = cswHandler.openSearchHandler;
        OpenSearchDescription description = openSearchHandler.getOpenSearchDescription(serverUrl);
        return Response.ok(description).build();
    }
}

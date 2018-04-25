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

package io.arlas.server.ogc.csw.writer.getrecords;

import io.arlas.server.app.ArlasServerConfiguration;
import io.arlas.server.ns.ATOM;
import io.arlas.server.ogc.csw.utils.AtomBuilder;
import net.opengis.cat.csw._3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3._2005.atom.*;
import org.w3._2005.atom.ObjectFactory;

import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import javax.xml.bind.JAXB;
import javax.xml.bind.JAXBElement;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;


@Provider
@Produces(ATOM.APPLICATION_ATOM_XML)
public class AtomGetRecordsMessageBodyWriter implements MessageBodyWriter<GetRecordsResponseType> {
    public Logger LOGGER = LoggerFactory.getLogger(AtomGetRecordsMessageBodyWriter.class);

    private ArlasServerConfiguration arlasServerConfiguration;

    public AtomGetRecordsMessageBodyWriter(ArlasServerConfiguration arlasServerConfiguration) {
        this.arlasServerConfiguration = arlasServerConfiguration;
    }


    @Override
    public boolean isWriteable(Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType) {
        return aClass != null && GetRecordsResponseType.class.isAssignableFrom(aClass);
    }

    @Override
    public long getSize(GetRecordsResponseType getRecordsResponseType, Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType) {
        return -1;
    }

    @Override
    public void writeTo(GetRecordsResponseType getRecordsResponseType, Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> multivaluedMap, OutputStream outputStream) throws IOException, WebApplicationException {

        FeedType feedType = new FeedType();
        PersonType personType = new PersonType();
        ObjectFactory objectFactory = new ObjectFactory();
        JAXBElement<String> name = objectFactory.createName("Matthieu Barbet");
        personType.getNameOrUriOrEmail().add(name);
        feedType.getAuthor().add(personType);
        TextType title = objectFactory.createTextType();
        title.getContent().add(this.arlasServerConfiguration.cswConfiguration.openSearchShortName);
        feedType.setTitle(title);
        IdType idTypeValue = new IdType();
        idTypeValue.setValue(String.valueOf(this.arlasServerConfiguration.cswConfiguration.openSearchShortName.hashCode()));
        feedType.setId(idTypeValue);
        GregorianCalendar c = new GregorianCalendar();
        c.setTime(new Date());
        try {
            XMLGregorianCalendar date = DatatypeFactory.newInstance().newXMLGregorianCalendar(c);
            DateTimeType dateTimeType = new DateTimeType();
            dateTimeType.setValue(date);
            feedType.setUpdated(dateTimeType);

        } catch (DatatypeConfigurationException e) {
            e.printStackTrace();
        }
        com.a9.opensearch.ObjectFactory openSearchFactory = new com.a9.opensearch.ObjectFactory();
        BigInteger nextRecord = getRecordsResponseType.getSearchResults().getNextRecord() ;
        BigInteger totalResult= getRecordsResponseType.getSearchResults().getNumberOfRecordsReturned() ;
        Long nextRecordLong = nextRecord.longValue();
        Long totalResultLong = totalResult.longValue();
        JAXBElement<Long> startIndex = openSearchFactory.createStartIndex(nextRecordLong-totalResultLong);
        feedType.getAny().add(startIndex);
        JAXBElement<Long> totalResults = openSearchFactory.createTotalResults(totalResultLong);
        feedType.getAny().add(totalResults);
        JAXBElement<Long> itemPerPages = openSearchFactory.createItemsPerPage(totalResultLong);
        feedType.getAny().add(itemPerPages);

        getRecordsResponseType.getSearchResults().getAbstractRecord().stream().forEach(jaxbElement -> {
            EntryType entryType = new EntryType();
            AtomBuilder.setEntryType(jaxbElement.getValue(),feedType,entryType);
            feedType.getEntry().add(entryType);
        });

        JAXB.marshal(objectFactory.createFeed(feedType),outputStream);
    }
}
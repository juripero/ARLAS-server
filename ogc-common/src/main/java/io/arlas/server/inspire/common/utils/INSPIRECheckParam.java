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

package io.arlas.server.inspire.common.utils;

import io.arlas.server.exceptions.INSPIRE.INSPIREException;
import io.arlas.server.exceptions.INSPIRE.INSPIREExceptionCode;
import io.arlas.server.exceptions.OGC.OGCException;
import io.arlas.server.inspire.common.constants.INSPIREConstants;
import io.arlas.server.inspire.common.enums.InspireSupportedLanguages;
import io.arlas.server.ogc.common.model.Service;

import java.text.ParseException;
import java.text.SimpleDateFormat;

public class INSPIRECheckParam {
    public static void checkLanguageInspireCompliance(String language, Service service) throws INSPIREException {
        try {
            InspireSupportedLanguages.valueOf(language);
        } catch (IllegalArgumentException e) {
            throw new INSPIREException(INSPIREExceptionCode.INVALID_INSPIRE_PARAMETER_VALUE, language + " is not a valid language. Languages must be one of the 24 Official languages of the EU in ISO 639-2 (B)", service);
        }
    }

    public static boolean isDateFormatValidForGetRecords(String date) {
        try {
            new SimpleDateFormat(INSPIREConstants.CSW_METADATA_DATE_FORMAT).parse(date);
            return true;
        } catch (ParseException e) {
            return false;
        }
    }
}

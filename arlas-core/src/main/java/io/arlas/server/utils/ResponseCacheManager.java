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

package io.arlas.server.utils;

import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.Response;

public class ResponseCacheManager {

    private int defaultMaxAgeCache = 0;

    public ResponseCacheManager(int defaultMaxAgeCache) {
        this.defaultMaxAgeCache = defaultMaxAgeCache;
    }

    public Response cache(Response.ResponseBuilder response, Integer maxagecache) {
        if (defaultMaxAgeCache > 0 || maxagecache != null) {
            if (maxagecache == null) {
                maxagecache = defaultMaxAgeCache;// defaultMaxAgeCache is defined in ARLAS configuration file
            }

            CacheControl cc = new CacheControl();
            cc.setPrivate(false);
            cc.setNoCache(false);
            cc.setNoTransform(true);
            cc.setMaxAge(maxagecache);

            response.cacheControl(cc);
        }

        return response.build();
    }
}
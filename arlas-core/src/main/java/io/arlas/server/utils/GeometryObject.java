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

import org.geojson.*;

import java.util.List;

public class GeometryObject {
    private Object coordinates;
    private String type;

    public Object getCoordinates() {
        return coordinates;
    }

    public void setCoordinates(List<Object> coordinates) {
        this.coordinates = coordinates;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public static GeometryObject toGeometryObject(GeoJsonObject geoJsonObject) {
        GeometryObject geometryObject = new GeometryObject();
        if (geoJsonObject instanceof Point) {
            geometryObject.coordinates = ((Point) geoJsonObject).getCoordinates();
            geometryObject.type = Point.class.getSimpleName();
        } else if (geoJsonObject instanceof Polygon) {
            geometryObject.coordinates = ((Polygon) geoJsonObject).getCoordinates();
            geometryObject.type = Polygon.class.getSimpleName();
        } else if (geoJsonObject instanceof LineString) {
            geometryObject.coordinates = ((LineString) geoJsonObject).getCoordinates();
            geometryObject.type = LineString.class.getSimpleName();
        } else {
            geometryObject = null;
        }
        return geometryObject;
    }

    public  GeoJsonObject toGeoJsonObject() {
        if (type.equals(Point.class.getSimpleName())) {
            return new Point((LngLatAlt)getCoordinates());
        } else if (type.equals(Polygon.class.getSimpleName())) {
            return new Polygon((List<LngLatAlt>)getCoordinates());
        } else if (type.equals(LineString.class.getSimpleName())) {
            return new LineString(((List<LngLatAlt>)getCoordinates()).toArray(new LngLatAlt[((List<LngLatAlt>)getCoordinates()).size()]));
        }
        return null;
    }


}

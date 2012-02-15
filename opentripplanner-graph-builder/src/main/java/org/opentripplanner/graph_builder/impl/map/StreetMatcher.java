/* This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public License
 as published by the Free Software Foundation, either version 3 of
 the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package org.opentripplanner.graph_builder.impl.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.opentripplanner.routing.edgetype.StreetEdge;
import org.opentripplanner.routing.graph.Edge;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.graph.Vertex;
import org.opentripplanner.routing.pqueue.BinHeap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.index.strtree.STRtree;
import com.vividsolutions.jts.linearref.LinearLocation;
import com.vividsolutions.jts.linearref.LocationIndexedLine;

public class StreetMatcher {
    private static final Logger log = LoggerFactory.getLogger(StreetMatcher.class);
    private static final double DISTANCE_THRESHOLD = 0.0002;

    Graph graph;

    private STRtree index;

    STRtree createIndex() {
        STRtree edgeIndex = new STRtree();
        for (Vertex v : graph.getVertices()) {
            for (Edge e : v.getOutgoing()) {
                if (e instanceof StreetEdge) {
                    Envelope envelope;
                    Geometry geometry = e.getGeometry();
                    envelope = geometry.getEnvelopeInternal();
                    edgeIndex.insert(envelope, e);
                }
            }
        }
        log.debug("Created index");
        return edgeIndex;
    }

    public StreetMatcher(Graph graph) {
        this.graph = graph;
        index = createIndex();
        index.build();
    }

    public List<Edge> match(Geometry routeGeometry) {
        
        routeGeometry = removeDuplicatePoints(routeGeometry);
        
        // initial state: start midway along a block.
        LocationIndexedLine indexedLine = new LocationIndexedLine(routeGeometry);

        LinearLocation startIndex = indexedLine.getStartIndex();

        Coordinate routeStartCoordinate = startIndex.getCoordinate(routeGeometry);
        Envelope envelope = new Envelope(routeStartCoordinate);
        envelope.expandBy(DISTANCE_THRESHOLD);

        BinHeap<MatchState> states = new BinHeap<MatchState>();
        // compute initial states
        for (Object obj : index.query(envelope)) {
            Edge initialEdge = (Edge) obj;
            Geometry edgeGeometry = initialEdge.getGeometry();
            
            LocationIndexedLine indexedEdge = new LocationIndexedLine(edgeGeometry);
            LinearLocation initialLocation = indexedEdge.project(routeStartCoordinate);
            
            double error = MatchState.distance(initialLocation.getCoordinate(edgeGeometry), routeStartCoordinate);
            MatchState state = new MidblockMatchState(null, routeGeometry, initialEdge, startIndex, initialLocation, error, 0);
            states.insert(state, error);
        }

        // search for best-matching path
        int seen_count = 0, total = 0;
        HashSet<MatchState> seen = new HashSet<MatchState>(); 
        while (!states.empty()) {
            MatchState state = states.extract_min();
            if (++total % 10000 == 0) {
                log.debug("seen / total: " + seen_count + " / " + total);
            }
            if (seen.contains(state)) {
                ++seen_count;
                continue;
            } else {
                seen.add(state);
            }
            if (state instanceof EndMatchState) {
                return toEdgeList(state);
            }
            for (MatchState next : state.getNextStates()) {
                if (seen.contains(next)) {
                    continue;
                }
                states.insert(next, next.getTotalError() / next.getDistanceAlongRoute());
            }
        }
        return null;
    }

    private Geometry removeDuplicatePoints(Geometry routeGeometry) {
        List<Coordinate> coords = new ArrayList<Coordinate>();
        Coordinate last = null;
        for (Coordinate c : routeGeometry.getCoordinates()) {
            if (!c.equals(last)) {
                last = c;
                coords.add(c);
            }
        }
        Coordinate[] coordArray = new Coordinate[coords.size()];
        return routeGeometry.getFactory().createLineString(coords.toArray(coordArray));
    }

    private List<Edge> toEdgeList(MatchState next) {
        ArrayList<Edge> edges = new ArrayList<Edge>();
        Edge lastEdge = null;
        while (next != null) {
            Edge edge = next.getEdge();
            if (edge != lastEdge) {
                edges.add(edge);
                lastEdge = edge;
            }
            next = next.parent;
        }
        Collections.reverse(edges);
        return edges;
    }
}
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

package org.opentripplanner.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import org.opentripplanner.routing.core.DirectEdge;
import org.opentripplanner.routing.core.Edge;
import org.opentripplanner.routing.core.Graph;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.core.TraverseModeSet;
import org.opentripplanner.routing.core.TraverseOptions;
import org.opentripplanner.routing.core.Vertex;
import org.opentripplanner.routing.edgetype.EndpointVertex;
import org.opentripplanner.routing.edgetype.PlainStreetEdge;
import org.opentripplanner.routing.edgetype.StreetEdge;
import org.opentripplanner.routing.edgetype.StreetTraversalPermission;
import org.opentripplanner.routing.edgetype.StreetVertex;
import org.opentripplanner.routing.edgetype.TurnEdge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StreetUtils {

    private static Logger _log = LoggerFactory.getLogger(StreetUtils.class);

    /**
     * Make an ordinary graph into an edge-based graph.
     * @param endpoints 
     * @param coordinateToStreetNames 
     */
    public static void makeEdgeBased(Graph graph, Collection<Vertex> endpoints, Map<Edge, TurnRestriction> restrictions) {
        
        /* generate turns */

        _log.debug("converting to edge-based graph");
        
	// TODO: make this not dependent on EndpointVertex
	// will require defining makeVertex on a superclass of some sort - either Vertex or a new
	// one.
	for (Vertex vraw : endpoints) {
	    EndpointVertex v = (EndpointVertex) vraw;

	    // mattwigway 2012-01-07: most of the code that used to be here is in 
	    // EndpointVertex.java now
	    // makeEdges calls makeVertex on edges to convert them to the appropriate vertex type
	    v.makeEdges(graph, restrictions);
	}

        /* remove standard graph */
        for (Vertex v : endpoints) {
            graph.removeVertexAndEdges(v);
        }

	// turns are now added directly
	// is there a reason it is a bad idea to add turns as-we-go?
        /* add turns 
        for (DirectEdge e : turns) {
            graph.addEdge(e);
	    } */
    }

    public static void pruneFloatingIslands(Graph graph) {
    	_log.debug("pruning");
    	Map<Vertex, HashSet<Vertex>> subgraphs = new HashMap<Vertex, HashSet<Vertex>>();
    	Map<Vertex, ArrayList<Vertex>> neighborsForVertex = new HashMap<Vertex, ArrayList<Vertex>>();
    	
    	TraverseOptions options = new TraverseOptions(new TraverseModeSet(TraverseMode.WALK));
    	
    	for (Vertex gv : graph.getVertices()) {
    		if (!(gv instanceof EndpointVertex)) {
    			continue;
    		}
        	State s0 = new State(gv, options);
    		for (Edge e: gv.getOutgoing()) {
    			Vertex in = gv;
    			if (!(e instanceof StreetEdge)) {
        			continue;
        		}
    			State s1 = e.traverse(s0);
    			if (s1 == null) {
    				continue;
    			}
    			Vertex out = s1.getVertex();
    			
    			ArrayList<Vertex> vertexList = neighborsForVertex.get(in);
    			if (vertexList == null) {
    				vertexList = new ArrayList<Vertex>();
    				neighborsForVertex.put(in, vertexList);
                }
                vertexList.add(out);
                
    			vertexList = neighborsForVertex.get(out);
    			if (vertexList == null) {
    				vertexList = new ArrayList<Vertex>();
    				neighborsForVertex.put(out, vertexList);
                }
                vertexList.add(in);
    		}
    	}
   	
    	ArrayList<HashSet<Vertex>> islands = new ArrayList<HashSet<Vertex>>();
    	/* associate each node with a subgraph */
    	for (Vertex gv : graph.getVertices()) {
    		if (!(gv instanceof EndpointVertex)) {
    			continue;
    		}
    		Vertex vertex = gv;
    		if (subgraphs.containsKey(vertex)) {
    			continue;
    		}
    		if (!neighborsForVertex.containsKey(vertex)) {
    			continue;
    		}
    		HashSet<Vertex> subgraph = computeConnectedSubgraph(neighborsForVertex, vertex);
            for (Vertex subnode : subgraph) {
                subgraphs.put(subnode, subgraph);
            }
            islands.add(subgraph);
    	}
    	
    	/* remove all tiny subgraphs */
/* removed 10/27/11, since it looks like PDX is fixed.
    	for (HashSet<Vertex> island : islands) {
    		if (island.size() < 20) {
    			_log.warn("Depedestrianizing or deleting floating island at " + island.iterator().next());
    			for (Vertex vertex : island) {
    				depedestrianizeOrRemove(graph, vertex);
    			}
    		} 
    	}
*/
    }
    
    private static void depedestrianizeOrRemove(Graph graph, Vertex v) {
    	Collection<Edge> outgoing = new ArrayList<Edge>(v.getOutgoing());
		for (Edge e : outgoing) {
    		if (e instanceof PlainStreetEdge) {
    			PlainStreetEdge pse = (PlainStreetEdge) e;
    			StreetTraversalPermission permission = pse.getPermission();
    			permission = permission.remove(StreetTraversalPermission.PEDESTRIAN);
    			if (permission == StreetTraversalPermission.NONE) {
    				graph.removeEdge(pse);
    			} else {
    				pse.setPermission(permission);
    			}
    		}
    	}
		if (v.getOutgoing().size() == 0) {
			graph.removeVertexAndEdges(v);
		}
	}

	private static HashSet<Vertex> computeConnectedSubgraph(
    		Map<Vertex, ArrayList<Vertex>> neighborsForVertex, Vertex startVertex) {
    	HashSet<Vertex> subgraph = new HashSet<Vertex>();
    	Queue<Vertex> q = new LinkedList<Vertex>();
    	q.add(startVertex);
    	while (!q.isEmpty()) {
    		Vertex vertex = q.poll();
    		for (Vertex neighbor : neighborsForVertex.get(vertex)) {
    			if (!subgraph.contains(neighbor)) {
    				subgraph.add(neighbor);
    				q.add(neighbor);
    			}
    		}
    	}
    	return subgraph;
    }
	
}

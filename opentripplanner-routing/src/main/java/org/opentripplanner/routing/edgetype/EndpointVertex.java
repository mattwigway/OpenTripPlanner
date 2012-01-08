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

package org.opentripplanner.routing.edgetype;

import java.util.Map;
import java.util.ArrayList;

import org.opentripplanner.routing.core.Vertex;
import org.opentripplanner.routing.core.Graph;
import org.opentripplanner.routing.core.Edge;
import org.opentripplanner.routing.core.DirectEdge;
import org.opentripplanner.common.TurnRestriction;
import org.opentripplanner.common.TurnRestrictionType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Represents an ordinary location in space, typically an intersection */

public class EndpointVertex extends Vertex {

    private static Logger _log = LoggerFactory.getLogger(EndpointVertex.class);

    public EndpointVertex(String label, double x, double y, String name) {
        super(label, x, y, name);
    }
    
    public EndpointVertex(String label, double x, double y) {
        super(label, x, y, label);
    }

    /**
     * Convert this to a bunch of TurnEdges. Moved from StreetUtils to permit multitype edging in
     * OpenStreetMapGraphBuilderImpl.
     * @param graph The graph to modify.
     * @param restrictions Turn restrictions.
     * @author mattwigway
     */
    public ArrayList<DirectEdge> makeEdges (Graph graph, Map<Edge, TurnRestriction> restrictions) {
	// this is what will be passed back to the caller to add to the graph later
	ArrayList<DirectEdge> turns = new ArrayList<DirectEdge>();

	// test to make sure we're working on our own graph
	Vertex gv = graph.getVertex(getLabel());
	
	if (gv == null) {
	    return null; // the vertex could have been removed from endpoints
	}
	if (gv != this) {
	    throw new IllegalStateException("Vertex in graph is not the same one at endpoint.");
	}

	// nested loop to make TurnEdges from every incoming street to every outgoing one.
	for (Edge eraw : getIncoming()) {

	    // cast to StreetEdge to make sure we have makeVertex
	    StreetEdge e = (StreetEdge) eraw;

	    boolean replaced = false;

	    StreetVertex v1 = (StreetVertex) e.makeVertex(graph);
	    TurnRestriction restriction = null;

	    // TODO: should this be restricted to PlainStreetEdges?
	    if (restrictions != null) {
		restriction = restrictions.get(e);
	    }
	    
	    for (Edge e2raw : getOutgoing()) {
		StreetEdge e2 = (StreetEdge) e2raw;

		StreetVertex v2 = (StreetVertex) e2.makeVertex(graph);
                    
		// build the edge
		TurnEdge turn = new TurnEdge(v1, v2);

		// check for turn restrictions
		if (restriction != null) {
		    // if you can't turn in certain modes, notate that
		    if (restriction.type == TurnRestrictionType.NO_TURN && restriction.to == e2) {
			turn.setRestrictedModes(restriction.modes);
		    // if certain modes are *required* to turn onto an edge other than this one,
		    // don't allow that mode to turn onto this edge from that one.
		    } else if (restriction.type == TurnRestrictionType.ONLY_TURN && 
			       restriction.to != e2) {
			turn.setRestrictedModes(restriction.modes);
		    }
		}

		// don't add turn edges from an edge to itself
		if (v1 != v2 && !v1.getEdgeId().equals(v2.getEdgeId())) {
		    turns.add(turn);
		    replaced = true;
		}
	    }

	    // TODO: what exactly does this do?
	    if (!replaced) {
		/*
		 * NOTE that resetting the from-vertex only works because all of the 
		 * endpoint vertices will soon have their edgelists reinitialized, and 
		 * then all these edges will be re-added to the graph.
		 * This can and does have rather unpredictable behavior, and should 
		 * eventually be changed.
		 */
		if (e instanceof PlainStreetEdge) {
		    PlainStreetEdge pse = (PlainStreetEdge) e;
		    pse.setFromVertex(v1);
		    turns.add(pse);
		}
		else {
		    // should build a DirectEdge here
		    _log.warn("Not resetting from vertex on edge " + e.toString() + 
			      " because it is not an instance of PlainStreetEdge." +
			      " Unpredictable behavior may result");
		}
	    }
	}

	return turns;
    }


    private static final long serialVersionUID = 1L;
}

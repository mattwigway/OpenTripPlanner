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

import org.opentripplanner.routing.core.DirectEdge;
import org.opentripplanner.routing.core.TraverseOptions;
import org.opentripplanner.routing.core.Vertex;
import org.opentripplanner.routing.core.Graph;

/** Interface for edges representing streets */
public interface StreetEdge extends EdgeWithElevation, DirectEdge {
    public boolean canTraverse(TraverseOptions options);
    public double getLength();
    public StreetTraversalPermission getPermission();
    public boolean isNoThruTraffic();
    /**
     * Make this edge into a vertex, and build any requisite edges to connect it to the
     * rest of Graph graph, which may be either the graph which contains the edge currently
     * or any other graph.
     * @param graph
     * @author mattwigway
     */
    public Vertex makeVertex(Graph graph);
}

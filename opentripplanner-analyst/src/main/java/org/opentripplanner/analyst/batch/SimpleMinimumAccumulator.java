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

package org.opentripplanner.analyst.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a simple accumulator that just takes the minimum time to access one of the features
 * and outputs that. This is useful for, say, accessibility to mailboxes; one mailbox a five-minute
 * walk away is sufficient, and two mailboxes a five-minute walk away is really no improvement.
 * 
 * In other words, this is useful when the opportunities are so homogeneous as to be interchangeable.
 * 
 * I haven't seen this anywhere in the literature but it's such a simple idea it's probably been
 * used somewhere before.
 * 
 * @author mattwigway
 */
public class SimpleMinimumAccumulator implements Accumulator {
    private static final Logger LOG = LoggerFactory.getLogger(SimpleMinimumAccumulator.class);
    
    @Override
    public void accumulate(double amount, ResultSet current, ResultSet accumulated) {
        if (current.population != accumulated.population) {
            LOG.error("population mismatch");
            return;
        }
        
        int size = accumulated.population.size();
        for (int i = 0; i < size; i++) {
            // TODO: what if time is 0 (points are coincident)?
            if (accumulated.results[i] == 0 || current.results[i] < accumulated.results[i]) {
                accumulated.results[i] = current.results[i];
            }
        }
    }

    @Override
    public void finish() {
        // TODO Auto-generated method stub

    }

}

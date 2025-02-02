/*  This file is part of Openrouteservice.
 *
 *  Openrouteservice is free software; you can redistribute it and/or modify it under the terms of the 
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version 2.1 
 *  of the License, or (at your option) any later version.

 *  This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; 
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
 *  See the GNU Lesser General Public License for more details.

 *  You should have received a copy of the GNU Lesser General Public License along with this library; 
 *  if not, see <https://www.gnu.org/licenses/>.  
 */
package org.heigit.ors.matrix.algorithms.rphast;

import java.util.ArrayList;
import java.util.List;

import com.graphhopper.GraphHopper;
import com.graphhopper.routing.ch.PrepareContractionHierarchies;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;

import org.heigit.ors.matrix.MatrixLocations;
import org.heigit.ors.matrix.MatrixMetricsType;
import org.heigit.ors.matrix.MatrixRequest;
import org.heigit.ors.matrix.MatrixResult;
import org.heigit.ors.matrix.MultiTreeMetricsExtractor;
import org.heigit.ors.matrix.algorithms.AbstractMatrixAlgorithm;
import org.heigit.ors.routing.algorithms.RPHASTAlgorithm;
import org.heigit.ors.routing.graphhopper.extensions.storages.MultiTreeSPEntry;

public class RPHASTMatrixAlgorithm extends AbstractMatrixAlgorithm {
	private PrepareContractionHierarchies prepareCH;
	private MultiTreeMetricsExtractor pathMetricsExtractor;

	@Override
	public void init(MatrixRequest req, GraphHopper gh, Graph graph, FlagEncoder encoder, Weighting weighting) {
		super.init(req, gh, graph, encoder, weighting);

		prepareCH = graphHopper.getCHFactoryDecorator().getPreparations().get(0);
		pathMetricsExtractor = new MultiTreeMetricsExtractor(req.getMetrics(), graph, this.encoder, weighting,
				req.getUnits());
	}

	@Override
	public MatrixResult compute(MatrixLocations srcData, MatrixLocations dstData, int metrics) throws Exception {
		MatrixResult mtxResult = new MatrixResult(srcData.getLocations(), dstData.getLocations());

		float[] times = null;
		float[] distances = null;
		float[] weights = null;

		int tableSize = srcData.size() * dstData.size();
		if (MatrixMetricsType.isSet(metrics, MatrixMetricsType.DURATION))
			times = new float[tableSize];
		if (MatrixMetricsType.isSet(metrics, MatrixMetricsType.DISTANCE))
			distances = new float[tableSize];
		if (MatrixMetricsType.isSet(metrics, MatrixMetricsType.WEIGHT))
			weights = new float[tableSize];

		if (!srcData.hasValidNodes() || !dstData.hasValidNodes()) {
			for (int srcIndex = 0; srcIndex < srcData.size(); srcIndex++) 
				pathMetricsExtractor.setEmptyValues(srcIndex, dstData, times, distances, weights);
		} else {
			RPHASTAlgorithm algorithm = new RPHASTAlgorithm(graph, prepareCH.getPrepareWeighting(),
					TraversalMode.NODE_BASED);
			
			int[] srcIds = getValidNodeIds(srcData.getNodeIds());
			int[] destIds = getValidNodeIds(dstData.getNodeIds());
			
			algorithm.prepare(srcIds, destIds);

			MultiTreeSPEntry[] destTrees = algorithm.calcPaths(srcIds, destIds);

			MultiTreeSPEntry[] originalDestTrees = new MultiTreeSPEntry[dstData.size()];
			
			int j = 0;
			for (int i = 0; i < dstData.size(); i++) {
				if (dstData.getNodeIds()[i] != -1) {
					originalDestTrees[i] = destTrees[j];
					++j;
				} else {
					originalDestTrees[i] = null;
				}
			}

			pathMetricsExtractor.calcValues(originalDestTrees, srcData, dstData, times, distances, weights);
		}

		if (MatrixMetricsType.isSet(metrics, MatrixMetricsType.DURATION))
			mtxResult.setTable(MatrixMetricsType.DURATION, times);
		if (MatrixMetricsType.isSet(metrics, MatrixMetricsType.DISTANCE))
			mtxResult.setTable(MatrixMetricsType.DISTANCE, distances);
		if (MatrixMetricsType.isSet(metrics, MatrixMetricsType.WEIGHT))
			mtxResult.setTable(MatrixMetricsType.WEIGHT, weights);

		return mtxResult;
	}
	
	private int[] getValidNodeIds(int[] nodeIds) {
		List<Integer> nodeList = new ArrayList<>();
		for (int dst : nodeIds) {
			if (dst != -1)
				nodeList.add(dst);

		}
		
		int[] res = new int[nodeList.size()];
		for (int i = 0; i < nodeList.size(); i++) 
			res[i] = nodeList.get(i);
		
		return res;
	}
}

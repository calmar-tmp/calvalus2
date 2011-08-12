/*
 * Copyright (C) 2010 Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

package com.bc.calvalus.processing.l3;

import com.bc.calvalus.binning.BinningGrid;
import com.bc.calvalus.binning.SpatialBin;
import com.bc.calvalus.processing.JobConfNames;
import com.bc.calvalus.processing.JobUtils;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import org.apache.hadoop.conf.Configurable;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.mapreduce.Partitioner;

/**
 * Partitions the bins by their bin index.
 * Reduces will receive spatial bins of contiguous latitude ranges.
 *
 * @author Marco Zuehlke
 * @author Norman Fomferra
 */
public class L3Partitioner extends Partitioner<LongWritable, SpatialBin> implements Configurable {

    private Configuration conf;
    private BinningGrid binningGrid;
    private int minRowIndex;
    private int numRowsCovered;

    @Override
    public int getPartition(LongWritable binIndex, SpatialBin spatialBin, int numPartitions) {
        long idx = binIndex.get();
        int row = binningGrid.getRowIndex(idx);
        int partition = ((row - minRowIndex) * numPartitions) / numRowsCovered;
        if (partition < 0) {
            partition = 0;
        } else if (partition >= numPartitions) {
            partition = numPartitions - 1;
        }
        return partition;
    }

    @Override
    public void setConf(Configuration conf) {
        this.conf = conf;
        String level3Parameters = conf.get(JobConfNames.CALVALUS_L3_PARAMETERS);
        L3Config l3Config = L3Config.fromXml(level3Parameters);
        this.binningGrid = l3Config.getBinningGrid();
        String regionGeometry = conf.get(JobConfNames.CALVALUS_REGION_GEOMETRY);
        Geometry roiGeometry = JobUtils.createGeometry(regionGeometry);
        if (roiGeometry != null && !roiGeometry.isEmpty()) {
            Envelope envelope = roiGeometry.getEnvelopeInternal();
            double minY = envelope.getMinY();
            double maxY = envelope.getMaxY();
            int maxRowIndex = binningGrid.getRowIndex(binningGrid.getBinIndex(minY, 0));
            minRowIndex = binningGrid.getRowIndex(binningGrid.getBinIndex(maxY, 0));
            numRowsCovered = maxRowIndex - minRowIndex + 1;
        } else {
            numRowsCovered = binningGrid.getNumRows();
            minRowIndex = 0;
        }
    }

    @Override
    public Configuration getConf() {
        return conf;
    }

    BinningGrid getBinningGrid() {
        return binningGrid;
    }
}

/*
 * Copyright (C) 2012 Brockmann Consult GmbH (info@brockmann-consult.de)
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

package com.bc.calvalus.processing.beam;

import com.bc.calvalus.processing.ProcessorAdapter;
import com.bc.ceres.core.ProgressMonitor;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.MapContext;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.gpf.operators.standard.SubsetOp;
import org.esa.beam.util.io.FileUtils;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.io.IOException;

/**
 * A processor adapter that does not process the input product.
 * It only subsets it using the geometry.
 *
 * @author MarcoZ
 */
public class IdentityProcessorAdapter extends ProcessorAdapter {

    private static final int DEFAULT_TILE_HEIGHT = 64;

    private Product targetProduct;

    public IdentityProcessorAdapter(MapContext mapContext) {
        super(mapContext);
        GpfUtils.init(mapContext.getConfiguration());
    }

    @Override
    public String[] getPredictedProductPathes() {
        String inputFilename = getInputPath().getName();
        String outputFilename = "L2_of_" + FileUtils.exchangeExtension(inputFilename, ".seq");
        return new String[]{ outputFilename };
    }

    @Override
    public int processSourceProduct(ProgressMonitor pm) throws IOException {
        pm.setSubTaskName("L2 Identity");

        targetProduct = createSubset();
        if (targetProduct == null ||
                targetProduct.getSceneRasterWidth() == 0 ||
                targetProduct.getSceneRasterHeight() == 0) {
            return 0;
        }
        getLogger().info(String.format("Processed product width = %d height = %d",
                                       targetProduct.getSceneRasterWidth(),
                                       targetProduct.getSceneRasterHeight()));
        return 1;
    }

    @Override
    public Product openProcessedProduct() {
        return targetProduct;
    }

    @Override
    public void saveProcessedProducts(ProgressMonitor pm) throws Exception {
        saveTargetProduct(targetProduct, pm);
    }

    @Override
    public void dispose() {
        super.dispose();
        if (targetProduct != null) {
            targetProduct.dispose();
            targetProduct = null;
        }
    }

    protected void saveTargetProduct(Product product, ProgressMonitor pm) throws IOException, InterruptedException {
        MapContext mapContext = getMapContext();
        String inputFilename = getInputPath().getName();
        String outputFilename = "L2_of_" + FileUtils.exchangeExtension(inputFilename, ".seq");

        Path workOutputProductPath = new Path(FileOutputFormat.getWorkOutputPath(mapContext), outputFilename);
        int tileHeight = DEFAULT_TILE_HEIGHT;
        Dimension preferredTileSize = product.getPreferredTileSize();
        if (preferredTileSize != null) {
            tileHeight = preferredTileSize.height;
        }
        StreamingProductWriter streamingProductWriter = new StreamingProductWriter(getConfiguration(), mapContext, pm);
        streamingProductWriter.writeProduct(product, workOutputProductPath, tileHeight);
    }

    protected Product createSubset() throws IOException {
        Product product = getInputProduct();
        // full region
        Rectangle srcProductRect = getInputRectangle();
        if (srcProductRect == null ||
                (srcProductRect.width == product.getSceneRasterWidth() && srcProductRect.height == product.getSceneRasterHeight())) {
            return product;
        }
        if (srcProductRect.isEmpty()) {
            throw new IllegalStateException("Can not create an empty subset.");
        }

        final SubsetOp op = new SubsetOp();
        op.setSourceProduct(product);
        op.setRegion(srcProductRect);
        op.setCopyMetadata(false);
        Product subsetProduct = op.getTargetProduct();
        getLogger().info(String.format("Created Subset product width = %d height = %d",
                                       subsetProduct.getSceneRasterWidth(),
                                       subsetProduct.getSceneRasterHeight()));
        return subsetProduct;
    }
}
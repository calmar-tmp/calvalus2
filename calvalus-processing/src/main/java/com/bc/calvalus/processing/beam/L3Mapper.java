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

package com.bc.calvalus.processing.beam;

import com.bc.calvalus.binning.BinningContext;
import com.bc.calvalus.binning.ObservationSlice;
import com.bc.calvalus.binning.SpatialBin;
import com.bc.calvalus.binning.SpatialBinProcessor;
import com.bc.calvalus.binning.SpatialBinner;
import com.bc.calvalus.commons.CalvalusLogger;
import com.bc.ceres.glevel.MultiLevelImage;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;
import org.esa.beam.framework.datamodel.GeoCoding;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.PixelPos;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.datamodel.RasterDataNode;
import org.esa.beam.framework.datamodel.VirtualBand;
import org.esa.beam.jai.ImageManager;

import java.awt.Point;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads an N1 product and produces an emits (binIndex, spatialBin) pairs.
 *
 * @author Marco Zuehlke
 * @author Norman Fomferra
 */
public class L3Mapper extends Mapper<NullWritable, NullWritable, LongWritable, SpatialBin> {

    private static final Logger LOG = CalvalusLogger.getLogger();

    @Override
    public void run(Context context) throws IOException, InterruptedException {
        BeamProductHandler.init();
        final Configuration hadoopConfiguration = context.getConfiguration();
        WpsConfig wpsConfig = WpsConfig.createFromJobConfig(hadoopConfiguration);
        BeamL3Config l3Config = BeamL3Config.create(wpsConfig.getRequestXmlDoc());
        l3Config.validateConfiguration();
        BeamL2Config beamConfig = new BeamL2Config(wpsConfig);

        final BinningContext ctx = l3Config.getBinningContext();
        final SpatialBinEmitter spatialBinEmitter = new SpatialBinEmitter(context);
        final SpatialBinner spatialBinner = new SpatialBinner(ctx, spatialBinEmitter);

        final FileSplit split = (FileSplit) context.getInputSplit();

        // write initial log entry for runtime measurements
        LOG.info(MessageFormat.format("{0} starts processing of split {1}", context.getTaskAttemptID(), split));
        final long startTime = System.nanoTime();

        final Path inputPath = split.getPath();
        BeamProductHandler beamProductHandler = new BeamProductHandler();
        Product source = beamProductHandler.readProduct(inputPath, hadoopConfiguration);

        final Product product = l3Config.getPreProcessedProduct(source, beamConfig);
        if (product != null) {
            try {
                processProduct(product, ctx, spatialBinner);
            } finally {
                product.dispose();
            }
        } else {
            LOG.info("product not used");
        }

        long stopTime = System.nanoTime();

        final Exception[] exceptions = spatialBinner.getExceptions();
        for (Exception exception : exceptions) {
            String m = MessageFormat.format("Failed to process input slice of split {0}", split);
            LOG.log(Level.SEVERE, m, exception);
        }
        // write final log entry for runtime measurements
        LOG.info(MessageFormat.format("{0} stops processing of split {1} after {2} sec ({3} observations seen, {4} bins produced)",
                                      context.getTaskAttemptID(), split, (stopTime - startTime) / 1E9, spatialBinEmitter.numObsTotal, spatialBinEmitter.numBinsTotal));

    }

    static void processProduct(Product product,
                               BinningContext ctx,
                               SpatialBinner spatialBinner) {
        if (product.getGeoCoding() == null) {
            throw new IllegalArgumentException("product.getGeoCoding() == null");
        }

        final int sliceWidth = product.getSceneRasterWidth();

        for (int i = 0; i < ctx.getVariableContext().getVariableCount(); i++) {
            String variableName = ctx.getVariableContext().getVariableName(i);
            String variableExpr = ctx.getVariableContext().getVariableExpr(i);
            if (variableExpr != null) {
                VirtualBand band = new VirtualBand(variableName,
                                                   ProductData.TYPE_FLOAT32,
                                                   product.getSceneRasterWidth(),
                                                   product.getSceneRasterHeight(),
                                                   variableExpr);
                band.setValidPixelExpression(ctx.getVariableContext().getMaskExpr());
                product.addBand(band);
            }
        }

        final String maskExpr = ctx.getVariableContext().getMaskExpr();
        final MultiLevelImage maskImage = ImageManager.getInstance().getMaskImage(maskExpr, product);
        final int sliceHeight = maskImage.getTileHeight();
        checkImageTileSize(MessageFormat.format("Mask image for expr ''{0}''", maskExpr),
                           maskImage, sliceWidth, sliceHeight);

        final MultiLevelImage[] varImages = new MultiLevelImage[ctx.getVariableContext().getVariableCount()];
        for (int i = 0; i < ctx.getVariableContext().getVariableCount(); i++) {
            final String nodeName = ctx.getVariableContext().getVariableName(i);
            final RasterDataNode node = getRasterDataNode(product, nodeName);
            final MultiLevelImage varImage = node.getGeophysicalImage();
            checkImageTileSize(MessageFormat.format("Geophysical image for node ''{0}''", node.getName()),
                               maskImage, sliceWidth, sliceHeight);
            varImages[i] = varImage;
        }

        final GeoCoding geoCoding = product.getGeoCoding();
        final Point[] tileIndices = maskImage.getTileIndices(null);
        ObservationSlice observationSlice;
        for (Point tileIndex : tileIndices) {
            observationSlice = createObservationSlice(geoCoding,
                                                      maskImage, varImages,
                                                      tileIndex,
                                                      sliceWidth, sliceHeight);
            spatialBinner.processObservationSlice(observationSlice);
        }

        spatialBinner.complete();
    }

    private static ObservationSlice createObservationSlice(GeoCoding geoCoding,
                                                           RenderedImage maskImage,
                                                           RenderedImage[] varImages,
                                                           Point tileIndex,
                                                           int sliceWidth,
                                                           int sliceHeight) {

        final Raster maskTile = maskImage.getTile(tileIndex.x, tileIndex.y);
        final Raster[] varTiles = new Raster[varImages.length];
        for (int i = 0; i < varImages.length; i++) {
            varTiles[i] = varImages[i].getTile(tileIndex.x, tileIndex.y);
        }

        final ObservationSlice observationSlice = new ObservationSlice(varTiles, sliceWidth * sliceHeight);
        final int y1 = maskTile.getMinY();
        final int y2 = y1 + maskTile.getHeight();
        final int x1 = maskTile.getMinX();
        final int x2 = x1 + sliceWidth;
        final PixelPos pixelPos = new PixelPos();
        final GeoPos geoPos = new GeoPos();
        for (int y = y1; y < y2; y++) {
            for (int x = x1; x < x2; x++) {
                if (maskTile.getSample(x, y, 0) != 0) {
                    pixelPos.setLocation(x + 0.5f, y + 0.5f);
                    geoCoding.getGeoPos(pixelPos, geoPos);
                    observationSlice.addObservation(geoPos.lat, geoPos.lon, x, y);
                }
            }
        }

        return observationSlice;
    }

    private static RasterDataNode getRasterDataNode(Product product, String nodeName) {
        final RasterDataNode node = product.getRasterDataNode(nodeName);
        if (node == null) {
            throw new IllegalStateException(String.format("Can't find raster data node '%s' in product '%s'",
                                                          nodeName, product.getName()));
        }
        return node;
    }

    private static void checkImageTileSize(String msg, MultiLevelImage image, int sliceWidth, int sliceHeight) {
        if (image.getTileWidth() != sliceWidth
                || image.getTileHeight() != sliceHeight) {
            throw new IllegalStateException(MessageFormat.format(
                    "{0}: unexpected slice size detected: " +
                            "expected {1} x {2}, " +
                            "but was image tile size was {3} x {4} pixels (BEAM reader problem?)",
                    msg,
                    sliceWidth,
                    sliceHeight,
                    image.getTileWidth(),
                    image.getTileHeight()));
        }
    }

    private static class SpatialBinEmitter implements SpatialBinProcessor {
        private Context context;
        int numObsTotal = 0;
        int numBinsTotal = 0;

        public SpatialBinEmitter(Context context) {
            this.context = context;
        }

        @Override
        public void processSpatialBinSlice(BinningContext ctx, List<SpatialBin> spatialBins) throws Exception {
            for (SpatialBin spatialBin : spatialBins) {
                context.write(new LongWritable(spatialBin.getIndex()), spatialBin);
                numObsTotal += spatialBin.getNumObs();
                numBinsTotal++;
            }
        }
    }

}

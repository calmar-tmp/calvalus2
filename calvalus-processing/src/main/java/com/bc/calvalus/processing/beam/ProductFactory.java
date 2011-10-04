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


import com.bc.calvalus.commons.CalvalusLogger;
import com.bc.calvalus.processing.JobConfigNames;
import com.bc.calvalus.processing.hadoop.FSImageInputStream;
import com.bc.calvalus.processing.xml.XmlBinding;
import com.bc.ceres.binding.BindingException;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.TiePointGrid;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.gpf.operators.standard.SubsetOp;
import org.esa.beam.util.SystemUtils;

import javax.imageio.stream.ImageInputStream;
import javax.media.jai.JAI;
import java.awt.*;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Logger;

/**
 * A factory for products.
 *
 * @author MarcoZ
 * @author Norman
 */
public class ProductFactory {
    private static final int M = 1024 * 1024;
    public static final int DEFAULT_TILE_CACHE_SIZE = 512 * M; // 512 M

    private static final Logger LOG = CalvalusLogger.getLogger();


    private final Configuration configuration;

    /**
     * Constructor.
     *
     * @param configuration The Hadoop job configuration
     */
    public ProductFactory(Configuration configuration) {
        this.configuration = configuration;
        initGpf(configuration, this.getClass().getClassLoader());
    }

    public static void initGpf(Configuration configuration, ClassLoader classLoader) {
        initSystemProperties(configuration);
        SystemUtils.init3rdPartyLibs(classLoader);
        JAI.enableDefaultTileCache();
        JAI.getDefaultInstance().getTileCache().setMemoryCapacity(configuration.getLong(JobConfigNames.CALVALUS_BEAM_TILE_CACHE_SIZE, DEFAULT_TILE_CACHE_SIZE));
        GPF.getDefaultInstance().getOperatorSpiRegistry().loadOperatorSpis();
    }

    private static void initSystemProperties(Configuration configuration) {
        for (Map.Entry<String, String> entry : configuration) {
            String key = entry.getKey();
            if (key.startsWith("calvalus.system.")) {
                String propertyName = key.substring("calvalus.system.".length());
                String propertyValue = entry.getValue();
                LOG.info(String.format("Setting system property: %s=%s", propertyName, propertyValue));
                System.setProperty(propertyName, propertyValue);
            }
        }
    }

    // todo - nf/nf 19.04.2011: generalise following L2 processor call, so that we can also call 'l2gen'

    /**
     * Reads a source product and generates a target product using the given parameters.
     * {@code processorName} may be the name of a Unix executable, a BEAM GPF operator or GPF XML processing graph.
     * Currently only GPG operator names are supported.
     *
     * @param inputPath           The input path
     * @param inputFormat         The input format, may be {@code null}. If {@code null}, the file format will be detected.
     * @param regionGeometry      The geometry of the region of interest. May be {@code null} or empty.
     * @param allowSpatialSubset  If true, and the geometry intersects the product's boundary, a subset of the source product will be created.
     * @param processorName       The name of a processor. May be {@code null} or empty.
     * @param processorParameters The text-encoded parameters for the processor.   @return The target product.
     * @return The product corresponding to the given input path, region geometry and processor.
     * @throws java.io.IOException If an I/O error occurs
     */
    public Product getProduct(Path inputPath,
                              String inputFormat,
                              Geometry regionGeometry,
                              boolean allowSpatialSubset,
                              String processorName,
                              String processorParameters) throws IOException {

        Product sourceProduct = readProduct(inputPath, inputFormat);
        if (productHasEmptyTiepoints(sourceProduct)) {
            sourceProduct.dispose();
            return null;
        }
        Product targetProduct;
        try {
            targetProduct = getProcessedProduct(sourceProduct, regionGeometry, allowSpatialSubset, processorName, processorParameters);
            if (targetProduct == null) {
                sourceProduct.dispose();
            }
        } catch (RuntimeException t) {
            sourceProduct.dispose();
            throw t;
        }
        return targetProduct;
    }

    public static boolean productHasEmptyTiepoints(Product sourceProduct) {
        // "AMORGOS" can produce products that are corrupted.
        // Until they are removed from the cluster, perform this fast check.
        // All tie point grids contain only zeros, check the first one,
        // if the product has one.
        TiePointGrid[] tiePointGrids = sourceProduct.getTiePointGrids();
        if (tiePointGrids != null && tiePointGrids.length > 0) {
            TiePointGrid firstGrid = tiePointGrids[0];
            float[] tiePoints = firstGrid.getTiePoints();
            for (float tiePoint : tiePoints) {
                if (tiePoint != 0.0f) {
                    return false;
                }
            }
            // all values are zero
            return true;
        }
        return false;
    }

    /**
     * Reads a product from the distributed file system.
     *
     * @param inputPath     The input path
     * @param inputFormat   The input format, may be {@code null}. If {@code null}, the file format will be detected.
     * @return The product The product read.
     * @throws java.io.IOException If an I/O error occurs
     */
    public Product readProduct(Path inputPath, String inputFormat) throws IOException {
        final FileSystem fs = inputPath.getFileSystem(configuration);
        final Product product;
        if ("HADOOP-STREAMING".equals(inputFormat) || inputPath.getName().toLowerCase().endsWith(".seq")) {
            StreamingProductReader reader = new StreamingProductReader(inputPath, configuration);
            product = reader.readProductNodes(null, null);
        } else {
            final FileStatus status = fs.getFileStatus(inputPath);
            final FSDataInputStream in = fs.open(inputPath);
            final ImageInputStream imageInputStream = new FSImageInputStream(in, status.getLen());
            ProductReader productReader = ProductIO.getProductReader(inputFormat != null ? inputFormat : "ENVISAT");
            if (productReader != null) {
                product = productReader.readProductNodes(imageInputStream, null);
            } else {
                product = null;
            }
        }
        if (product == null) {
            throw new IOException(MessageFormat.format("No reader found for product '{0}' using input format '{1}'", inputPath, inputFormat));
        }
        return product;
    }

    public static Map<String, Object> getOperatorParameterMap(String operatorName, String level2Parameters) throws BindingException {
        if (level2Parameters == null) {
            return Collections.emptyMap();
        }
        Class<? extends Operator> operatorClass = getOperatorClass(operatorName);
        return new XmlBinding().convertXmlToMap(level2Parameters, operatorClass);
    }

    private static Class<? extends Operator> getOperatorClass(String operatorName) {
        OperatorSpi operatorSpi = GPF.getDefaultInstance().getOperatorSpiRegistry().getOperatorSpi(operatorName);
        if (operatorSpi == null) {
            throw new IllegalStateException(String.format("Unknown operator '%s'", operatorName));
        }
        return operatorSpi.getOperatorClass();
    }

    private static Product getSubsetProduct(Product product, Geometry regionGeometry, boolean allowSpatialSubset) {
        if (regionGeometry == null || regionGeometry.isEmpty() || isGlobalCoverageGeometry(regionGeometry)) {
            return product;
        }
        final Rectangle pixelRegion;
        try {
            pixelRegion = SubsetOp.computePixelRegion(product, regionGeometry, 1);
        } catch (Exception e) {
            // Computation of pixel region could fail (JTS Exception), if the geo-coding of the product is messed up
            // in this case ignore this product
            return null;
        }
        //  SubsetOp throws an OperatorException if pixelRegion.isEmpty(), we don't want this
        if (pixelRegion.isEmpty()) {
            return null;
        }
        if (!allowSpatialSubset) {
            return product;
        }

        final SubsetOp op = new SubsetOp();
        op.setSourceProduct(product);
        op.setRegion(pixelRegion);
        op.setCopyMetadata(false);
        return op.getTargetProduct();
    }

    public static boolean isGlobalCoverageGeometry(Geometry geometry) {
        Envelope envelopeInternal = geometry.getEnvelopeInternal();
        return eq(envelopeInternal.getMinX(), -180.0, 1E-8)
                && eq(envelopeInternal.getMaxX(), 180.0, 1E-8)
                && eq(envelopeInternal.getMinY(), -90.0, 1E-8)
                && eq(envelopeInternal.getMaxY(), 90.0, 1E-8);
    }

    private static boolean eq(double x1, double x2, double eps) {
        double delta = x1 - x2;
        return delta > 0 ? delta < eps : -delta < eps;
    }


    private static Product getProcessedProduct(Product source, String operatorName, String operatorParameters) {
        Product product = source;
        if (operatorName != null && !operatorName.isEmpty()) {
            // transform request into parameter objects
            Map<String, Object> parameterMap;
            try {
                parameterMap = getOperatorParameterMap(operatorName, operatorParameters);
            } catch (BindingException e) {
                throw new IllegalArgumentException("Invalid operator parameters: " + e.getMessage(), e);
            }
            product = GPF.createProduct(operatorName, parameterMap, product);
        }
        return product;
    }

    static Product getProcessedProduct(Product sourceProduct,
                                       Geometry regionGeometry,
                                       boolean allowSpatialSubset,
                                       String processorName,
                                       String processorParameters) {
        Product subsetProduct = getSubsetProduct(sourceProduct, regionGeometry, allowSpatialSubset);
        if (subsetProduct == null) {
            return null;
        }
        Product targetProduct = getProcessedProduct(subsetProduct, processorName, processorParameters);
        if (targetProduct != null && targetProduct != subsetProduct) {
            if (targetProduct.getStartTime() == null) {
                targetProduct.setStartTime(subsetProduct.getStartTime());
            }
            if (targetProduct.getEndTime() == null) {
                targetProduct.setEndTime(subsetProduct.getEndTime());
            }
        }
        return targetProduct;
    }


}

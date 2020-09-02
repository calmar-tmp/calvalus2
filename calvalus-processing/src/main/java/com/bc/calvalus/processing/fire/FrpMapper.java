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

package com.bc.calvalus.processing.fire;

import com.bc.calvalus.commons.CalvalusLogger;
import com.bc.calvalus.processing.JobConfigNames;
import com.bc.calvalus.processing.ProcessorAdapter;
import com.bc.calvalus.processing.ProcessorFactory;
import com.bc.calvalus.processing.beam.CalvalusProductIO;
import com.bc.calvalus.processing.hadoop.MetadataSerializer;
import com.bc.calvalus.processing.hadoop.ProgressSplitProgressMonitor;
import com.bc.calvalus.processing.l3.HadoopBinManager;
import com.bc.calvalus.processing.l3.L3SpatialBin;
import com.bc.calvalus.processing.utils.GeometryUtils;
import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.core.SubProgressMonitor;
import com.vividsolutions.jts.geom.Geometry;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;
import org.esa.snap.binning.BinningContext;
import org.esa.snap.binning.DataPeriod;
import org.esa.snap.binning.SpatialBin;
import org.esa.snap.binning.SpatialBinConsumer;
import org.esa.snap.binning.SpatialBinner;
import org.esa.snap.binning.operator.BinningConfig;
import org.esa.snap.binning.operator.SpatialProductBinner;
import org.esa.snap.binning.support.ObservationImpl;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.MetadataElement;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.dataio.netcdf.util.NetcdfFileOpener;
import ucar.ma2.Array;
import ucar.ma2.Index;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFile;
import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads an FRP product and produces (binIndex, spatialBin) pairs.
 *
 * @author boe
 */
public class FrpMapper extends Mapper<NullWritable, NullWritable, LongWritable, L3SpatialBin> {

    private static final Logger LOG = CalvalusLogger.getLogger();
    private static final String COUNTER_GROUP_NAME_PRODUCTS = "Products";

    enum FRP_VARIABLES {
        time,
        latitude,
        longitude,
        j,
        i,
        FRP_MWIR,
        FRP_SWIR,
        IFOV_area,
        flags,
        used_channel,
        confidence
    };

    enum GEODETIC_VARIABLES {
        latitude_in,
        longitude_in
    };

    private static final SimpleDateFormat ISO_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    private static final SimpleDateFormat COMPACT_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd'T'HHmmss");
    private static long THIRTY_YEARS;
    static {
        ISO_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
        COMPACT_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            THIRTY_YEARS = ISO_DATE_FORMAT.parse("2000-01-01T00:00:00.000Z").getTime();
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run(Context context) throws IOException, InterruptedException {

        Configuration conf = context.getConfiguration();
        String targetFormat = conf.get("calvalus.targetFormat", "l2monthly");  // one of l2monthly, l3daily, l3cycle, l3monthly

        final Path inputPath = ((FileSplit) context.getInputSplit()).getPath();
        final File[] inputFiles = CalvalusProductIO.uncompressArchiveToCWD(inputPath, conf);
        int platformNumber = inputPath.getName().startsWith("S3A") ? 1 : inputPath.getName().startsWith("S3B") ? 2 : 0;

        final File frpFile = findByName("FRP_in.nc", inputFiles);
        final NetcdfFile frpNetcdf = openNetcdfFile(frpFile);
        final Array[] frpArrays = new Array[FRP_VARIABLES.values().length];
        for (FRP_VARIABLES v : FRP_VARIABLES.values()) {
            frpArrays[v.ordinal()] = frpNetcdf.findVariable(v.name()).read();
        }
        Index index = frpArrays[FRP_VARIABLES.flags.ordinal()].getIndex();
        int numFires = frpNetcdf.findDimension("fires").getLength();

        int count = 0;

        if ("l2monthly".equals(targetFormat)) {
            System.out.print("Time\tLatitude\tLongitude\tRow\tColumn\tFRP_MIR\tFRP_SWIR\tAREA\tday_flag\tf1_flag\tPlatform\tConfidence\n");
            for (int i=0; i<numFires; ++i) {
                // filter
                long time = frpArrays[FRP_VARIABLES.time.ordinal()].getLong(i);
                double latitude = frpArrays[FRP_VARIABLES.latitude.ordinal()].getDouble(i);
                double longitude = frpArrays[FRP_VARIABLES.longitude.ordinal()].getDouble(i);
                int row = frpArrays[FRP_VARIABLES.j.ordinal()].getInt(i);
                int col = frpArrays[FRP_VARIABLES.i.ordinal()].getShort(i);
                double frpMwir = frpArrays[FRP_VARIABLES.FRP_MWIR.ordinal()].getDouble(i);
                double frpSwir = frpArrays[FRP_VARIABLES.FRP_SWIR.ordinal()].getDouble(i);
                double area = frpArrays[FRP_VARIABLES.IFOV_area.ordinal()].getDouble(i);
                int flags = frpArrays[FRP_VARIABLES.flags.ordinal()].getInt(index.set(row, col));
                int used_channel = frpArrays[FRP_VARIABLES.used_channel.ordinal()].getInt(i);
                double confidence = frpArrays[FRP_VARIABLES.confidence.ordinal()].getDouble(i);
                if (area <= 0.0) {
                    LOG.info("skipping empty area record at time " + time);
                    continue;
                }
                if (frpMwir <= 0.0) {
                    LOG.info("skipping non-MWIR record at time " + time);
                    continue;
                }
/*
                System.out.print(ISO_DATE_FORMAT.format(new Date(time / 1000 + THIRTY_YEARS)));
                System.out.print('\t');
                System.out.print(String.format("%8.5f", latitude));
                System.out.print('\t');
                System.out.print(String.format("%8.5f", longitude));
                System.out.print('\t');
                System.out.print(String.format("%d", row));
                System.out.print('\t');
                System.out.print(String.format("%d", col));
                System.out.print('\t');
                System.out.print(String.format("%f", frpMwir));
                System.out.print('\t');
                System.out.print(String.format("%f", frpSwir));
                System.out.print('\t');
                System.out.print(String.format("%f", area));
                System.out.print('\t');
                System.out.print(String.format("%d", flags));
                System.out.print('\t');
                System.out.print(String.format("%d", used_channel));
                System.out.print('\t');
                System.out.print(String.format("%s", platformNumber == 1 ? "S3A" : platformNumber == 2 ? "S3B" : "unknown"));
                System.out.print('\t');
                System.out.print(String.format("%f", confidence));
                System.out.print('\n');
*/
                ++count;
                //
                // create and write one bin with a record of FRP values
                L3SpatialBin bin = new L3SpatialBin(time, FRP_VARIABLES.values().length, 0);
                bin.getFeatureValues()[0] = (float) platformNumber;
                bin.getFeatureValues()[FRP_VARIABLES.latitude.ordinal()] = (float) latitude;
                bin.getFeatureValues()[FRP_VARIABLES.longitude.ordinal()] = (float) longitude;
                bin.getFeatureValues()[FRP_VARIABLES.j.ordinal()] = (float) row;
                bin.getFeatureValues()[FRP_VARIABLES.i.ordinal()] = (float) col;
                bin.getFeatureValues()[FRP_VARIABLES.FRP_MWIR.ordinal()] = (float) frpMwir;
                bin.getFeatureValues()[FRP_VARIABLES.FRP_SWIR.ordinal()] = (float) frpSwir;
                bin.getFeatureValues()[FRP_VARIABLES.IFOV_area.ordinal()] = (float) area;
                bin.getFeatureValues()[FRP_VARIABLES.flags.ordinal()] = (float) ((flags & 64) != 0 ? 1 : 0);
                bin.getFeatureValues()[FRP_VARIABLES.used_channel.ordinal()] = (float) used_channel;
                bin.getFeatureValues()[FRP_VARIABLES.confidence.ordinal()] = (float) confidence;
                context.write(new LongWritable(time), bin);
            }
            LOG.info(count + "/" + numFires + " records streamed of " + inputPath.getName());
            return;
        }

        final File geodeticFile = findByName("geodetic_in.nc", inputFiles);
        final NetcdfFile geodeticNetcdf = openNetcdfFile(geodeticFile);
        final Array[] geodeticArrays = new Array[GEODETIC_VARIABLES.values().length];
        for (GEODETIC_VARIABLES v : GEODETIC_VARIABLES.values()) {
            geodeticArrays[v.ordinal()] = geodeticNetcdf.findVariable(v.name()).read();
        }
        final int columns = geodeticNetcdf.findDimension("columns").getLength();
        final int rows = geodeticNetcdf.findDimension("rows").getLength();

        final Geometry regionGeometry = GeometryUtils.createGeometry(conf.get(JobConfigNames.CALVALUS_REGION_GEOMETRY));
        final BinningConfig binningConfig = HadoopBinManager.getBinningConfig(conf);
        final DataPeriod dataPeriod = HadoopBinManager.createDataPeriod(conf, binningConfig.getMinDataHour());
        final BinningContext binningContext = HadoopBinManager.createBinningContext(binningConfig, dataPeriod, regionGeometry);
        final SpatialBinEmitter spatialBinEmitter = new SpatialBinEmitter(context);
        final SpatialBinner spatialBinner = new SpatialBinner(binningContext, spatialBinEmitter);

        if ("l3daily".equals(targetFormat)) {
            // determine mjd from file name
            final double mjd;
            try {
                mjd = ProductData.UTC.parse(inputPath.getName().substring(16, 31), COMPACT_DATE_FORMAT).getMJD();
            } catch (ParseException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
            // create lut of fires by row and column
            HashMap<Integer, Integer> fireIndex = new HashMap<>();
            for (int i=0; i<numFires; ++i) {
                int row = frpArrays[FRP_VARIABLES.j.ordinal()].getInt(i);
                int col = frpArrays[FRP_VARIABLES.i.ordinal()].getShort(i);
                fireIndex.put(row * columns + col, i);
            }
            // create observation variable sequence
            int[] variableIndex = new int[] {
                    binningContext.getVariableContext().getVariableIndex("s3a_day_pixel"),
                    binningContext.getVariableContext().getVariableIndex("s3a_day_cloud"),
                    binningContext.getVariableContext().getVariableIndex("s3a_day_water"),
                    binningContext.getVariableContext().getVariableIndex("s3a_day_fire"),
                    binningContext.getVariableContext().getVariableIndex("s3a_day_frp")
            };
            // pixel loop
            for (int row=0; row<rows; ++row) {
                ObservationImpl[] observations = new ObservationImpl[columns];
                for (int col=0; col<columns; ++col) {
                    // construct observation
                    double lat = geodeticArrays[GEODETIC_VARIABLES.latitude_in.ordinal()].getInt(index.set(row, col)) * 1e-6;
                    double lon = geodeticArrays[GEODETIC_VARIABLES.longitude_in.ordinal()].getInt(index.set(row, col)) * 1e-6;
                    int flags = frpArrays[FRP_VARIABLES.flags.ordinal()].getInt(index.set(row, col));
                    Integer fire = fireIndex.get(row * columns + col);
                    float frpMwir = Float.NaN;
                    if (fire != null) {
                        double area = frpArrays[FRP_VARIABLES.IFOV_area.ordinal()].getDouble(fire);
                        if (area > 0.0) {
                            frpMwir = (float) frpArrays[FRP_VARIABLES.FRP_MWIR.ordinal()].getDouble(fire);
                            if (frpMwir <= 0.0) {
                                frpMwir = Float.NaN;
                            } else {
                                ++count;
                            }
                        }
                    }
                    //long binIndex = binningContext.getPlanetaryGrid().getBinIndex(lat, lon);
                    // aggregate contributions based on flags
                    float[] values = new float[5];
                    values[variableIndex[0]] = 1;
                    values[variableIndex[1]] = (flags & 32) != 0 ? 1 : 0;  // frp_cloud
                    values[variableIndex[2]] = (flags & 6) != 0 ? 1 : 0;  // l1b_water | frp_water
                    values[variableIndex[3]] = ! Float.isNaN(frpMwir) ? 1 : 0;
                    values[variableIndex[4]] = frpMwir;
                    observations[col] = new ObservationImpl(lat, lon, mjd, values);
                }
                spatialBinner.processObservationSlice(observations);
            }
            spatialBinner.complete();
            LOG.info(count + "/" + numFires + " records and " +
                             spatialBinEmitter.numObsTotal + "/" + spatialBinEmitter.numBinsTotal +
                             " obs/bins streamed of " + inputPath.getName());
            final Exception[] exceptions = spatialBinner.getExceptions();
            for (Exception exception : exceptions) {
                String m = MessageFormat.format("Failed to process input slice of {0}", inputPath.getName());
                LOG.log(Level.SEVERE, m, exception);
            }
            return;
        }
    }

    private File findByName(String name, File[] files) {
        for (File f : files) {
            if (f.getName().equals(name)) {
                return f;
            }
        }
        throw new NoSuchElementException(name + " not found");
    }

    private NetcdfFile openNetcdfFile(File inputFile) throws IOException {
        final NetcdfFile netcdfFile = NetcdfFileOpener.open(inputFile.getPath());
        if (netcdfFile == null) {
            throw new IOException("Failed to open file " + inputFile.getPath());
        }
        return netcdfFile;
    }

    private static class SpatialBinEmitter implements SpatialBinConsumer {
        private Context context;
        int numObsTotal = 0;
        int numBinsTotal = 0;

        public SpatialBinEmitter(Context context) {
            this.context = context;
        }

        @Override
        public void consumeSpatialBins(BinningContext binningContext, List<SpatialBin> spatialBins) throws Exception {
            for (SpatialBin spatialBin : spatialBins) {
                context.write(new LongWritable(spatialBin.getIndex()), (L3SpatialBin) spatialBin);
                numObsTotal += spatialBin.getNumObs();
                numBinsTotal++;
            }
        }
    }
}

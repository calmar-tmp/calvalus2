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

package com.bc.calvalus.processing.hadoop;

import com.bc.calvalus.binning.SpatialBin;
import com.bc.calvalus.binning.TemporalBin;
import com.bc.calvalus.commons.WorkflowException;
import com.bc.calvalus.processing.beam.BeamOpProcessingType;
import com.bc.calvalus.processing.beam.BeamUtils;
import com.bc.calvalus.processing.beam.CalvalusClasspath;
import com.bc.calvalus.processing.beam.L3Config;
import com.bc.calvalus.processing.beam.L3Mapper;
import com.bc.calvalus.processing.beam.L3Partitioner;
import com.bc.calvalus.processing.beam.L3Reducer;
import com.bc.calvalus.processing.beam.ProcessingConfiguration;
import com.bc.calvalus.processing.shellexec.ExecutablesInputFormat;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.JobID;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat;
import org.esa.beam.util.StringUtils;

import java.io.IOException;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Norman
 * Date: 15.03.11
 * Time: 16:56
 * To change this template use File | Settings | File Templates.
 */
public class L3WorkflowItem extends HadoopWorkflowItem {

    private L3ProcessingRequest processingRequest;
    private final String productionId;
    private final String processorBundle;
    private final String processorName;
    private final String processorParameters;
    private final String[] inputFiles;
    private final String outputDir;
    private final L3Config l3Config;

    public L3WorkflowItem(HadoopProcessingService processingService, L3ProcessingRequest processingRequest) {
        super(processingService);
        this.processingRequest = processingRequest;
        Map<String, Object> parameters = processingRequest.getProcessingParameters();
         productionId = BeamOpProcessingType.getString(parameters, "productionId");
         processorBundleName = BeamOpProcessingType.getString(parameters, "processorBundleName");
         processorBundleVersion = BeamOpProcessingType.getString(parameters, "processorBundleVersion");
         processorBundle = processorBundleName + "-" + processorBundleVersion;
         processorName = BeamOpProcessingType.getString(parameters, "processorName");
         processorParameters = BeamOpProcessingType.getString(parameters, "processorParameters");
        inputFiles = (String[]) parameters.get("inputFiles");
         outputDir = BeamOpProcessingType.getString(parameters, "outputDir");
         l3Config = (L3Config) parameters.get("binningParameters");


    }

    public L3WorkflowItem(HadoopProcessingService processingService,
                          String productionId,
                          String processorBundle,
                          String processorName,
                          String processorParameters,
                          String[] inputFiles,
                          String outputDir,
                          L3Config l3Config) {
        super(processingService);
        this.productionId = productionId;
        this.processorBundle = processorBundle;
        this.processorName = processorName;
        this.processorParameters = processorParameters;
        this.inputFiles = inputFiles;
        this.outputDir = outputDir;
        this.l3Config = l3Config;
    }

    @Override
    public void submit() throws WorkflowException {
        try {

            Job job = createJob(productionId, processorBundle, processorName, processorParameters, inputFiles, outputDir, l3Config);

            JobID jobId = submitJob(job);
            setJobId(jobId);
        } catch (Exception e) {
            throw new WorkflowException("Failed to submit Hadoop job: " + e.getMessage(), e);
        }
    }

    private Job createJob(String productionId, String processorBundle, String processorName, String processorParameters,
                          String[] inputFiles, String outputDir, L3Config l3Config) throws IOException {
        Job job = new Job(getProcessingService().getJobClient().getConf(), productionId);

        Configuration configuration = job.getConfiguration();

        configuration.set("hadoop.job.ugi", "hadoop,hadoop");  // user hadoop owns the outputs
        configuration.set("mapred.map.tasks.speculative.execution", "false");
        configuration.set("mapred.reduce.tasks.speculative.execution", "false");
        configuration.set("mapred.child.java.opts", "-Xmx1024m");


        configuration.set(ProcessingConfiguration.CALVALUS_IDENTIFIER, productionId);
        configuration.set(ProcessingConfiguration.CALVALUS_BUNDLE, processorBundle);
        configuration.set(ProcessingConfiguration.CALVALUS_INPUT, StringUtils.join(inputFiles, ","));
        configuration.set(ProcessingConfiguration.CALVALUS_OUTPUT, outputDir);
        configuration.set(ProcessingConfiguration.CALVALUS_L2_OPERATOR, processorName);
        configuration.set(ProcessingConfiguration.CALVALUS_L2_PARAMETER, processorParameters);
        configuration.set(ProcessingConfiguration.CALVALUS_L3_PARAMETER, BeamUtils.saveAsXml(l3Config));

        // clear output directory
        final Path outputPath = new Path(outputDir);
        final FileSystem fileSystem = outputPath.getFileSystem(configuration);
        fileSystem.delete(outputPath, true);
        FileOutputFormat.setOutputPath(job, outputPath);

        job.setInputFormatClass(ExecutablesInputFormat.class);
        job.setNumReduceTasks(4);
        job.setMapperClass(L3Mapper.class);
        job.setMapOutputKeyClass(LongWritable.class);
        job.setMapOutputValueClass(SpatialBin.class);
        job.setPartitionerClass(L3Partitioner.class);
        job.setReducerClass(L3Reducer.class);
        job.setOutputKeyClass(LongWritable.class);
        job.setOutputValueClass(TemporalBin.class);
        job.setOutputFormatClass(SequenceFileOutputFormat.class);

        CalvalusClasspath.configure(processorBundle, configuration);
        return job;
    }
}

package com.bc.calvalus.wps.localprocess;

import com.bc.calvalus.commons.ProcessState;
import com.bc.calvalus.production.ProductionException;
import com.bc.calvalus.wps.exceptions.InvalidProcessorIdException;
import com.bc.calvalus.wps.exceptions.ProductMetadataException;
import com.bc.calvalus.wps.exceptions.SqlStoreException;
import com.bc.calvalus.wps.exceptions.WpsResultProductException;
import com.bc.calvalus.wps.utils.ExecuteRequestExtractor;
import com.bc.ceres.binding.BindingException;
import com.bc.ceres.core.ProgressMonitor;
import com.bc.wps.api.WpsRequestContext;
import com.bc.wps.api.WpsServerContext;
import com.bc.wps.api.exceptions.InvalidParameterValueException;
import com.bc.wps.api.exceptions.MissingParameterValueException;
import com.bc.wps.api.schema.Execute;
import com.bc.wps.utilities.PropertiesWrapper;
import com.bc.wps.utilities.WpsLogger;
import org.esa.snap.core.dataio.ProductIO;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.gpf.OperatorException;

import javax.xml.bind.JAXBException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author hans
 */
class LocalProduction {

    private static final String CATALINA_BASE = System.getProperty("catalina.base");
    private Logger logger = WpsLogger.getLogger();

    LocalProductionStatus orderProductionAsynchronous(Execute executeRequest, String systemUserName, String remoteUserName,
                                                      WpsRequestContext wpsRequestContext, LocalFacade localFacade) {
        ProcessBuilder processBuilder = ProcessBuilder.create();
        LocalProductionService productionService;
        try {
            productionService = GpfProductionService.getProductionServiceSingleton();
        } catch (SqlStoreException exception) {
            return getStatusWithoutUpdatingDb(processBuilder, exception);
        }
        try {
            productionService = GpfProductionService.getProductionServiceSingleton();
            processBuilder = getProcessBuilder(executeRequest, systemUserName, remoteUserName, wpsRequestContext.getServerContext());
            return doProductionAsynchronous(productionService, localFacade, processBuilder);
        } catch (InvalidParameterValueException | JAXBException | MissingParameterValueException | IOException exception) {
            LocalProductionStatus status = getFailedStatus("Processing failed : " + exception.getMessage(), processBuilder);
            logError("[" + processBuilder.getJobId() + "] Processing failed : ", exception);
            updateJobStatus(productionService, status, processBuilder);
            return status;
        } catch (SqlStoreException exception) {
            return getStatusWithoutUpdatingDb(processBuilder, exception);
        } finally {
            try {
                productionService.updateStatuses();
            } catch (SqlStoreException exception) {
                logger.log(Level.SEVERE, "[" + processBuilder.getJobId() + "] Unable to persist the job information to DB...", exception);
            }
        }
    }

    LocalProductionStatus orderProductionSynchronous(Execute executeRequest, String systemUserName, String remoteUserName, WpsRequestContext wpsRequestContext,
                                                     LocalFacade localFacade) {
        ProcessBuilder processBuilder = ProcessBuilder.create();
        LocalProductionService productionService;
        try {
            productionService = GpfProductionService.getProductionServiceSingleton();
        } catch (SqlStoreException exception) {
            return getStatusWithoutUpdatingDb(processBuilder, exception);
        }
        try {
            processBuilder = getProcessBuilder(executeRequest, systemUserName, remoteUserName, wpsRequestContext.getServerContext());
            doProductionSynchronous(processBuilder);
            LocalProductionStatus status = getSuccessfulStatus(processBuilder, localFacade.getProductResultUrls(processBuilder.getJobId()));
            LocalJob job = new LocalJob(processBuilder.getJobId(), processBuilder.getParameters(), status);
            productionService.addJob(job);
            logInfo("[" + processBuilder.getJobId() + "] Generating product metadata...");
            localFacade.generateProductMetadata(processBuilder.getJobId());
            return status;
        } catch (WpsResultProductException | OperatorException | ProductionException | InvalidParameterValueException |
                    JAXBException | MissingParameterValueException exception) {
            LocalProductionStatus status = getFailedStatus("Processing failed : " + exception.getMessage(), processBuilder);
            updateJobStatus(productionService, status, processBuilder);
            logError("[" + processBuilder.getJobId() + "] Processing failed : ", exception);
            return status;
        } catch (ProductMetadataException | IOException | URISyntaxException |
                    BindingException | InvalidProcessorIdException exception) {
            LocalProductionStatus status = getFailedStatus("Creating product metadata failed : " + exception.getMessage(), processBuilder);
            updateJobStatus(productionService, status, processBuilder);
            logError("[" + processBuilder.getJobId() + "] Creating product metadata failed...", exception);
            return status;
        } finally {
            try {
                productionService.updateStatuses();
            } catch (SqlStoreException exception) {
                logger.log(Level.SEVERE, "[" + processBuilder.getJobId() + "] Unable to persist the job information to DB...", exception);
            }
        }
    }

    private LocalProductionStatus getStatusWithoutUpdatingDb(ProcessBuilder processBuilder, Exception exception) {
        LocalProductionStatus status = getFailedStatus("Processing failed : " + exception.getMessage(), processBuilder);
        logError("[" + processBuilder.getJobId() + "] Processing failed : ", exception);
        return status;
    }

    private void logError(String msg, Exception exception) {
        logger.log(Level.SEVERE, msg, exception);
    }

    private void logInfo(String message) {
        logger.log(Level.INFO, message);
    }

    private ProcessBuilder getProcessBuilder(Execute executeRequest, String systemUserName, String remoteUserName, WpsServerContext serverContext)
                throws JAXBException, MissingParameterValueException, InvalidParameterValueException, IOException {
        ExecuteRequestExtractor requestExtractor = new ExecuteRequestExtractor(executeRequest);
        Map<String, String> inputParameters = requestExtractor.getInputParametersMapRaw();
        final Product sourceProduct = getSourceProduct(inputParameters);
        String jobId = GpfProductionService.createJobId(remoteUserName);
        String processId = executeRequest.getIdentifier().getValue();
        Path targetDirPath = getTargetDirectoryPath(jobId, systemUserName, remoteUserName);
        HashMap<String, Object> parameters = new HashMap<>();
        parameters.put("processId", processId);
        parameters.put("productionName", inputParameters.get("productionName"));
        parameters.put("geoRegion", inputParameters.get("regionWKT"));
        parameters.put("outputFormat", inputParameters.get("outputFormat"));
        parameters.put("productionType", inputParameters.get("productionType"));
        parameters.put("sourceProduct", inputParameters.get("inputDataSetName"));
        parameters.put("copyMetadata", inputParameters.get("copyMetadata"));
        parameters.put("targetDir", targetDirPath.toString());
        return ProcessBuilder.create()
                    .withJobId(jobId)
                    .withProcessId(processId)
                    .withParameters(parameters)
                    .withSourceProduct(sourceProduct)
                    .withTargetDirPath(targetDirPath)
                    .withServerContext(serverContext)
                    .withExecuteRequest(executeRequest);
    }

    private void doProductionSynchronous(ProcessBuilder processBuilder)
                throws InvalidProcessorIdException, BindingException, IOException,
                       URISyntaxException, ProductionException, ProductMetadataException {
        logger.log(Level.INFO, "[" + processBuilder.getJobId() + "] starting synchronous process...");
        Product subset = GPF.createProduct("Subset", processBuilder.getParameters(), processBuilder.getSourceProduct());
        String outputFormat = (String) processBuilder.getParameters().get("outputFormat");
        GPF.writeProduct(subset,
                         new File(processBuilder.getTargetDirPath().toFile(), processBuilder.getSourceProduct().getName()),
                         outputFormat,
                         false,
                         ProgressMonitor.NULL);
        logger.log(Level.INFO, "[" + processBuilder.getJobId() + "] process finished...");
    }

    private LocalProductionStatus doProductionAsynchronous(LocalProductionService productionService, LocalFacade localFacade, ProcessBuilder processBuilder) {
        logger.log(Level.INFO, "[" + processBuilder.getJobId() + "] starting asynchronous process...");

        LocalProductionStatus status = new LocalProductionStatus(processBuilder.getJobId(),
                                                                 ProcessState.SCHEDULED,
                                                                 0,
                                                                 "The request has been queued.",
                                                                 null);
        LocalJob job = new LocalJob(processBuilder.getJobId(), processBuilder.getParameters(), status);
        productionService.addJob(job);
        GpfTask gpfTask = new GpfTask(localFacade, processBuilder);
        GpfProductionService.getWorker().submit(gpfTask);
        logger.log(Level.INFO, "[" + processBuilder.getJobId() + "] job has been queued...");
        return status;
    }

    private Product getSourceProduct(Map<String, String> inputParameters) throws IOException {
        final Product sourceProduct;
        Path dir = Paths.get(CATALINA_BASE + PropertiesWrapper.get("wps.application.path"), PropertiesWrapper.get("utep.input.directory"));
        List<File> files = new ArrayList<>();
        DirectoryStream<Path> stream = Files.newDirectoryStream(dir, inputParameters.get("inputDataSetName"));
        for (Path entry : stream) {
            files.add(entry.toFile());
        }

        String sourceProductPath;
        if (files.size() != 0) {
            sourceProductPath = files.get(0).getAbsolutePath();
        } else {
            throw new FileNotFoundException("The source product '" + inputParameters.get("sourceProduct") + "' cannot be found");
        }

        sourceProduct = ProductIO.readProduct(sourceProductPath);
        return sourceProduct;
    }

    private Path getTargetDirectoryPath(String jobId, String systemUserName, String remoteUserName) throws IOException {
        Path targetDirectoryPath;
        if (systemUserName.equalsIgnoreCase(remoteUserName)) {
            targetDirectoryPath = Paths.get(CATALINA_BASE + PropertiesWrapper.get("wps.application.path"),
                                            PropertiesWrapper.get("utep.output.directory"), systemUserName, jobId);
        } else {
            targetDirectoryPath = Paths.get(CATALINA_BASE + PropertiesWrapper.get("wps.application.path"),
                                            PropertiesWrapper.get("utep.output.directory"), systemUserName, remoteUserName, jobId);
        }
        Files.createDirectories(targetDirectoryPath);
        return targetDirectoryPath;
    }

    private void updateJobStatus(LocalProductionService productionService, LocalProductionStatus status, ProcessBuilder processBuilder) {
        LocalJob job = new LocalJob(processBuilder.getJobId(), processBuilder.getParameters(), status);
        productionService.updateJob(job);
    }

    private LocalProductionStatus getFailedStatus(String errorMessage, ProcessBuilder processBuilder) {
        LocalProductionStatus status;
        status = new LocalProductionStatus(processBuilder.getJobId(),
                                           ProcessState.ERROR,
                                           0,
                                           errorMessage,
                                           null);
        status.setStopDate(new Date());
        return status;
    }

    private LocalProductionStatus getSuccessfulStatus(ProcessBuilder processBuilder, List<String> resultUrls) {
        LocalProductionStatus status;
        status = new LocalProductionStatus(processBuilder.getJobId(),
                                           ProcessState.COMPLETED,
                                           100,
                                           "The request has been processed successfully.",
                                           resultUrls);
        status.setStopDate(new Date());
        return status;
    }
}
package com.bc.calvalus.production;


/**
 * Information about a production.
 *
 * @author Norman
 */
public class Production {
    private final String id;
    private final String name;
    private final String user;
    private final Object[] jobIds;
    private final boolean outputStaging;
    private final ProductionRequest productionRequest;
    private String outputUrl;
    private ProductionStatus processingStatus;
    private ProductionStatus stagingStatus;

    public Production(String id,
                      String name,
                      String user,
                      boolean outputStaging,
                      Object[] jobIds,
                      ProductionRequest productionRequest) {
        if (id == null) {
            throw new NullPointerException("id");
        }
        if (name == null) {
            throw new NullPointerException("name");
        }
        if (user == null) {
            throw new NullPointerException("user");
        }
        if (jobIds == null) {
            throw new NullPointerException("jobIds");
        }
        if (productionRequest == null) {
            throw new NullPointerException("productionRequest");
        }
        this.id = id;
        this.name = name;  // todo - check: remove param, instead derive from  productionRequest?
        this.user = user; // todo - check: remove param, instead derive from  productionRequest?
        this.outputStaging = outputStaging; // todo - check: remove param, instead derive from  productionRequest?
        this.jobIds = jobIds.clone();
        this.productionRequest = productionRequest;
        this.processingStatus = ProductionStatus.UNKNOWN;
        this.stagingStatus = outputStaging ? ProductionStatus.WAITING : ProductionStatus.UNKNOWN;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getUser() {
        return user;
    }

    public Object[] getJobIds() {
        return jobIds.clone();
    }

    public boolean isOutputStaging() {
        return outputStaging;
    }

    public ProductionRequest getProductionRequest() {
        return productionRequest;
    }

    public String getOutputUrl() {
        return outputUrl;
    }

    public void setOutputUrl(String outputUrl) {
        this.outputUrl = outputUrl;
    }

    public ProductionStatus getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(ProductionStatus processingStatus) {
        this.processingStatus = processingStatus;
    }

    public ProductionStatus getStagingStatus() {
        return stagingStatus;
    }

    public void setStagingStatus(ProductionStatus stagingStatus) {
        this.stagingStatus = stagingStatus;
    }

    @Override
    public String toString() {
        return "Production{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", outputUrl=" + outputUrl +
                ", productionStatus=" + processingStatus +
                ", stagingStatus=" + stagingStatus +
                '}';
    }

}

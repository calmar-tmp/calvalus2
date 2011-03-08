package com.bc.calvalus.production;

import org.apache.hadoop.mapreduce.JobID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ProductionTest {

    @Test
    public void testConstructor() throws Exception {
        Production production;
        JobID jobID = new JobID("34627598547", 6);

        production = new Production("9A3F", "Toasting", "ewa", false, new JobID[]{jobID}, new ProductionRequest("test"));
        assertEquals("9A3F", production.getId());
        assertEquals("Toasting", production.getName());
        assertEquals("ewa", production.getUser());
        assertEquals(false, production.isOutputStaging());
        assertEquals(1, production.getJobIds().length);
        assertEquals(jobID, production.getJobIds()[0]);
        assertEquals("test", production.getProductionRequest().getProductionType());
        assertEquals(ProcessState.UNKNOWN, production.getProcessingStatus().getState());
        assertEquals(ProcessState.UNKNOWN, production.getStagingStatus().getState());

        production = new Production("9A3F", "Toasting", "user", true, new JobID[]{jobID}, new ProductionRequest("test"));
        assertEquals(ProcessState.UNKNOWN, production.getProcessingStatus().getState());
        assertEquals(ProcessState.WAITING, production.getStagingStatus().getState());
    }

    @Test
    public void testJobIdsArrayDoesNotEscape() throws Exception {
        Production production;
        JobID jobID = new JobID("34627985F47", 4);

        production = new Production("9A3F", "Toasting", "ewa", false, new JobID[]{jobID}, new ProductionRequest("test"));

        assertEquals(jobID, production.getJobIds()[0]);

        Object[] jobIds = production.getJobIds();
        jobIds[0] = new JobID("745928345", 5324);

        assertEquals(jobID, production.getJobIds()[0]);
    }
}
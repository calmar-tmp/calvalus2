package com.bc.calvalus.b3;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class SpatialBinTest {
    @Test
    public void testIllegalConstructorCalls() {
        try {
            new SpatialBin(0, -1);
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException e) {

        }
    }

    @Test
    public void testLegalConstructorCalls() {
        SpatialBin bin = new SpatialBin(42, 0);
        assertEquals(42, bin.getIndex());
        bin = new SpatialBin(43, 3);
        assertEquals(43, bin.getIndex());
    }

    @Test
    public void testBinAggregationAndIO() throws IOException {
        MyVariableContext variableContext = new MyVariableContext("A", "B", "C");
        BinManagerImpl bman = new BinManagerImpl(new AggregatorMinMax(variableContext, "A"),
                                                         new AggregatorAverage(variableContext, "B"),
                                                         new AggregatorAverageML(variableContext, "C"));

        SpatialBin bin = bman.createSpatialBin(0);

        bman.aggregateSpatialBin(new ObservationImpl(0.0, 0.0, new float[]{0.2f, 4.0f, 4.0f}), bin);
        bman.aggregateSpatialBin(new ObservationImpl(0.0, 0.0, new float[]{0.6f, 2.0f, 2.0f}), bin);
        bman.aggregateSpatialBin(new ObservationImpl(0.0, 0.0, new float[]{0.4f, 6.0f, 6.0f}), bin);

        assertEquals(3, bin.getNumObs());

        Vector agg1 = bman.getSpatialVector(bin, 0);
        Vector agg2 = bman.getSpatialVector(bin, 1);
        Vector agg3 = bman.getSpatialVector(bin, 2);

        assertEquals(2, agg1.size());
        assertEquals(0.2f, agg1.get(0), 1e-5f);
        assertEquals(0.6f, agg1.get(1), 1e-5f);

        assertEquals(2, agg2.size());
        assertEquals(12.0f, agg2.get(0), 1e-5f);
        assertEquals(56.0f, agg2.get(1), 1e-5f);

        assertEquals(2, agg3.size());
        assertEquals(3.871201f, agg3.get(0), 1e-5f);
        assertEquals(5.612667f, agg3.get(1), 1e-5f);

        bman.completeSpatialBin(bin);

        assertEquals(0.2f, agg1.get(0), 1e-5f);
        assertEquals(0.6f, agg1.get(1), 1e-5f);

        assertEquals(6.9282035f, agg2.get(0), 1e-5f);
        assertEquals(32.3316154f, agg2.get(1), 1e-5f);

        assertEquals(2.235039f, agg3.get(0), 1e-5f);
        assertEquals(3.240475f, agg3.get(1), 1e-5f);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bin.write(new DataOutputStream(baos));
        byte[] bytes = baos.toByteArray();

        SpatialBin binCopy = SpatialBin.read(new DataInputStream(new ByteArrayInputStream(bytes)));

        assertEquals(-1, binCopy.getIndex());
        assertEquals(3, binCopy.getNumObs());

        Vector agg1Copy = bman.getSpatialVector(binCopy, 0);
        Vector agg2Copy = bman.getSpatialVector(binCopy, 1);
        Vector agg3Copy = bman.getSpatialVector(binCopy, 2);

        assertEquals(2, agg1Copy.size());
        assertEquals(0.2f, agg1Copy.get(0), 1e-5f);
        assertEquals(0.6f, agg1Copy.get(1), 1e-5f);

        assertEquals(2, agg2Copy.size());
        assertEquals(6.9282035f, agg2Copy.get(0), 1e-5f);
        assertEquals(32.3316154f, agg2Copy.get(1), 1e-5f);

        assertEquals(2, agg3Copy.size());
        assertEquals(2.235039f, agg3Copy.get(0), 1e-5f);
        assertEquals(3.240475f, agg3Copy.get(1), 1e-5f);
    }
}

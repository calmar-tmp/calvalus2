package com.bc.calvalus.processing.fire.format.grid;

import org.apache.hadoop.io.Writable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.bc.calvalus.processing.fire.format.grid.GridFormatUtils.LC_CLASSES_COUNT;
import static com.bc.calvalus.processing.fire.format.grid.GridFormatUtils.TARGET_RASTER_HEIGHT;
import static com.bc.calvalus.processing.fire.format.grid.GridFormatUtils.TARGET_RASTER_WIDTH;

/**
 * @author thomas
 */
public class GridCell implements Writable {

    private static final int BAND_SIZE = TARGET_RASTER_WIDTH * TARGET_RASTER_HEIGHT;

    public float[] baFirstHalf;
    public float[] baSecondHalf;
    public float[] patchNumberFirstHalf;
    public float[] patchNumberSecondHalf;
    public float[] errorsFirstHalf;
    public float[] errorsSecondHalf;
    public List<float[]> baInLcFirstHalf;
    public List<float[]> baInLcSecondHalf;
    public float[] coverageFirstHalf;
    public float[] coverageSecondHalf;

    public void setBaFirstHalf(float[] baFirstHalf) {
        this.baFirstHalf = baFirstHalf;
    }

    public void setBaSecondHalf(float[] baSecondHalf) {
        this.baSecondHalf = baSecondHalf;
    }

    public void setPatchNumberFirstHalf(float[] patchNumberFirstHalf) {
        this.patchNumberFirstHalf = patchNumberFirstHalf;
    }

    public void setPatchNumberSecondHalf(float[] patchNumberSecondHalf) {
        this.patchNumberSecondHalf = patchNumberSecondHalf;
    }

    public void setErrorsFirstHalf(float[] errorsFirstHalf) {
        this.errorsFirstHalf = errorsFirstHalf;
    }

    public void setErrorsSecondHalf(float[] errorsSecondHalf) {
        this.errorsSecondHalf = errorsSecondHalf;
    }

    public void setBaInLcFirstHalf(List<float[]> baInLcFirstHalf) {
        this.baInLcFirstHalf = baInLcFirstHalf;
    }

    public void setBaInLcSecondHalf(List<float[]> baInLcSecondHalf) {
        this.baInLcSecondHalf = baInLcSecondHalf;
    }

    public void setCoverageFirstHalf(float[] coverageFirstHalf) {
        this.coverageFirstHalf = coverageFirstHalf;
    }

    public void setCoverageSecondHalf(float[] coverageSecondHalf) {
        this.coverageSecondHalf = coverageSecondHalf;
    }

    @Override
    public void write(DataOutput out) throws IOException {
        for (float v : baFirstHalf) {
            out.writeFloat((int) v);
        }
        for (float v : baSecondHalf) {
            out.writeFloat((int) v);
        }
        for (float v : patchNumberFirstHalf) {
            out.writeFloat(v);
        }
        for (float v : patchNumberSecondHalf) {
            out.writeFloat(v);
        }
        for (float v : errorsFirstHalf) {
            out.writeFloat((int) v);
        }
        for (float v : errorsSecondHalf) {
            out.writeFloat((int) v);
        }
        for (float[] lcClass : baInLcFirstHalf) {
            for (float value : lcClass) {
                out.writeFloat((int) value);
            }
        }
        for (float[] lcClass : baInLcSecondHalf) {
            for (float value : lcClass) {
                out.writeFloat((int) value);
            }
        }
        for (float v : coverageFirstHalf) {
            out.writeFloat(v);
        }
        for (float v : coverageSecondHalf) {
            out.writeFloat(v);
        }
    }

    @Override
    public void readFields(DataInput in) throws IOException {
        baFirstHalf = new float[BAND_SIZE];
        baSecondHalf = new float[BAND_SIZE];
        patchNumberFirstHalf = new float[BAND_SIZE];
        patchNumberSecondHalf = new float[BAND_SIZE];
        errorsFirstHalf = new float[BAND_SIZE];
        errorsSecondHalf = new float[BAND_SIZE];
        baInLcFirstHalf = new ArrayList<>();
        baInLcSecondHalf = new ArrayList<>();
        for (int lcClass = 0; lcClass < LC_CLASSES_COUNT; lcClass++) {
            baInLcFirstHalf.add(new float[BAND_SIZE]);
            baInLcSecondHalf.add(new float[BAND_SIZE]);
        }
        coverageFirstHalf = new float[BAND_SIZE];
        coverageSecondHalf = new float[BAND_SIZE];

        for (int i = 0; i < BAND_SIZE; i++) {
            baFirstHalf[i] = in.readFloat();
        }
        for (int i = 0; i < BAND_SIZE; i++) {
            baSecondHalf[i] = in.readFloat();
        }
        for (int i = 0; i < BAND_SIZE; i++) {
            patchNumberFirstHalf[i] = in.readFloat();
        }
        for (int i = 0; i < BAND_SIZE; i++) {
            patchNumberSecondHalf[i] = in.readFloat();
        }
        for (int i = 0; i < BAND_SIZE; i++) {
            errorsFirstHalf[i] = in.readFloat();
        }
        for (int i = 0; i < BAND_SIZE; i++) {
            errorsSecondHalf[i] = in.readFloat();
        }
        for (float[] lcClass : baInLcFirstHalf) {
            for (int i = 0; i < lcClass.length; i++) {
                lcClass[i] = in.readFloat();
            }
        }
        for (float[] lcClass : baInLcSecondHalf) {
            for (int i = 0; i < lcClass.length; i++) {
                lcClass[i] = in.readFloat();
            }
        }
        for (int i = 0; i < BAND_SIZE; i++) {
            coverageFirstHalf[i] = in.readFloat();
        }
        for (int i = 0; i < BAND_SIZE; i++) {
            coverageSecondHalf[i] = in.readFloat();
        }
    }

    @Override
    public String toString() {
        return "GridCell{" +
                "baFirstHalf=" + Arrays.toString(baFirstHalf) +
                ", baSecondHalf=" + Arrays.toString(baSecondHalf) +
                ", patchNumberFirstHalf=" + Arrays.toString(patchNumberFirstHalf) +
                ", patchNumberSecondHalf=" + Arrays.toString(patchNumberSecondHalf) +
                ", errorsFirstHalf=" + Arrays.toString(errorsFirstHalf) +
                ", errorsSecondHalf=" + Arrays.toString(errorsSecondHalf) +
                ", baInLcFirstHalf=" + baInLcFirstHalf +
                ", baInLcSecondHalf=" + baInLcSecondHalf +
                ", coverageFirstHalf=" + Arrays.toString(coverageFirstHalf) +
                ", coverageSecondHalf=" + Arrays.toString(coverageSecondHalf) +
                '}';
    }
}

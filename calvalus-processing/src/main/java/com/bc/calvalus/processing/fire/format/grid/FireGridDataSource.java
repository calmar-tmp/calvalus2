package com.bc.calvalus.processing.fire.format.grid;

import java.io.IOException;

interface FireGridDataSource {

    void readPixels(SourceData data, int rasterWidth, int x, int y) throws IOException;

    void setDoyFirstOfMonth(int doyFirstOfMonth);

    void setDoyLastOfMonth(int doyLastOfMonth);

    void setDoyFirstHalf(int doyFirstHalf);

    void setDoySecondHalf(int doySecondHalf);
}

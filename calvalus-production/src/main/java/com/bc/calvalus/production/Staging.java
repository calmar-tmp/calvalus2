package com.bc.calvalus.production;

import java.util.concurrent.Callable;

public abstract class Staging implements Callable<Void> {
    @Override
    public abstract Void call() throws Exception;

    public abstract boolean isCancelled();

    public abstract void cancel();
}

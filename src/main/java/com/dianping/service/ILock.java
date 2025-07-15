package com.dianping.service;

public interface ILock {
    boolean tryLock(long timeoutSec);

    void unlock();
}

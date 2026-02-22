package com.moveinsync.mdm.repository;

public interface RegionVersionCountProjection {
    String getRegion();

    String getVersion();

    long getTotal();
}

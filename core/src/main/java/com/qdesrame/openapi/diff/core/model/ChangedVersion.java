package com.qdesrame.openapi.diff.core.model;

import lombok.Data;

@Data
public class ChangedVersion {
    
    private String oldVersion;
    private String newVersion;
    
    public ChangedVersion(String oldVersion, String newVersion) {
        this.oldVersion = oldVersion;
        this.newVersion = newVersion;
    }
    
    @Override
    public String toString() {
        return oldVersion + " --> " + newVersion;
    }
}
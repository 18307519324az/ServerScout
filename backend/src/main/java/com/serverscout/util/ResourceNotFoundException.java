package com.serverscout.util;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String resource, Object id) {
        super(resource + " 不存在: " + id);
    }
}

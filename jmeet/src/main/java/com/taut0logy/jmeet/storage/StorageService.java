package com.taut0logy.jmeet.storage;

import java.io.InputStream;

public interface StorageService {

    void put(String key, InputStream content, long size, String contentType);

    StorageContent get(String key);
}

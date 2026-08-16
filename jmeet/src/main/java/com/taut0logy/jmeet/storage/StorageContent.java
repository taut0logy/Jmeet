package com.taut0logy.jmeet.storage;

import java.io.InputStream;

public sealed interface StorageContent {

    record Redirect(String url) implements StorageContent {
    }

    record Stream(InputStream data, String contentType, long size) implements StorageContent {
    }
}

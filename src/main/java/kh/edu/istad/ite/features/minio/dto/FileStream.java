package kh.edu.istad.ite.features.minio.dto;


import java.io.InputStream;

public record FileStream(
        InputStream stream,
        String contentType,
        long size
) {

}

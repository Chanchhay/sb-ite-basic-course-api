package kh.edu.istad.ite.features.migration.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A workbook we generated, dressed as an upload.
 *
 * So that a prepared migration enters the importer through exactly the door a
 * shopkeeper's own file uses — same validation, same storage, same reading of
 * the headings. The alternative was a second way of creating an import job,
 * which is the sort of shortcut that works until the two drift apart.
 */
record PreparedMultipartFile(String name, byte[] content) implements MultipartFile {

    private static final String XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Override
    public String getName() {
        return "file";
    }

    @Override
    public String getOriginalFilename() {
        return name;
    }

    @Override
    public String getContentType() {
        return XLSX;
    }

    @Override
    public boolean isEmpty() {
        return content.length == 0;
    }

    @Override
    public long getSize() {
        return content.length;
    }

    @Override
    public byte[] getBytes() {
        return content;
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(content);
    }

    @Override
    public void transferTo(java.io.File destination) throws IOException {
        Files.write(Path.of(destination.getPath()), content);
    }
}

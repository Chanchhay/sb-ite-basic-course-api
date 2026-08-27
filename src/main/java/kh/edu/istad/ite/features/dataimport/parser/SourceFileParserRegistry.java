package kh.edu.istad.ite.features.dataimport.parser;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Picks the reader for an uploaded file.
 *
 * The only place in the feature that knows which formats exist. Supporting a
 * new one means adding a {@link SourceFileParser} bean and nothing else —
 * staging, checking, preview and commit never learn about it.
 */
@Component
@RequiredArgsConstructor
public class SourceFileParserRegistry {

    private final List<SourceFileParser> parsers;

    public SourceFileParser parserFor(String fileName) {
        return parsers.stream()
                .filter(parser -> parser.supports(fileName))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Only CSV and Excel (.xlsx) files can be imported."
                ));
    }
}

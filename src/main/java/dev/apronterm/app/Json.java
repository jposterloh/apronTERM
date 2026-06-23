package dev.apronterm.app;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * Shared Jackson mappers.
 *
 * <p>{@link #WT} is lenient so it can read Windows Terminal's {@code settings.json}, which is
 * really JSONC (it tolerates {@code //} comments and trailing commas). {@link #APP} is used for
 * ApronTerm's own, pretty-printed configuration files.
 */
public final class Json {

    /** Lenient mapper for Windows Terminal's settings.json (JSONC). */
    public static final ObjectMapper WT = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    /** Pretty mapper for ApronTerm's own config files. */
    public static final ObjectMapper APP = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    private Json() {
    }
}

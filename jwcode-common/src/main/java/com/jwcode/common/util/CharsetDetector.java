package com.jwcode.common.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * CharsetDetector — intelligent character encoding detection.
 *
 * <p>Primary use case: decoding shell command output and web page content
 * on Windows where UTF-8 and GBK (CP936) output may be mixed.</p>
 *
 * <p>Strategy: try strict UTF-8 first; if malformed, fall back to the
 * system default charset (usually GBK on Chinese Windows); as a last
 * resort, use ISO-8859-1 (which never fails but may produce garbled text).</p>
 *
 * <p>All methods are stateless and thread-safe.</p>
 */
public final class CharsetDetector {

    private static final String OS_NAME = System.getProperty("os.name").toLowerCase();
    private static final boolean IS_WINDOWS = OS_NAME.contains("win");

    private CharsetDetector() {}

    /**
     * Decode a byte array using auto-detection.
     *
     * @param rawBytes raw bytes to decode
     * @return decoded string (never null)
     */
    public static String decode(byte[] rawBytes) {
        if (rawBytes == null || rawBytes.length == 0) return "";

        // 1. Try strict UTF-8
        String result = tryDecode(rawBytes, StandardCharsets.UTF_8);
        if (result != null) return result;

        // 2. Try system charset (GBK on Chinese Windows)
        Charset systemCharset = Charset.defaultCharset();
        if (!StandardCharsets.UTF_8.equals(systemCharset)) {
            result = tryDecode(rawBytes, systemCharset);
            if (result != null) return result;
        }

        // 3. Try Windows-1252 / ISO-8859-1 (never fails)
        return new String(rawBytes, StandardCharsets.ISO_8859_1);
    }

    /**
     * Decode a byte array line-by-line, returning each decoded line.
     * Each line is independently decoded with the same fallback strategy.
     * This handles mixed-encoding streams (e.g. ASCII headers + GBK body).
     *
     * @param rawBytes raw bytes to decode
     * @return decoded string
     */
    public static String decodeByLine(byte[] rawBytes) {
        if (rawBytes == null || rawBytes.length == 0) return "";

        StringBuilder result = new StringBuilder(rawBytes.length);
        int start = 0;

        for (int i = 0; i < rawBytes.length; i++) {
            if (rawBytes[i] == '\n') {
                int len = i - start;
                if (rawBytes[start] == '\r') {
                    start++;
                    len--;
                }
                if (len > 0) {
                    byte[] lineBytes = new byte[len];
                    System.arraycopy(rawBytes, start, lineBytes, 0, len);
                    result.append(decode(lineBytes));
                }
                result.append('\n');
                start = i + 1;
            }
        }

        // remaining bytes after last newline
        if (start < rawBytes.length) {
            byte[] tail = new byte[rawBytes.length - start];
            System.arraycopy(rawBytes, start, tail, 0, tail.length);
            result.append(decode(tail));
        }

        return result.toString();
    }

    /**
     * Read an InputStream line-by-line with encoding auto-detection.
     *
     * @param in           the input stream
     * @param maxBytes     maximum bytes to read
     * @param maxLines     maximum lines to read
     * @return decoded content
     * @throws IOException on read error
     */
    public static String readStreamByLine(InputStream in, int maxBytes, int maxLines) throws IOException {
        byte[] buffer = new byte[8192];
        byte[] lineBuffer = new byte[maxBytes];
        int linePos = 0;
        int totalBytes = 0;
        int lineCount = 0;
        int read;
        StringBuilder output = new StringBuilder(maxBytes);

        while ((read = in.read(buffer)) != -1 && totalBytes < maxBytes && lineCount < maxLines) {
            for (int i = 0; i < read && totalBytes < maxBytes && lineCount < maxLines; i++) {
                byte b = buffer[i];
                totalBytes++;

                if (b == '\n') {
                    if (linePos > 0) {
                        byte[] lineBytes = new byte[linePos];
                        System.arraycopy(lineBuffer, 0, lineBytes, 0, linePos);
                        output.append(decode(lineBytes)).append('\n');
                        lineCount++;
                        linePos = 0;
                    } else {
                        output.append('\n');
                        lineCount++;
                    }
                } else if (b != '\r') {
                    if (linePos < lineBuffer.length) {
                        lineBuffer[linePos++] = b;
                    }
                }
            }
        }

        // trailing content without newline
        if (linePos > 0 && lineCount < maxLines) {
            byte[] lineBytes = new byte[linePos];
            System.arraycopy(lineBuffer, 0, lineBytes, 0, linePos);
            output.append(decode(lineBytes));
        }

        return output.toString();
    }

    /**
     * Detect charset from HTTP response headers.
     *
     * @param headers HTTP response headers (null-safe)
     * @return detected charset name, or "UTF-8" if not found
     */
    public static String detectFromHttpHeaders(Map<String, List<String>> headers) {
        if (headers == null) return "UTF-8";

        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase("Content-Type")) {
                for (String value : entry.getValue()) {
                    String charset = extractCharset(value);
                    if (charset != null) return charset;
                }
            }
        }
        return "UTF-8";
    }

    /**
     * Get a command that switches the Windows console to UTF-8 code page.
     *
     * @return "chcp 65001 >nul 2>&1" on Windows, empty string otherwise
     */
    public static String prepareWindowsConsole() {
        if (IS_WINDOWS) {
            return "chcp 65001 >nul 2>&1";
        }
        return "";
    }

    /**
     * Wrap a command with UTF-8 code page prefix on Windows.
     *
     * @param command the original command
     * @return wrapped command, unchanged on non-Windows
     */
    public static String wrapWithUtf8Console(String command) {
        if (IS_WINDOWS && command != null && !command.isEmpty()) {
            return "chcp 65001 >nul 2>&1 & " + command;
        }
        return command;
    }

    // ---- private helpers ----

    private static String tryDecode(byte[] bytes, Charset charset) {
        try {
            CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
            return decoder.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    /**
     * Extract charset from a Content-Type header value.
     * e.g. "text/html; charset=utf-8" -> "utf-8"
     */
    private static String extractCharset(String contentType) {
        if (contentType == null) return null;
        String lower = contentType.toLowerCase();
        int charsetIdx = lower.indexOf("charset=");
        if (charsetIdx < 0) return null;

        int start = charsetIdx + 8;
        if (start >= contentType.length()) return null;

        StringBuilder sb = new StringBuilder();
        for (int i = start; i < contentType.length(); i++) {
            char c = contentType.charAt(i);
            if (c == ' ' || c == ';' || c == '"') break;
            sb.append(c);
        }
        return sb.length() > 0 ? sb.toString().trim() : null;
    }
}


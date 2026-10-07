package org.example.j1.common;

import lombok.Value;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

// Свой формат обмена байтами, в задании он не задан:
//   запрос:  ASCII-имя + нулевой байт-терминатор
//   ответ:   1 байт статуса, дальше либо
//     OK:    int32 длина + PEM сертификата, int32 длина + PEM ключа
//     ERROR: int32 длина + текст ошибки
public final class Protocol {

    public static final byte STATUS_OK = 1;
    public static final byte STATUS_ERROR = 0;

    public static final int MAX_NAME_BYTES = 4096;

    private Protocol() {
    }

    public static byte[] encodeRequest(String name) {
        byte[] nameBytes = name.getBytes(StandardCharsets.US_ASCII);
        byte[] framed = Arrays.copyOf(nameBytes, nameBytes.length + 1);
        framed[nameBytes.length] = 0;
        return framed;
    }

    public static byte[] encodeSuccess(String certPem, String keyPem) {
        byte[] certBytes = certPem.getBytes(StandardCharsets.UTF_8);
        byte[] keyBytes = keyPem.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[1 + 4 + certBytes.length + 4 + keyBytes.length];
        int pos = 0;
        out[pos++] = STATUS_OK;
        pos = writeInt(out, pos, certBytes.length);
        pos = writeBytes(out, pos, certBytes);
        pos = writeInt(out, pos, keyBytes.length);
        writeBytes(out, pos, keyBytes);
        return out;
    }

    public static byte[] encodeError(String message) {
        byte[] msgBytes = message.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[1 + 4 + msgBytes.length];
        int pos = 0;
        out[pos++] = STATUS_ERROR;
        pos = writeInt(out, pos, msgBytes.length);
        writeBytes(out, pos, msgBytes);
        return out;
    }

    public static Response readResponse(InputStream rawIn) throws IOException {
        DataInputStream in = new DataInputStream(rawIn);
        byte status = in.readByte();
        if (status == STATUS_OK) {
            String cert = readLengthPrefixedUtf8(in);
            String key = readLengthPrefixedUtf8(in);
            return new Response(true, cert, key, null);
        } else if (status == STATUS_ERROR) {
            String message = readLengthPrefixedUtf8(in);
            return new Response(false, null, null, message);
        } else {
            throw new IOException("Unknown response status byte: " + status);
        }
    }

    private static String readLengthPrefixedUtf8(DataInputStream in) throws IOException {
        int len = in.readInt();
        if (len < 0 || len > 50 * 1024 * 1024) {
            throw new IOException("Implausible length-prefixed field: " + len + " bytes");
        }
        byte[] data = new byte[len];
        in.readFully(data);
        return new String(data, StandardCharsets.UTF_8);
    }

    private static int writeInt(byte[] out, int pos, int value) {
        out[pos] = (byte) (value >>> 24);
        out[pos + 1] = (byte) (value >>> 16);
        out[pos + 2] = (byte) (value >>> 8);
        out[pos + 3] = (byte) value;
        return pos + 4;
    }

    private static int writeBytes(byte[] out, int pos, byte[] src) {
        System.arraycopy(src, 0, out, pos, src.length);
        return pos + src.length;
    }

    @Value
    public static class Response {
        boolean ok;
        String certPem;
        String keyPem;
        String errorMessage;
    }
}

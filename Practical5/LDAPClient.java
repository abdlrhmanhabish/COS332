//abdelrahman ahmed u24898008 && hamdaan mirza 24631494

import java.util.List;
import java.util.ArrayList;
import java.util.Scanner;
import java.net.Socket;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class LDAPClient {

    // ldap default (no tls for this practical)
    private static final int port = 389;
    private static final String DEFAULT_HOST = "localhost";
    private static final String DEFAULT_BASE_DN = "dc=assets,dc=local";
    private static final String FIXED_OU = "Automobiles";
    private static final String SPEED_ATTRIBUTE = "description";

    // quick sample entries you can load into pla so testing is painless
    // we store speed as plain integer text in "description"
    private static final String[][] DATASET = new String[][] {
            { "ToyotaCorolla", "180" },
            { "VWGolfGTI", "250" },
            { "FordMustangGT", "250" },
            { "BMWM3", "290" },
            { "Porsche911Carrera", "293" }
    };

    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.println("LDAP Practical Client (minimal setup)");
            System.out.println("Design choices:");
            System.out.println("- Fixed OU: " + FIXED_OU);
            System.out.println("- Simple bind only (no anonymous bind)");
            System.out.println("- Speed attribute: " + SPEED_ATTRIBUTE + " (store integer text, e.g. 250)");
            System.out.println("- Search scope: one-level under ou=" + FIXED_OU + ",<baseDN>");
            printDatasetSuggestion();
            System.out.println();

            System.out.print("LDAP server host (default: " + DEFAULT_HOST + "): ");
            String host = scanner.nextLine().trim();
            if (host.isEmpty()) {
                host = DEFAULT_HOST;
            }

            System.out.print("Base DN (default: " + DEFAULT_BASE_DN + "): ");
            String baseDn = scanner.nextLine().trim();
            if (baseDn.isEmpty()) {
                baseDn = DEFAULT_BASE_DN;
            }

            System.out.print("Bind DN (required for simple bind): ");
            String bindDn = scanner.nextLine().trim();
            if (bindDn.isEmpty()) {
                System.out.println("Bind DN is required.");
                return;
            }

            System.out.print("Bind password: ");
            String bindPassword = scanner.nextLine();

            System.out.print("Asset common name (cn) in OU " + FIXED_OU + ": ");
            String assetName = scanner.nextLine().trim();
            if (assetName.isEmpty()) {
                System.out.println("Asset name is required.");
                return;
            }

            String searchBase = "ou=" + FIXED_OU + "," + baseDn;

            // main ldap flow: connect -> bind -> search -> unbind
            try (Socket socket = new Socket(host, port)) {
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();

                // 1) authenticate using simple bind
                int bindMessageId = 1;
                byte[] bindRequest = buildBindRequest(bindMessageId, bindDn, bindPassword);
                out.write(bindRequest);
                out.flush();

                byte[] bindResponse = readBerMessage(in);
                BindResult bindResult = parseBindResponse(bindResponse, bindMessageId);
                if (!bindResult.success) {
                    System.out.println(
                            "Bind failed. code=" + bindResult.resultCode + " message=" + bindResult.diagnosticMessage);
                    return;
                }

                // 2) search for exactly one asset by cn under ou=automobiles
                int searchMessageId = 2;
                byte[] searchRequest = buildSearchRequest(searchMessageId, searchBase, assetName, SPEED_ATTRIBUTE);
                out.write(searchRequest);
                out.flush();

                String speed = null;
                while (true) {
                    byte[] serverMessage = readBerMessage(in);
                    SearchStepResult step = parseSearchStep(serverMessage, searchMessageId, SPEED_ATTRIBUTE);

                    if (step.type == SearchStepType.ENTRY && step.attributeValue != null) {
                        speed = step.attributeValue;
                    }

                    if (step.type == SearchStepType.DONE) {
                        if (step.resultCode != 0) {
                            System.out.println(
                                    "Search failed. code=" + step.resultCode + " message=" + step.diagnosticMessage);
                        } else if (speed == null) {
                            System.out.println("Asset not found or speed attribute missing (" + SPEED_ATTRIBUTE + ").");
                        } else {
                            try {
                                // speed is stored as integer text, so parse before printing
                                int speedKmh = Integer.parseInt(speed.trim());
                                System.out.println("Maximum speed for " + assetName + ": " + speedKmh + " km/h");
                            } catch (NumberFormatException nfe) {
                                System.out.println("Found asset, but speed is not an integer: '" + speed + "'");
                            }
                        }
                        break;
                    }
                }

                // 3) cleanly tell server we're done
                int unbindMessageId = 3;
                byte[] unbindRequest = buildUnbindRequest(unbindMessageId);
                out.write(unbindRequest);
                out.flush();
            } catch (IOException e) {
                System.out.println("I/O error: " + e.getMessage());
            } catch (IllegalArgumentException e) {
                System.out.println("Protocol parse error: " + e.getMessage());
            }
        }
    }

    private static void printDatasetSuggestion() {
        System.out.println("Suggested PLA entries under ou=" + FIXED_OU + ":");
        for (String[] row : DATASET) {
            System.out.println("  cn=" + row[0] + " , " + SPEED_ATTRIBUTE + "=" + row[1]);
        }
    }

    // wraps ldap v3 simple bind into an ldapmessage
    private static byte[] buildBindRequest(int messageId, String bindDn, String password) {
        byte[] version = encodeInteger(3);
        byte[] name = encodeOctetString(bindDn);
        byte[] authentication = encodeContextPrimitive(0, password.getBytes(StandardCharsets.UTF_8));
        byte[] bindContent = concat(version, name, authentication);
        byte[] bindRequest = encodeApplicationConstructed(0, bindContent);
        return encodeSequence(concat(encodeInteger(messageId), bindRequest));
    }

    // unbind has an empty body; server usually closes right after this
    private static byte[] buildUnbindRequest(int messageId) {
        byte[] unbindRequest = encodeTagAndContent(0x42, new byte[0]);
        return encodeSequence(concat(encodeInteger(messageId), unbindRequest));
    }

    // minimal search request: one-level scope + equality filter on cn
    private static byte[] buildSearchRequest(int messageId, String baseDn, String assetCn, String attributeName) {
        byte[] baseObject = encodeOctetString(baseDn);
        byte[] scopeSingleLevel = encodeEnumerated(1);
        byte[] derefNever = encodeEnumerated(0);
        byte[] sizeLimit = encodeInteger(1);
        byte[] timeLimit = encodeInteger(5);
        byte[] typesOnlyFalse = encodeBoolean(false);
        byte[] filterAttribute = encodeOctetString("cn");
        byte[] filterValue = encodeOctetString(escapeFilterValue(assetCn));
        byte[] equalityMatchContent = concat(filterAttribute, filterValue);
        byte[] filter = encodeContextConstructed(3, equalityMatchContent);
        byte[] attributes = encodeSequence(encodeOctetString(attributeName));
        byte[] searchContent = concat(baseObject, scopeSingleLevel, derefNever, sizeLimit, timeLimit, typesOnlyFalse, filter, attributes);
        byte[] searchRequest = encodeApplicationConstructed(3, searchContent);
        return encodeSequence(concat(encodeInteger(messageId), searchRequest));
    }

    // escapes special chars so user input cannot break the ldap filter format
    private static String escapeFilterValue(String input) {
        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) {
            switch (c) {
                case '*':
                    sb.append("\\2a");
                    break;
                case '(':
                    sb.append("\\28");
                    break;
                case ')':
                    sb.append("\\29");
                    break;
                case '\\':
                    sb.append("\\5c");
                    break;
                case '\0':
                    sb.append("\\00");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }

    private static BindResult parseBindResponse(byte[] message, int expectedMessageId) {
        BerReader top = new BerReader(message);
        BerReader ldapMessage = top.readExpectedTagAsReader(0x30);

        int messageId = ldapMessage.readInteger();
        if (messageId != expectedMessageId) {
            throw new IllegalArgumentException("Unexpected bind response message ID: " + messageId);
        }

        BerReader bindResponse = ldapMessage.readExpectedTagAsReader(0x61);
        int resultCode = bindResponse.readEnumerated();
        bindResponse.readOctetString();
        String diagnostic = bindResponse.readOctetString();

        BindResult result = new BindResult();
        result.resultCode = resultCode;
        result.diagnosticMessage = diagnostic;
        result.success = resultCode == 0;
        return result;
    }

    // handles both entry packets (0x64) and done packet (0x65)
    private static SearchStepResult parseSearchStep(byte[] message, int expectedMessageId, String targetAttribute) {
        BerReader top = new BerReader(message);
        BerReader ldapMessage = top.readExpectedTagAsReader(0x30);

        int messageId = ldapMessage.readInteger();
        if (messageId != expectedMessageId) {
            throw new IllegalArgumentException("Unexpected search response message ID: " + messageId);
        }

        int opTag = ldapMessage.peekTag();
        if (opTag == 0x64) {
            BerReader entry = ldapMessage.readExpectedTagAsReader(0x64);
            entry.readOctetString();
            BerReader attributes = entry.readExpectedTagAsReader(0x30);

            String found = null;
            while (attributes.hasRemaining()) {
                BerReader partialAttribute = attributes.readExpectedTagAsReader(0x30);
                String attributeType = partialAttribute.readOctetString();
                BerReader values = partialAttribute.readExpectedTagAsReader(0x31);

                if (attributeType.equalsIgnoreCase(targetAttribute) && values.hasRemaining()) {
                    found = values.readOctetString();
                } else {
                    while (values.hasRemaining()) {
                        values.skipElement();
                    }
                }
            }

            SearchStepResult result = new SearchStepResult();
            result.type = SearchStepType.ENTRY;
            result.attributeValue = found;
            return result;
        }

        if (opTag == 0x65) {
            BerReader done = ldapMessage.readExpectedTagAsReader(0x65);
            int code = done.readEnumerated();
            done.readOctetString();
            String diagnostic = done.readOctetString();
            SearchStepResult result = new SearchStepResult();
            result.type = SearchStepType.DONE;
            result.resultCode = code;
            result.diagnosticMessage = diagnostic;
            return result;
        }

        if (opTag == 0x73) {
            ldapMessage.skipElement();
            SearchStepResult result = new SearchStepResult();
            result.type = SearchStepType.REFERENCE;
            return result;
        }
        throw new IllegalArgumentException("Unexpected LDAP operation tag: 0x" + Integer.toHexString(opTag));
    }

    // reads exactly one ber message off the socket: tag + length + content
    private static byte[] readBerMessage(InputStream in) throws IOException {
        int tag = in.read();
        if (tag == -1) {
            throw new IOException("Connection closed while reading BER tag.");
        }

        int firstLenByte = in.read();
        if (firstLenByte == -1) {
            throw new IOException("Connection closed while reading BER length.");
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        out.write(firstLenByte);

        int contentLength;
        if ((firstLenByte & 0x80) == 0) {
            contentLength = firstLenByte;
        } else {
            int lenCount = firstLenByte & 0x7F;
            if (lenCount == 0) {
                throw new IOException("Indefinite BER length is not supported.");
            }
            contentLength = 0;
            for (int i = 0; i < lenCount; i++) {
                int b = in.read();
                if (b == -1) {
                    throw new IOException("Connection closed while reading BER long length.");
                }
                out.write(b);
                contentLength = (contentLength << 8) | (b & 0xFF);
            }
        }

        byte[] content = readExactly(in, contentLength);
        out.write(content);
        return out.toByteArray();
    }

    // small helper so we don't accidentally parse partial packets
    private static byte[] readExactly(InputStream in, int count) throws IOException {
        byte[] data = new byte[count];
        int offset = 0;
        while (offset < count) {
            int n = in.read(data, offset, count - offset);
            if (n == -1) {
                throw new IOException("Connection closed before reading full message body.");
            }
            offset += n;
        }
        return data;
    }

    private static byte[] encodeSequence(byte[] content) {
        return encodeTagAndContent(0x30, content);
    }

    // app tags are ldap protocol operations (bindrequest, searchrequest, etc)
    private static byte[] encodeApplicationConstructed(int tagNumber, byte[] content) {
        int tag = 0x60 | (tagNumber & 0x1F);
        return encodeTagAndContent(tag, content);
    }

    // context-specific constructed tags are used in filters and auth choices
    private static byte[] encodeContextConstructed(int tagNumber, byte[] content) {
        int tag = 0xA0 | (tagNumber & 0x1F);
        return encodeTagAndContent(tag, content);
    }

    private static byte[] encodeContextPrimitive(int tagNumber, byte[] content) {
        int tag = 0x80 | (tagNumber & 0x1F);
        return encodeTagAndContent(tag, content);
    }

    // encodes positive ints the ber way (with leading 0x00 when needed)
    private static byte[] encodeInteger(int value) {
        if (value == 0) {
            return encodeTagAndContent(0x02, new byte[] { 0x00 });
        }

        List<Byte> bytes = new ArrayList<>();
        int temp = value;
        while (temp != 0) {
            bytes.add(0, (byte) (temp & 0xFF));
            temp >>>= 8;
        }

        if ((bytes.get(0) & 0x80) != 0) {
            bytes.add(0, (byte) 0x00);
        }

        byte[] content = new byte[bytes.size()];
        for (int i = 0; i < bytes.size(); i++) {
            content[i] = bytes.get(i);
        }
        return encodeTagAndContent(0x02, content);
    }

    // enumerated uses same payload as integer, just a different tag
    private static byte[] encodeEnumerated(int value) {
        byte[] integerLike = encodeInteger(value);
        integerLike[0] = 0x0A;
        return integerLike;
    }

    private static byte[] encodeBoolean(boolean value) {
        return encodeTagAndContent(0x01, new byte[] { (byte) (value ? 0xFF : 0x00) });
    }

    private static byte[] encodeOctetString(String value) {
        return encodeTagAndContent(0x04, value.getBytes(StandardCharsets.UTF_8));
    }

    // generic tlv wrapper (tag-length-value)
    private static byte[] encodeTagAndContent(int tag, byte[] content) {
        byte[] len = encodeLength(content.length);
        return concat(new byte[] { (byte) tag }, len, content);
    }

    // ber short/long length encoding
    private static byte[] encodeLength(int length) {
        if (length < 0x80) {
            return new byte[] { (byte) length };
        }

        List<Byte> bytes = new ArrayList<>();
        int temp = length;
        while (temp > 0) {
            bytes.add(0, (byte) (temp & 0xFF));
            temp >>>= 8;
        }

        byte[] result = new byte[1 + bytes.size()];
        result[0] = (byte) (0x80 | bytes.size());
        for (int i = 0; i < bytes.size(); i++) {
            result[i + 1] = bytes.get(i);
        }
        return result;
    }

    // simple byte-array join helper to build packets
    private static byte[] concat(byte[]... arrays) {
        int total = 0;
        for (byte[] arr : arrays) {
            total += arr.length;
        }

        byte[] out = new byte[total];
        int pos = 0;
        for (byte[] arr : arrays) {
            System.arraycopy(arr, 0, out, pos, arr.length);
            pos += arr.length;
        }
        return out;
    }

    private enum SearchStepType {
        ENTRY,
        REFERENCE,
        DONE
    }

    private static class BindResult {
        boolean success;
        int resultCode;
        String diagnosticMessage;
    }

    private static class SearchStepResult {
        SearchStepType type;
        int resultCode;
        String diagnosticMessage;
        String attributeValue;
    }

    // tiny ber reader just for the tags this practical actually needs
    private static class BerReader {
        private final byte[] data;
        private int pos;

        BerReader(byte[] data) {
            this.data = data;
            this.pos = 0;
        }

        boolean hasRemaining() {
            return pos < data.length;
        }

        int peekTag() {
            ensureAvailable(1);
            return data[pos] & 0xFF;
        }

        // reads one tlv and returns a new reader over the value bytes
        BerReader readExpectedTagAsReader(int expectedTag) {
            int tag = readByte();
            if (tag != expectedTag) {
                throw new IllegalArgumentException("Expected tag 0x" + Integer.toHexString(expectedTag)
                        + " but found 0x" + Integer.toHexString(tag));
            }
            int length = readLength();
            byte[] content = readBytes(length);
            return new BerReader(content);
        }

        int readInteger() {
            int tag = readByte();
            if (tag != 0x02) {
                throw new IllegalArgumentException("Expected INTEGER tag, got 0x" + Integer.toHexString(tag));
            }
            int length = readLength();
            if (length < 1 || length > 4) {
                throw new IllegalArgumentException("Unsupported INTEGER length: " + length);
            }

            int value = 0;
            for (int i = 0; i < length; i++) {
                value = (value << 8) | (readByte() & 0xFF);
            }
            return value;
        }

        int readEnumerated() {
            int tag = readByte();
            if (tag != 0x0A) {
                throw new IllegalArgumentException("Expected ENUMERATED tag, got 0x" + Integer.toHexString(tag));
            }
            int length = readLength();
            if (length < 1 || length > 4) {
                throw new IllegalArgumentException("Unsupported ENUMERATED length: " + length);
            }

            int value = 0;
            for (int i = 0; i < length; i++) {
                value = (value << 8) | (readByte() & 0xFF);
            }
            return value;
        }

        String readOctetString() {
            int tag = readByte();
            if (tag != 0x04) {
                throw new IllegalArgumentException("Expected OCTET STRING tag, got 0x" + Integer.toHexString(tag));
            }
            int length = readLength();
            byte[] content = readBytes(length);
            return new String(content, StandardCharsets.UTF_8);
        }

        void skipElement() {
            readByte();
            int length = readLength();
            readBytes(length);
        }

        private int readByte() {
            ensureAvailable(1);
            return data[pos++] & 0xFF;
        }

        private byte[] readBytes(int count) {
            ensureAvailable(count);
            byte[] out = new byte[count];
            System.arraycopy(data, pos, out, 0, count);
            pos += count;
            return out;
        }

        private int readLength() {
            int first = readByte();
            if ((first & 0x80) == 0) {
                return first;
            }

            int lenCount = first & 0x7F;
            if (lenCount == 0) {
                throw new IllegalArgumentException("Indefinite BER lengths are not supported.");
            }
            if (lenCount > 4) {
                throw new IllegalArgumentException("Length too large to parse: " + lenCount + " bytes.");
            }

            int length = 0;
            for (int i = 0; i < lenCount; i++) {
                length = (length << 8) | (readByte() & 0xFF);
            }
            return length;
        }

        private void ensureAvailable(int needed) {
            if (pos + needed > data.length) {
                throw new IllegalArgumentException("Unexpected end of BER message.");
            }
        }
    }
}
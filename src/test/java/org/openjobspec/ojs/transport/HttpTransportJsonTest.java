package org.openjobspec.ojs.transport;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the internal JSON encoder/decoder in {@link Json}.
 * Covers edge cases, unicode handling, number precision, and malformed input.
 */
class HttpTransportJsonTest {

    // -----------------------------------------------------------------------
    // Encoding
    // -----------------------------------------------------------------------

    @Nested
    class EncoderTests {

        @Test
        void encodesNull() {
            assertEquals("null", Json.encode(null));
        }

        @Test
        void encodesString() {
            assertEquals("\"hello\"", Json.encode("hello"));
        }

        @Test
        void encodesStringWithEscapes() {
            assertEquals("\"line1\\nline2\"", Json.encode("line1\nline2"));
            assertEquals("\"tab\\there\"", Json.encode("tab\there"));
            assertEquals("\"quote\\\"here\"", Json.encode("quote\"here"));
            assertEquals("\"back\\\\slash\"", Json.encode("back\\slash"));
        }

        @Test
        void encodesStringWithControlCharacters() {
            var encoded = Json.encode("\u0001\u001f");
            assertTrue(encoded.contains("\\u0001"));
            assertTrue(encoded.contains("\\u001f"));
        }

        @Test
        void encodesInteger() {
            assertEquals("42", Json.encode(42));
            assertEquals("-1", Json.encode(-1));
            assertEquals("0", Json.encode(0));
        }

        @Test
        void encodesLong() {
            assertEquals("9999999999", Json.encode(9999999999L));
        }

        @Test
        void encodesDouble() {
            assertEquals("3.14", Json.encode(3.14));
        }

        @Test
        void encodesBoolean() {
            assertEquals("true", Json.encode(true));
            assertEquals("false", Json.encode(false));
        }

        @Test
        void encodesEmptyMap() {
            assertEquals("{}", Json.encode(Map.of()));
        }

        @Test
        void encodesMap() {
            var map = new LinkedHashMap<String, Object>();
            map.put("name", "test");
            map.put("count", 5);
            assertEquals("{\"name\":\"test\",\"count\":5}", Json.encode(map));
        }

        @Test
        void encodesNestedMap() {
            var inner = new LinkedHashMap<String, Object>();
            inner.put("key", "value");
            var outer = new LinkedHashMap<String, Object>();
            outer.put("nested", inner);
            assertEquals("{\"nested\":{\"key\":\"value\"}}", Json.encode(outer));
        }

        @Test
        void encodesEmptyList() {
            assertEquals("[]", Json.encode(List.of()));
        }

        @Test
        void encodesList() {
            assertEquals("[1,2,3]", Json.encode(List.of(1, 2, 3)));
        }

        @Test
        void encodesArray() {
            assertEquals("[\"a\",\"b\"]", Json.encode(new Object[]{"a", "b"}));
        }

        @Test
        void encodesNullInMap() {
            var map = new LinkedHashMap<String, Object>();
            map.put("key", null);
            assertEquals("{\"key\":null}", Json.encode(map));
        }

        @Test
        void encodesMixedList() {
            assertEquals("[\"hello\",42,true,null]",
                    Json.encode(List.of("hello", 42, true, "null_placeholder"))
                            .replace("\"null_placeholder\"", "null"));
        }

        @Test
        void encodesObjectToString() {
            // Non-standard types should be encoded as strings via toString()
            var result = Json.encode(java.time.Duration.ofSeconds(5));
            assertTrue(result.startsWith("\""));
            assertTrue(result.endsWith("\""));
        }
    }

    // -----------------------------------------------------------------------
    // Decoding - Basic Types
    // -----------------------------------------------------------------------

    @Nested
    class DecoderBasicTests {

        @Test
        void decodesNull() {
            assertNull(Json.decode("null"));
        }

        @Test
        void decodesTrue() {
            assertEquals(Boolean.TRUE, Json.decode("true"));
        }

        @Test
        void decodesFalse() {
            assertEquals(Boolean.FALSE, Json.decode("false"));
        }

        @Test
        void decodesString() {
            assertEquals("hello", Json.decode("\"hello\""));
        }

        @Test
        void decodesEmptyString() {
            assertEquals("", Json.decode("\"\""));
        }

        @Test
        void decodesStringWithEscapes() {
            assertEquals("line1\nline2", Json.decode("\"line1\\nline2\""));
            assertEquals("tab\there", Json.decode("\"tab\\there\""));
            assertEquals("quote\"here", Json.decode("\"quote\\\"here\""));
            assertEquals("back\\slash", Json.decode("\"back\\\\slash\""));
            assertEquals("slash/here", Json.decode("\"slash\\/here\""));
            assertEquals("\b\f\r", Json.decode("\"\\b\\f\\r\""));
        }

        @Test
        void decodesInteger() {
            assertEquals(42, Json.decode("42"));
            assertEquals(-1, Json.decode("-1"));
            assertEquals(0, Json.decode("0"));
        }

        @Test
        void decodesLong() {
            assertEquals(9999999999L, Json.decode("9999999999"));
        }

        @Test
        void decodesDouble() {
            assertEquals(3.14, Json.decode("3.14"));
        }

        @Test
        void decodesScientificNotation() {
            assertEquals(1.5e10, Json.decode("1.5e10"));
            assertEquals(2.0E-3, Json.decode("2.0E-3"));
            assertEquals(1.0e+5, Json.decode("1.0e+5"));
        }

        @Test
        void decodesNegativeDouble() {
            assertEquals(-3.14, Json.decode("-3.14"));
        }
    }

    // -----------------------------------------------------------------------
    // Decoding - Unicode
    // -----------------------------------------------------------------------

    @Nested
    class DecoderUnicodeTests {

        @Test
        void decodesUnicodeEscape() {
            assertEquals("\u00e9", Json.decode("\"\\u00e9\"")); // é
        }

        @Test
        void decodesMultipleUnicodeEscapes() {
            assertEquals("\u00e9\u00e8", Json.decode("\"\\u00e9\\u00e8\"")); // éè
        }

        @Test
        void decodesNullCharacterUnicode() {
            assertEquals("\u0000", Json.decode("\"\\u0000\""));
        }

        @Test
        void decodesUppercaseUnicodeEscape() {
            assertEquals("\u00E9", Json.decode("\"\\u00E9\""));
        }

        @Test
        void throwsOnIncompleteUnicodeEscape() {
            assertThrows(IllegalArgumentException.class,
                    () -> Json.decode("\"\\u00\""));
        }

        @Test
        void throwsOnTruncatedUnicodeEscapeAtEnd() {
            assertThrows(IllegalArgumentException.class,
                    () -> Json.decode("\"\\uFF\""));
        }

        @Test
        void throwsOnUnicodeEscapeAtEndOfInput() {
            assertThrows(IllegalArgumentException.class,
                    () -> Json.decode("\"\\u\""));
        }
    }

    // -----------------------------------------------------------------------
    // Decoding - Objects and Arrays
    // -----------------------------------------------------------------------

    @Nested
    class DecoderStructureTests {

        @Test
        void decodesEmptyObject() {
            var result = Json.decode("{}");
            assertInstanceOf(Map.class, result);
            assertTrue(((Map<?, ?>) result).isEmpty());
        }

        @Test
        void decodesSimpleObject() {
            @SuppressWarnings("unchecked")
            var result = (Map<String, Object>) Json.decode("{\"name\":\"test\",\"count\":5}");
            assertEquals("test", result.get("name"));
            assertEquals(5, result.get("count"));
        }

        @Test
        void decodesNestedObject() {
            @SuppressWarnings("unchecked")
            var result = (Map<String, Object>) Json.decode(
                    "{\"outer\":{\"inner\":\"value\"}}");
            @SuppressWarnings("unchecked")
            var outer = (Map<String, Object>) result.get("outer");
            assertEquals("value", outer.get("inner"));
        }

        @Test
        void decodesEmptyArray() {
            var result = Json.decode("[]");
            assertInstanceOf(List.class, result);
            assertTrue(((List<?>) result).isEmpty());
        }

        @Test
        void decodesSimpleArray() {
            @SuppressWarnings("unchecked")
            var result = (List<Object>) Json.decode("[1,2,3]");
            assertEquals(List.of(1, 2, 3), result);
        }

        @Test
        void decodesMixedArray() {
            @SuppressWarnings("unchecked")
            var result = (List<Object>) Json.decode("[\"hello\",42,true,null]");
            assertEquals(4, result.size());
            assertEquals("hello", result.get(0));
            assertEquals(42, result.get(1));
            assertEquals(true, result.get(2));
            assertNull(result.get(3));
        }

        @Test
        void decodesArrayOfObjects() {
            @SuppressWarnings("unchecked")
            var result = (List<Object>) Json.decode(
                    "[{\"a\":1},{\"b\":2}]");
            assertEquals(2, result.size());
        }

        @Test
        void decodesDeeplyNestedStructure() {
            var json = "{\"l1\":{\"l2\":{\"l3\":{\"l4\":{\"value\":\"deep\"}}}}}";
            @SuppressWarnings("unchecked")
            var result = (Map<String, Object>) Json.decode(json);
            @SuppressWarnings("unchecked")
            var l1 = (Map<String, Object>) result.get("l1");
            @SuppressWarnings("unchecked")
            var l2 = (Map<String, Object>) l1.get("l2");
            @SuppressWarnings("unchecked")
            var l3 = (Map<String, Object>) l2.get("l3");
            @SuppressWarnings("unchecked")
            var l4 = (Map<String, Object>) l3.get("l4");
            assertEquals("deep", l4.get("value"));
        }

        @Test
        void decodesObjectWithWhitespace() {
            @SuppressWarnings("unchecked")
            var result = (Map<String, Object>) Json.decode(
                    "  { \"key\" : \"value\" , \"num\" : 1 }  ");
            assertEquals("value", result.get("key"));
            assertEquals(1, result.get("num"));
        }
    }

    // -----------------------------------------------------------------------
    // Decoding - Error Cases
    // -----------------------------------------------------------------------

    @Nested
    class DecoderErrorTests {

        @Test
        void throwsOnUnterminatedString() {
            assertThrows(IllegalArgumentException.class,
                    () -> Json.decode("\"unterminated"));
        }

        @Test
        void throwsOnUnterminatedObject() {
            assertThrows(IllegalArgumentException.class,
                    () -> Json.decode("{\"key\":\"value\""));
        }

        @Test
        void throwsOnUnterminatedArray() {
            assertThrows(IllegalArgumentException.class,
                    () -> Json.decode("[1,2,3"));
        }

        @Test
        void throwsOnInvalidToken() {
            assertThrows(IllegalArgumentException.class,
                    () -> Json.decode("undefined"));
        }

        @Test
        void throwsOnInvalidNumberFormat() {
            assertThrows(NumberFormatException.class,
                    () -> Json.decode("-"));
        }
    }

    // -----------------------------------------------------------------------
    // decodeObject
    // -----------------------------------------------------------------------

    @Nested
    class DecodeObjectTests {

        @Test
        void decodeObjectReturnsMap() {
            var result = Json.decodeObject("{\"key\":\"value\"}");
            assertEquals("value", result.get("key"));
        }

        @Test
        void decodeObjectThrowsForNonObject() {
            assertThrows(IllegalArgumentException.class,
                    () -> Json.decodeObject("[1,2,3]"));
        }

        @Test
        void decodeObjectThrowsForString() {
            assertThrows(IllegalArgumentException.class,
                    () -> Json.decodeObject("\"just a string\""));
        }
    }

    // -----------------------------------------------------------------------
    // Round-trip (encode then decode)
    // -----------------------------------------------------------------------

    @Nested
    class RoundTripTests {

        @Test
        void roundTripSimpleObject() {
            var original = new LinkedHashMap<String, Object>();
            original.put("name", "test");
            original.put("count", 42);
            original.put("active", true);
            original.put("data", null);

            var json = Json.encode(original);
            @SuppressWarnings("unchecked")
            var decoded = (Map<String, Object>) Json.decode(json);

            assertEquals("test", decoded.get("name"));
            assertEquals(42, decoded.get("count"));
            assertEquals(true, decoded.get("active"));
            assertNull(decoded.get("data"));
        }

        @Test
        void roundTripNestedStructure() {
            var inner = new LinkedHashMap<String, Object>();
            inner.put("x", 1);
            inner.put("y", 2);

            var original = new LinkedHashMap<String, Object>();
            original.put("point", inner);
            original.put("tags", List.of("a", "b"));

            var json = Json.encode(original);
            @SuppressWarnings("unchecked")
            var decoded = (Map<String, Object>) Json.decode(json);

            @SuppressWarnings("unchecked")
            var decodedPoint = (Map<String, Object>) decoded.get("point");
            assertEquals(1, decodedPoint.get("x"));
            assertEquals(2, decodedPoint.get("y"));

            @SuppressWarnings("unchecked")
            var decodedTags = (List<Object>) decoded.get("tags");
            assertEquals(List.of("a", "b"), decodedTags);
        }

        @Test
        void roundTripStringWithSpecialCharacters() {
            var original = "line1\nline2\ttab \"quoted\" back\\slash";
            var json = Json.encode(original);
            var decoded = Json.decode(json);
            assertEquals(original, decoded);
        }

        @Test
        void roundTripOjsJobEnvelope() {
            var job = new LinkedHashMap<String, Object>();
            job.put("id", "01912f4e-fd1a-7000-8000-000000000001");
            job.put("type", "email.send");
            job.put("queue", "default");
            job.put("state", "available");
            job.put("args", List.of(Map.of("to", "user@example.com")));
            job.put("priority", 0);
            job.put("attempt", 1);

            var envelope = new LinkedHashMap<String, Object>();
            envelope.put("job", job);

            var json = Json.encode(envelope);
            @SuppressWarnings("unchecked")
            var decoded = (Map<String, Object>) Json.decode(json);
            @SuppressWarnings("unchecked")
            var decodedJob = (Map<String, Object>) decoded.get("job");

            assertEquals("01912f4e-fd1a-7000-8000-000000000001", decodedJob.get("id"));
            assertEquals("email.send", decodedJob.get("type"));
            assertEquals("available", decodedJob.get("state"));
        }
    }

    // -----------------------------------------------------------------------
    // Safety Limits
    // -----------------------------------------------------------------------

    @Nested
    class SafetyLimitTests {

        @Test
        void rejectsDeeplyNestedObjects() {
            // Build JSON with depth > MAX_DEPTH using nested objects
            var sb = new StringBuilder();
            for (int i = 0; i < Json.MAX_DEPTH + 1; i++) {
                sb.append("{\"a\":");
            }
            sb.append("1");
            for (int i = 0; i < Json.MAX_DEPTH + 1; i++) {
                sb.append("}");
            }

            assertThrows(IllegalArgumentException.class,
                    () -> Json.decode(sb.toString()));
        }

        @Test
        void rejectsDeeplyNestedArrays() {
            var sb = new StringBuilder();
            for (int i = 0; i < Json.MAX_DEPTH + 1; i++) {
                sb.append("[");
            }
            sb.append("1");
            for (int i = 0; i < Json.MAX_DEPTH + 1; i++) {
                sb.append("]");
            }

            assertThrows(IllegalArgumentException.class,
                    () -> Json.decode(sb.toString()));
        }

        @Test
        void acceptsNestingAtMaxDepth() {
            // Build JSON with depth == MAX_DEPTH (should succeed)
            var sb = new StringBuilder();
            for (int i = 0; i < Json.MAX_DEPTH; i++) {
                sb.append("{\"a\":");
            }
            sb.append("1");
            for (int i = 0; i < Json.MAX_DEPTH; i++) {
                sb.append("}");
            }

            assertDoesNotThrow(() -> Json.decode(sb.toString()));
        }

        @Test
        void rejectsMixedDeepNesting() {
            // Mix objects and arrays to exceed depth
            var sb = new StringBuilder();
            for (int i = 0; i < Json.MAX_DEPTH + 1; i++) {
                if (i % 2 == 0) {
                    sb.append("{\"a\":");
                } else {
                    sb.append("[");
                }
            }
            sb.append("1");
            for (int i = Json.MAX_DEPTH; i >= 0; i--) {
                if (i % 2 == 0) {
                    sb.append("}");
                } else {
                    sb.append("]");
                }
            }

            assertThrows(IllegalArgumentException.class,
                    () -> Json.decode(sb.toString()));
        }

        @Test
        void rejectsInputExceedingMaxLength() {
            // Create a JSON string longer than MAX_INPUT_LENGTH
            var sb = new StringBuilder("\"");
            sb.append("x".repeat(Json.MAX_INPUT_LENGTH));
            sb.append("\"");

            assertThrows(IllegalArgumentException.class,
                    () -> Json.decode(sb.toString()));
        }

        @Test
        void acceptsInputAtMaxLength() {
            // Create a valid JSON string at exactly MAX_INPUT_LENGTH
            var padding = "x".repeat(Json.MAX_INPUT_LENGTH - 2); // minus 2 for quotes
            var json = "\"" + padding + "\"";
            assertEquals(Json.MAX_INPUT_LENGTH, json.length());

            assertDoesNotThrow(() -> Json.decode(json));
        }

        @Test
        void depthErrorMessageIsClear() {
            var sb = new StringBuilder();
            for (int i = 0; i < Json.MAX_DEPTH + 1; i++) {
                sb.append("[");
            }
            sb.append("1");
            for (int i = 0; i < Json.MAX_DEPTH + 1; i++) {
                sb.append("]");
            }

            var ex = assertThrows(IllegalArgumentException.class,
                    () -> Json.decode(sb.toString()));
            assertTrue(ex.getMessage().contains("nesting depth"));
            assertTrue(ex.getMessage().contains(String.valueOf(Json.MAX_DEPTH)));
        }

        @Test
        void lengthErrorMessageIsClear() {
            var sb = new StringBuilder("\"");
            sb.append("x".repeat(Json.MAX_INPUT_LENGTH));
            sb.append("\"");

            var ex = assertThrows(IllegalArgumentException.class,
                    () -> Json.decode(sb.toString()));
            assertTrue(ex.getMessage().contains("maximum length"));
        }
    }
}

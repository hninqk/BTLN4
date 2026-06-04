package com.auction.service.security;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

public final class PasswordHashService {

    private static final int MEMORY_KIB = 4_096;

    private static final int ITERATIONS = 1;

    private static final int PARALLELISM = 1;

    private static final int SALT_BYTES = 16;

    private static final int HASH_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Base64.Encoder B64_ENCODER = Base64.getEncoder().withoutPadding();

    private static final Base64.Decoder B64_DECODER = Base64.getDecoder();

    private PasswordHashService() {
    }

    public static String hash(String plaintextPassword) {
        if (plaintextPassword == null || plaintextPassword.isEmpty()) {
            throw new IllegalArgumentException("Password must not be empty");
        }

        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);

        char[] passwordChars = plaintextPassword.toCharArray();
        try {
            byte[] hash = hash(passwordChars, salt, MEMORY_KIB, ITERATIONS, PARALLELISM, HASH_BYTES);
            return "$argon2id$v=19$m=" + MEMORY_KIB
                    + ",t=" + ITERATIONS
                    + ",p=" + PARALLELISM
                    + "$" + B64_ENCODER.encodeToString(salt)
                    + "$" + B64_ENCODER.encodeToString(hash);
        } finally {
            Arrays.fill(passwordChars, '\0');
        }
    }

    public static boolean verify(String storedHash, String plaintextPassword) {
        if (!isArgon2idHash(storedHash) || plaintextPassword == null) {
            return false;
        }

        char[] passwordChars = plaintextPassword.toCharArray();
        try {
            ParsedHash parsed = ParsedHash.parse(storedHash);
            byte[] candidate = hash(
                    passwordChars,
                    parsed.salt(),
                    parsed.memoryKiB(),
                    parsed.iterations(),
                    parsed.parallelism(),
                    parsed.hash().length);
            return MessageDigest.isEqual(candidate, parsed.hash());
        } catch (IllegalArgumentException e) {
            return false;
        } finally {
            Arrays.fill(passwordChars, '\0');
        }
    }

    public static boolean isArgon2idHash(String value) {
        return value != null && value.startsWith("$argon2id$");
    }

    private static byte[] hash(
            char[] password,
            byte[] salt,
            int memoryKiB,
            int iterations,
            int parallelism,
            int hashLength) {
        Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withMemoryAsKB(memoryKiB)
                .withIterations(iterations)
                .withParallelism(parallelism)
                .withSalt(salt)
                .build();

        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(params);

        byte[] result = new byte[hashLength];
        generator.generateBytes(password, result);
        return result;
    }

    private record ParsedHash(
            int memoryKiB,
            int iterations,
            int parallelism,
            byte[] salt,
            byte[] hash) {

        private static ParsedHash parse(String phc) {
            String[] parts = phc.split("\\$");
            if (parts.length != 6
                    || !"argon2id".equals(parts[1])
                    || !"v=19".equals(parts[2])) {
                throw new IllegalArgumentException("Unsupported password hash format");
            }

            int memory = 0;
            int time = 0;
            int parallelism = 0;
            for (String param : parts[3].split(",")) {
                String[] keyValue = param.split("=", 2);
                if (keyValue.length != 2) {
                    throw new IllegalArgumentException("Invalid Argon2 parameter: " + param);
                }
                switch (keyValue[0]) {
                    case "m" -> memory = Integer.parseInt(keyValue[1]);
                    case "t" -> time = Integer.parseInt(keyValue[1]);
                    case "p" -> parallelism = Integer.parseInt(keyValue[1]);
                    default -> throw new IllegalArgumentException("Unknown Argon2 parameter: " + keyValue[0]);
                }
            }

            if (memory <= 0 || time <= 0 || parallelism <= 0) {
                throw new IllegalArgumentException("Invalid Argon2 parameters");
            }

            return new ParsedHash(
                    memory,
                    time,
                    parallelism,
                    decodeBase64(parts[4]),
                    decodeBase64(parts[5]));
        }

        private static byte[] decodeBase64(String value) {
            int padding = (4 - value.length() % 4) % 4;
            return B64_DECODER.decode(value + "=".repeat(padding));
        }
    }
}

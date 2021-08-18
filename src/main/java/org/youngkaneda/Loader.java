package org.youngkaneda;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.json.Json;
import javax.json.JsonObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Loader {

    private static String baseUrl = "https://twist.moe";
    private static String activeCdnUrl = "https://air-cdn.twist.moe";
    private static String cdnUrl = "https://cdn.twist.moe";
    private static String secret = "267041df55ca2b36f2e322d05ee2c9cf";
    private static String accessToken = "0df14814b9e590a1f26d3071a4ed7974";
    private static String userAgent = "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:91.0) Gecko/20100101 Firefox/91.0";

    public static void main(String[] args) {
        Json.createArrayBuilder();
        if (!checkArgs(args)) {
            return;
        }
        String title = args[0];
        int episode = Integer.parseInt(args[1]);
        if (title.isEmpty()) {
            System.out.println("Please give an anime name and a episode.");
            return;
        }
        Map<String, String> apiHeader = new HashMap<>();
        apiHeader.put("user-agent", userAgent);
        apiHeader.put("x-access-token", accessToken);
        List<Object> animeList = new ArrayList<>();
        try {
            String json = new String(makeRequestAndGetData("GET", baseUrl + "/api/anime", apiHeader), StandardCharsets.UTF_8);
            animeList.addAll(Arrays.asList(Json.createReader(new StringReader(json)).readArray().toArray()));
        } catch (IOException e) {
            System.out.println("A problem occurred trying to get the anime json list.");
            System.out.println(e.getMessage());
            System.exit(1);
        }
        List<JsonObject> animeMatched = animeList.stream()
            .map(obj -> ((JsonObject) obj))
            .filter(anime -> Stream.of(
                anime.getString("title").toLowerCase(Locale.ROOT),
                anime.getString("alt-title", "").toLowerCase(Locale.ROOT),
                anime.getJsonObject("slug").getString("slug").toLowerCase(Locale.ROOT)
            ).anyMatch(s -> s.contains(title)))
            .collect(Collectors.toList());
        if (animeMatched.isEmpty()) {
            System.out.println("Anime not found.");
            return;
        }
        JsonObject animeInfo;
        if (animeMatched.size() > 1) {
            int index = 0;
            Scanner scanner = new Scanner(System.in);
            while (index <= 0 || index > animeMatched.size() + 1) {
                System.out.println("Please select one of the following: ");
                for (int i = 0; i < animeMatched.size(); i++) {
                    System.out.println((i + 1) + ". " + animeMatched.get(i).getString("title"));
                }
                System.out.print("> ");
                index = Integer.parseInt(scanner.nextLine());
            }
            animeInfo = animeMatched.get(index - 1);
        } else {
            animeInfo = animeMatched.get(0);
        }
        String animeSourcesUrl = String.format(
            "%s/%s/%s/sources",
            baseUrl,
            "api/anime",
            animeInfo.getJsonObject("slug").getString("slug"));
        List<Object> sources = new ArrayList<>();
        try {
            String json = new String(makeRequestAndGetData("GET", animeSourcesUrl, apiHeader), StandardCharsets.UTF_8);
            sources.addAll(Arrays.asList(Json.createReader(new StringReader(json)).readArray().toArray()));
        } catch (IOException e) {
            System.out.println("A problem occurred trying to get the chosen anime sources.");
            System.out.println(e.getMessage());
            System.exit(1);
        }
        JsonObject sourceInfo = sources.stream()
            .map(obj -> ((JsonObject) obj))
            .filter(source -> source.getInt("number") == episode)
            .findFirst()
            .orElse(null);
        if (sourceInfo != null) {
            try {
                String source = decrypt(sourceInfo.getString("source", ""));
                String mediaUrl = animeInfo.getInt("ongoing", 0) != 0
                    ? activeCdnUrl + source
                    : cdnUrl + source;
                // get file name and ext
                int index = mediaUrl.lastIndexOf("/");
                String filenameAndExt = mediaUrl.substring(index + 1);
                mediaUrl = mediaUrl.replace(" ","%20");
                downloadFile(mediaUrl, animeInfo.getString("title"), filenameAndExt);
            } catch (GeneralSecurityException e) {
                System.out.println("An error occurred while trying to decrypt the source media url.");
                System.out.println(e.getMessage());
                System.exit(1);
            } catch (Exception e) {
                System.out.println("A problem occurred while downloading the episode.");
                System.out.println(e.getMessage());
                System.exit(1);
            }
        } else {
            System.out.println("Episode not found.");
        }
    }

    private static boolean checkArgs(String[] args) {
        if (args.length < 2) {
            System.out.println("Please give the needed arguments.");
            return false;
        }
        try {
            if (Integer.parseInt(args[1]) <= 0) {
                System.out.println("Please give a number higher than 0.");
                return false;
            };
        } catch (NumberFormatException e) {
            System.out.println("Invalid episode value.");
            System.out.println(e.getMessage());
            return false;
        }
        return true;
    }

    public static void downloadFile(String mediaUrl, String directory, String filename) throws Exception {
        Map<String, String> header = new HashMap<>();
        header.put("user-agent", userAgent);
        header.put("x-access-token", accessToken);
        header.put("referer", baseUrl);
        HttpURLConnection connection;
        connection = (java.net.HttpURLConnection)(new URL(mediaUrl)).openConnection();
        connection.setRequestMethod("HEAD");
        header.forEach(connection::setRequestProperty);
        long contentLength = connection.getContentLengthLong();
        connection.disconnect();
        long rangeSize = 1048576 * 3;
        long offset = 0;
        Map<String, String> mediaHeader = new HashMap<>(header);
        File animeDirectory = new File(System.getProperty("user.home") + File.separator + directory);
        animeDirectory.mkdir();
        FileOutputStream fos = new FileOutputStream(animeDirectory.getAbsolutePath() + File.separator + filename, true);
        while (offset < contentLength) {
            mediaHeader.put(
                "Range", "bytes=" + offset + "-" + (Math.min((offset + rangeSize), contentLength))
            );
            try {
                fos.write(makeRequestAndGetData("GET", mediaUrl, mediaHeader));
            } catch (IOException e) {
                System.out.println("A problem occurred trying to write the media data to a file.");
                System.out.println(e.getMessage());
                System.exit(1);
            }
            offset += rangeSize + 1;
            System.out.print("downloading: " + getDownloadPercentage(contentLength, offset - 1) + "%\r");
        }
        fos.flush();
        fos.close();
        System.out.println();
    }

    public static byte[] makeRequestAndGetData(String method, String url, Map<String, String> headers) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(method);
        // adding the headers <key, value> pairs to the request properties
        headers.forEach(connection::setRequestProperty);
        // reading the url inputstream
        BufferedInputStream bis = new BufferedInputStream(connection.getInputStream());
        byte[] bytes = inputStreamToByteArray(bis);
        bis.close();
        connection.disconnect();
        return bytes;
    }

    private static long getDownloadPercentage(long contentLength, long downloaded) {
        return (downloaded * 100)/contentLength;
    }

    public static String decrypt(String chiperText) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException {
        byte[] data = Base64.getDecoder().decode(chiperText);
        byte[] saltData = Arrays.copyOfRange(data, 8, 16);
        byte[] encrypted = Arrays.copyOfRange(data, 16, data.length);
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        final byte[][] keyAndIV = generateKeyAndIV(32, 16, 1, saltData, secret.getBytes(StandardCharsets.UTF_8), md5);
        SecretKeySpec key = new SecretKeySpec(keyAndIV[0], "AES");
        IvParameterSpec iv = new IvParameterSpec(keyAndIV[1]);
        Cipher aesCBC = Cipher.getInstance("AES/CBC/PKCS5Padding");
        aesCBC.init(Cipher.DECRYPT_MODE, key, iv);
        byte[] decryptedData = aesCBC.doFinal(encrypted);
        return new String(decryptedData, StandardCharsets.UTF_8);
    }

    public static byte[][] generateKeyAndIV(int keyLength, int ivLength, int iterations, byte[] salt, byte[] password, MessageDigest md) {
        int digestLength = md.getDigestLength();
        int requiredLength = (keyLength + ivLength + digestLength - 1) / digestLength * digestLength;
        byte[] generatedData = new byte[requiredLength];
        int generatedLength = 0;
        try {
            md.reset();
            // Repeat process until sufficient data has been generated
            while (generatedLength < keyLength + ivLength) {
                // Digest data (last digest if available, password data, salt if available)
                if (generatedLength > 0)
                    md.update(generatedData, generatedLength - digestLength, digestLength);
                md.update(password);
                if (salt != null)
                    md.update(salt, 0, 8);
                md.digest(generatedData, generatedLength, digestLength);
                // additional rounds
                for (int i = 1; i < iterations; i++) {
                    md.update(generatedData, generatedLength, digestLength);
                    md.digest(generatedData, generatedLength, digestLength);
                }
                generatedLength += digestLength;
            }
            // Copy key and IV into separate byte arrays
            byte[][] result = new byte[2][];
            result[0] = Arrays.copyOfRange(generatedData, 0, keyLength);
            if (ivLength > 0) {
                result[1] = Arrays.copyOfRange(generatedData, keyLength, keyLength + ivLength);
            }
            return result;
        } catch (DigestException e) {
            throw new RuntimeException(e);
        } finally {
            // Clean out temporary data
            Arrays.fill(generatedData, (byte) 0);
        }
    }

    public static byte[] inputStreamToByteArray(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[16384];
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }
}

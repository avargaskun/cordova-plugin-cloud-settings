package cordova.plugin.cloudsettings;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates all raw HTTP calls to the Google Drive REST API v3.
 * All methods are static, synchronous, and throw IOException on failure.
 * The caller (CloudSettingsPlugin) handles threading and error-to-callbackContext translation.
 */
public class DriveHelper {

    private static final String TAG = "DriveHelper";
    private static final String DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files";
    private static final String DRIVE_UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files";
    private static final String DRIVE_ABOUT_URL = "https://www.googleapis.com/drive/v3/about?fields=user";
    private static final String BOUNDARY = "cloud_settings_boundary";

    /**
     * Fetch the email for the authorized Google account using the Drive About API.
     * Uses the drive/v3/about endpoint which works with just the drive.appdata scope,
     * avoiding the need for an additional email/openid scope.
     *
     * @param accessToken OAuth2 access token with drive.appdata scope
     * @return the email address of the authenticated user
     * @throws IOException on network or parse error
     */
    public static String fetchEmail(String accessToken) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(DRIVE_ABOUT_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);

            int responseCode = conn.getResponseCode();
            Log.d(TAG, "fetchEmail: HTTP " + responseCode);
            if (responseCode != 200) {
                throw new IOException("fetchEmail failed with HTTP " + responseCode + ": " + readErrorStream(conn));
            }

            String body = readStream(conn);
            JSONObject json = new JSONObject(body);
            JSONObject user = json.getJSONObject("user");
            return user.getString("emailAddress");
        } catch (IOException e) {
            Log.e(TAG, "fetchEmail: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            Log.e(TAG, "fetchEmail: " + e.getMessage());
            throw new IOException("fetchEmail error: " + e.getMessage(), e);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * List files in appDataFolder matching a name query. Returns file IDs.
     *
     * @param accessToken OAuth2 access token with drive.appdata scope
     * @param nameQuery   file name to search for (e.g. "cloudsettings.json")
     * @return list of file IDs matching the query
     * @throws IOException on network or parse error
     */
    public static List<String> listFiles(String accessToken, String nameQuery) throws IOException {
        HttpURLConnection conn = null;
        try {
            String query = "name%3D'" + nameQuery + "'";
            URL url = new URL(DRIVE_FILES_URL + "?spaces=appDataFolder&q=" + query + "&fields=files(id)");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);

            int responseCode = conn.getResponseCode();
            Log.d(TAG, "listFiles: HTTP " + responseCode);
            if (responseCode != 200) {
                throw new IOException("listFiles failed with HTTP " + responseCode + ": " + readErrorStream(conn));
            }

            String body = readStream(conn);
            JSONObject json = new JSONObject(body);
            JSONArray files = json.getJSONArray("files");

            List<String> fileIds = new ArrayList<>();
            for (int i = 0; i < files.length(); i++) {
                fileIds.add(files.getJSONObject(i).getString("id"));
            }
            Log.d(TAG, "listFiles: found " + fileIds.size() + " file(s)");
            return fileIds;
        } catch (IOException e) {
            Log.e(TAG, "listFiles: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            Log.e(TAG, "listFiles: " + e.getMessage());
            throw new IOException("listFiles error: " + e.getMessage(), e);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * Download file content by ID. Returns the file body as a String.
     *
     * @param accessToken OAuth2 access token with drive.appdata scope
     * @param fileId      the Drive file ID to download
     * @return the file content as a string
     * @throws IOException on network error
     */
    public static String downloadFile(String accessToken, String fileId) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(DRIVE_FILES_URL + "/" + fileId + "?alt=media");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);

            int responseCode = conn.getResponseCode();
            Log.d(TAG, "downloadFile: HTTP " + responseCode);
            if (responseCode != 200) {
                throw new IOException("downloadFile failed with HTTP " + responseCode + ": " + readErrorStream(conn));
            }

            String content = readStream(conn);
            Log.d(TAG, "downloadFile: downloaded " + content.length() + " bytes");
            return content;
        } catch (IOException e) {
            Log.e(TAG, "downloadFile: " + e.getMessage());
            throw e;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * Create a new file in appDataFolder. Returns the new file ID.
     *
     * @param accessToken OAuth2 access token with drive.appdata scope
     * @param fileName    the name of the file to create (e.g. "cloudsettings.json")
     * @param content     the file content (JSON string)
     * @return the new file ID
     * @throws IOException on network or parse error
     */
    public static String createFile(String accessToken, String fileName, String content) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(DRIVE_UPLOAD_URL + "?uploadType=multipart");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            conn.setRequestProperty("Content-Type", "multipart/related; boundary=" + BOUNDARY);
            conn.setDoOutput(true);

            // Build metadata JSON with parents
            JSONObject metadata = new JSONObject();
            metadata.put("name", fileName);
            JSONArray parents = new JSONArray();
            parents.put("appDataFolder");
            metadata.put("parents", parents);

            String body = buildMultipartBody(metadata.toString(), content);

            OutputStream os = conn.getOutputStream();
            os.write(body.getBytes("UTF-8"));
            os.flush();
            os.close();

            int responseCode = conn.getResponseCode();
            Log.d(TAG, "createFile: HTTP " + responseCode);
            if (responseCode != 200 && responseCode != 201) {
                throw new IOException("createFile failed with HTTP " + responseCode + ": " + readErrorStream(conn));
            }

            String responseBody = readStream(conn);
            JSONObject json = new JSONObject(responseBody);
            String fileId = json.getString("id");
            Log.d(TAG, "createFile: created id=" + fileId);
            return fileId;
        } catch (IOException e) {
            Log.e(TAG, "createFile: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            Log.e(TAG, "createFile: " + e.getMessage());
            throw new IOException("createFile error: " + e.getMessage(), e);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * Update an existing file by ID.
     *
     * @param accessToken OAuth2 access token with drive.appdata scope
     * @param fileId      the Drive file ID to update
     * @param fileName    the file name (for metadata)
     * @param content     the new file content (JSON string)
     * @throws IOException on network error
     */
    public static void updateFile(String accessToken, String fileId, String fileName, String content) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(DRIVE_UPLOAD_URL + "/" + fileId + "?uploadType=multipart");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            // HttpURLConnection doesn't support PATCH directly; use X-HTTP-Method-Override
            conn.setRequestProperty("X-HTTP-Method-Override", "PATCH");
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            conn.setRequestProperty("Content-Type", "multipart/related; boundary=" + BOUNDARY);
            conn.setDoOutput(true);

            // Build metadata JSON without parents (can't change parent on update)
            JSONObject metadata = new JSONObject();
            metadata.put("name", fileName);

            String body = buildMultipartBody(metadata.toString(), content);

            OutputStream os = conn.getOutputStream();
            os.write(body.getBytes("UTF-8"));
            os.flush();
            os.close();

            int responseCode = conn.getResponseCode();
            Log.d(TAG, "updateFile: HTTP " + responseCode + " for id=" + fileId);
            if (responseCode != 200) {
                throw new IOException("updateFile failed with HTTP " + responseCode + ": " + readErrorStream(conn));
            }
        } catch (IOException e) {
            Log.e(TAG, "updateFile: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            Log.e(TAG, "updateFile: " + e.getMessage());
            throw new IOException("updateFile error: " + e.getMessage(), e);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * Build a multipart/related body with metadata and content parts.
     */
    private static String buildMultipartBody(String metadataJson, String content) {
        StringBuilder sb = new StringBuilder();
        sb.append("--").append(BOUNDARY).append("\r\n");
        sb.append("Content-Type: application/json; charset=UTF-8\r\n");
        sb.append("\r\n");
        sb.append(metadataJson).append("\r\n");
        sb.append("--").append(BOUNDARY).append("\r\n");
        sb.append("Content-Type: application/json; charset=UTF-8\r\n");
        sb.append("\r\n");
        sb.append(content).append("\r\n");
        sb.append("--").append(BOUNDARY).append("--");
        return sb.toString();
    }

    /**
     * Read the response body from an HttpURLConnection.
     */
    private static String readStream(HttpURLConnection conn) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        return sb.toString();
    }

    /**
     * Read the error stream from an HttpURLConnection, or return a fallback message.
     */
    private static String readErrorStream(HttpURLConnection conn) {
        try {
            if (conn.getErrorStream() == null) {
                return "(no error body)";
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            return "(could not read error stream: " + e.getMessage() + ")";
        }
    }
}

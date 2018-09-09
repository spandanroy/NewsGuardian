package com.example.spandanroy.newsguardian;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class QueryUtils {

    private static int readTimeOut = 10000, connectTimeOut = 15000;

    private static final String LOG_TAG = QueryUtils.class.getSimpleName();

    private QueryUtils() {
    }

    private static URL createUrl(String stringUrl) {
        URL url = null;
        try {
            url = new URL(stringUrl);
        } catch (MalformedURLException e) {
            Log.e(LOG_TAG, "Problem with building URL ", e);
        }
        return url;
    }

    private static String makeHttpRequest(URL url) throws IOException {
        String jsonResponse = "";

        // If the URL is null, then return early.
        if (url == null) {
            return jsonResponse;
        }

        HttpURLConnection urlConnection = null;
        InputStream inputStream = null;

        try {
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setReadTimeout(readTimeOut /* milliseconds */);
            urlConnection.setConnectTimeout(connectTimeOut /* milliseconds */);
            urlConnection.setRequestMethod("GET");
            urlConnection.connect();

            // If the request was successful (response code 200),
            // then read the input stream and parse the response.
            if (urlConnection.getResponseCode() == 200) {
                inputStream = urlConnection.getInputStream();
                jsonResponse = readFromStream(inputStream);
            } else {
                Log.e(LOG_TAG, "Error response code: " + urlConnection.getResponseCode());
            }
        } catch (IOException e) {

            Log.e(LOG_TAG, "Problem retrieving the article JSON results.", e);
        } finally {
            if (urlConnection != null) {
                inputStream=urlConnection.getErrorStream();
                urlConnection.disconnect();
            }
            if (inputStream != null) {
                inputStream.close();
            }
        }
        return jsonResponse;
    }

    private static String readFromStream(InputStream inputStream) throws IOException {
        StringBuilder output = new StringBuilder();
        if (inputStream != null) {
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream, Charset.forName("UTF-8"));
            BufferedReader reader = new BufferedReader(inputStreamReader);
            String line = reader.readLine();
            while (line != null) {
                output.append(line);
                line = reader.readLine();
            }
        }
        return output.toString();
    }

    public static ArrayList<Article> extractFeatureFromJSON(String articleJSON) {

        if (TextUtils.isEmpty(articleJSON)) {
            return null;
        }
        ArrayList<Article> articles = new ArrayList<>();

        try {
            JSONObject baseJsonResponse = new JSONObject(articleJSON);

            JSONObject response = baseJsonResponse.optJSONObject("response");
            JSONArray results = response.optJSONArray("results");

            for (int i = 0; i < results.length(); i++) {

                JSONObject r = results.optJSONObject(i);

                String id = r.optString("id");
                String title = r.optString("webTitle");
                String webPublicationDate = r.optString("webPublicationDate");
                String url = r.optString("webUrl");
                String sectionName = r.optString("sectionName");

                JSONObject fields = r.optJSONObject("fields");
                String author = fields.optString("byline");
                String thumbnail = fields.optString("thumbnail");

                String date1 = "";
                Drawable imgThumbnail = null;
                try {
                    Bitmap bitmap = getBitmapFromURL(thumbnail);
                    imgThumbnail = new BitmapDrawable(Resources.getSystem(), bitmap);
                } catch (IOException e) {
                    Log.e(LOG_TAG, "Problem with getting bitmap from URL.", e);
                }

                try {
                    DateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
                    Date date = format.parse(webPublicationDate);
                    DateFormat format2 = new SimpleDateFormat("HH:mm | dd MMM yy", Locale.getDefault());
                    date1 = format2.format(date);
                } catch (ParseException e) {
                    Log.e(LOG_TAG, "extractFeatureFromJSON: ", e);
                }


                Article article = new Article(id, title, author, date1, imgThumbnail, url, sectionName);

                articles.add(article);
            }

        } catch (JSONException e) {
            Log.e(LOG_TAG, "Problem parsing the article JSON results", e);
        }

        return articles;
    }

    public static List<Article> fetchNewsData(String requestURL) {
        URL url = createUrl(requestURL);
        String jsonResponse = null;
        try {
            jsonResponse = makeHttpRequest(url);
        } catch (IOException e) {
            Log.e(LOG_TAG, "Error closing input stream", e);
        }
        return extractFeatureFromJSON(jsonResponse);
    }

    private static Bitmap getBitmapFromURL(String imageUrl) throws IOException {
        HttpURLConnection connection = null;
        InputStream input = null;
        try {
            URL url = new URL(imageUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setReadTimeout(readTimeOut);
            connection.setConnectTimeout(connectTimeOut);
            connection.setDoInput(true);
            connection.connect();
            input = connection.getInputStream();
            return BitmapFactory.decodeStream(new FlushedInputStream(input));
        } catch (IOException e) {
            Log.e(LOG_TAG, "Problem with getting bitmap form URL.", e);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            if (input != null) {
                input.close();
            }
        }
    }

    static class FlushedInputStream extends FilterInputStream {
        public FlushedInputStream(InputStream inputStream) {
            super(inputStream);
        }

        @Override
        public long skip(long n) throws IOException {
            long totalBytesSkipped = 0L;
            while (totalBytesSkipped < n) {
                long bytesSkipped = in.skip(n - totalBytesSkipped);
                if (bytesSkipped == 0L) {
                    int b = read();
                    if (b < 0) {
                        break;  // we reached EOF
                    } else {
                        bytesSkipped = 1; // we read one byte
                    }
                }
                totalBytesSkipped += bytesSkipped;
            }
            return totalBytesSkipped;
        }
    }

}


package webserver.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.IOUtils;
import webserver.http.parser.CookieParser;
import webserver.http.parser.FormUrlencodedBodyParser;

import java.io.*;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

public class HttpRequest {
    private static final Logger logger = LoggerFactory.getLogger(HttpRequest.class);

    private HttpMethod method;
    private String path;
    private final Map<String, String> parameters;
    private final Map<String, String> headers;
    private final Map<String, String> cookies;
    private String body;

    private HttpRequest() {
        parameters = new HashMap<>();
        headers = new HashMap<>();
        cookies = new HashMap<>();
    }

    public HttpMethod getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public String getParameter(String name) {
        return parameters.get(name);
    }

    public String getHeader(String name) {
        return headers.get(name);
    }

    public UUID getSessionId() {
        String sessionId = getCookie(HttpSession.COOKIE_HTTP_SESSION_KEY);
        if (sessionId != null) {
            return UUID.fromString(sessionId);
        }
        return null;
    }

    public HttpSession getSession() {
        if (getSessionId() == null) {
            HttpSession session = new HttpSession();
            HttpSessionStorage.putSession(session);
            return session;
        }
        return HttpSessionStorage.getSession(getSessionId());
    }

    public String getCookie(String name) {
        return cookies.get(name);
    }

    public static class Parser {
        public static HttpRequest parse(InputStream in) throws IOException {
            HttpRequest httpRequest = new HttpRequest();

            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String inputLine = br.readLine();

            handleFirstLine(inputLine, httpRequest);
            while(isNotEmpty(inputLine = br.readLine())) {
                handleLine(inputLine, httpRequest);
            }
            readBody(br, httpRequest);
            handleFormBody(httpRequest);
            handleCookies(httpRequest);

            logger.debug("method={}, path={}", httpRequest.method, httpRequest.path);
            logger.debug("parameters : {}", httpRequest.parameters);
            logger.debug("headers : {}", httpRequest.headers);
            return httpRequest;
        }

        private static boolean isNotEmpty(String line) {
            return (line != null) && (line.length() > 0);
        }

        private static void handleLine(String inputLine, HttpRequest httpRequest) {
            String[] headerLine = inputLine.split(":", 2);
            httpRequest.headers.put(headerLine[0].trim(), headerLine[1].trim());
        }

        private static void handleFirstLine(String firstLine, HttpRequest httpRequest) {
            try {
                String[] token = firstLine.split(" ");
                httpRequest.method = HttpMethod.valueOf(token[0].trim());
                String urlDecoded = URLDecoder.decode(token[1], "utf-8");
                parsePathAndParameters(urlDecoded, httpRequest);
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException("UnsupportedEncodingException : " + e.getMessage());
            }
        }

        private static void parsePathAndParameters(String pathAndParams, HttpRequest httpRequest) {
            String[] tokens = pathAndParams.split("[?&]");
            httpRequest.path = tokens[0];
            Stream.of(tokens).skip(1)
                    .map(param -> param.split("="))
                    .forEach(p -> httpRequest.parameters.put(p[0], p[1]));
        }

        private static void readBody(BufferedReader br, HttpRequest httpRequest) throws IOException {
            if (!httpRequest.headers.containsKey(HttpHeaders.CONTENT_LENGTH)) {
                return;
            }
            int contentLength = Integer.parseInt(httpRequest.headers.get(HttpHeaders.CONTENT_LENGTH));
            httpRequest.body = IOUtils.readData(br, contentLength);
        }

        private static void handleFormBody(HttpRequest httpRequest) {
            if (!httpRequest.headers.containsKey(HttpHeaders.CONTENT_TYPE)) {
                return;
            }
            String contentType = httpRequest.headers.get(HttpHeaders.CONTENT_TYPE);
            if (ContentType.APPLICATION_FORM_URLENCODED.equals(contentType)) {
                Map<String, String> body = FormUrlencodedBodyParser.parse(httpRequest.body);
                body.forEach(httpRequest.parameters::put);
            }
        }

        private static void handleCookies(HttpRequest httpRequest) {
            if (!httpRequest.headers.containsKey(HttpHeaders.COOKIE)) {
                logger.debug("empty cookies");
                return;
            }

            String cookieString = httpRequest.headers.get(HttpHeaders.COOKIE);
            Map<String, String> cookies = CookieParser.parse(cookieString);
            cookies.forEach(httpRequest.cookies::put);
        }
    }
}

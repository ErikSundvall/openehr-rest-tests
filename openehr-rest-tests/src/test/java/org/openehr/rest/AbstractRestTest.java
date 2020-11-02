package org.openehr.rest;

import care.better.platform.locatable.LocatableUid;
import care.better.platform.model.Ehr;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.openehr.data.OpenEhrViewRequest;
import org.openehr.data.OpenEhrViewResponse;
import org.openehr.jaxb.rm.Composition;
import org.openehr.jaxb.rm.Folder;
import org.openehr.jaxb.rm.ObjectRef;
import org.openehr.jaxb.rm.ObjectVersionId;
import org.openehr.rest.conf.WebClientConfiguration;
import org.openehr.utils.FolderBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openehr.data.OpenEhrConstants.POST_COMPOSITION_PATH;
import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.http.HttpStatus.*;
import static org.springframework.http.MediaType.*;

/**
 * @author Dusan Markovic
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ContextConfiguration(classes = WebClientConfiguration.class)
public class AbstractRestTest {
    public static final DateTimeFormatter DATE_TIME_FORMATTER = ISODateTimeFormat.dateTime().withZoneUTC();
    private static final Pattern HEADER_ETAG_PATTERN = Pattern.compile("W/\"|\"");

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    @Qualifier("restTemplate")
    protected RestTemplate restTemplate;

    @Value("${openehr.rest.uri}")
    protected URI uri;

    protected String targetPath;
    protected String ehrId;
    protected String nonExistingUid;
    protected String compositionUid;
    protected String compositionUid2;
    protected Composition composition;
    protected Composition unProcessableComposition;

    @BeforeAll
    public void setUp() throws IOException {
        targetPath = uri.toURL().toString();
        nonExistingUid = UUID.randomUUID() + "::domain3::1";
        HttpHeaders headers = fullRepresentationHeaders();
        ResponseEntity<Ehr> ehrResponseEntity = exchange(getTargetPath() + "/ehr", POST, null, Ehr.class, headers);
        ehrId = Objects.requireNonNull(ehrResponseEntity.getBody()).getEhrId().getValue();

        uploadTemplate("/rest/Demo Vitals.opt");
        composition = objectMapper.readValue(IOUtils.toString(
                OpenEhrCompositionRestTest.class.getResourceAsStream("/rest/composition.json"),
                StandardCharsets.UTF_8).replace("{{REPLACE_THIS}}", "Jane Nurse"), Composition.class);
        assertThat(composition).isNotNull();
        assertThat(composition.getUid()).isNull();
        compositionUid = postComposition(ehrId, composition);
        String jsonCompositionWithPlaceholder = IOUtils.toString(
                OpenEhrCompositionRestTest.class.getResourceAsStream("/rest/AtemfrequenzTemplate-composition.json"),
                StandardCharsets.UTF_8);
        unProcessableComposition = objectMapper.readValue(jsonCompositionWithPlaceholder.replace("{{REPLACE_THIS}}", "John Nurse"), Composition.class);
        unProcessableComposition.setArchetypeNodeId("openEHR-EHR-COMPOSITION.report.sv2");
    }

    public String getTargetPath() {
        return targetPath;
    }

    protected <R> R get(String url, Class<R> responseType) {
        return getResponse(url, responseType).getBody();
    }

    protected <R> R get(String url, Class<R> responseType, MediaType acceptMediaTypes) {
        return getResponse(url, responseType, acceptMediaTypes).getBody();
    }

    protected <R> ResponseEntity<R> getResponse(String url, Class<R> responseType, Object... uriVariables) {
        return getResponse(restTemplate, url, responseType, null, uriVariables);
    }

    protected <R> ResponseEntity<R> getResponse(String url, Class<R> responseType, MediaType acceptMediaTypes, Object... uriVariables) {
        return getResponse(restTemplate, url, responseType, acceptMediaTypes, uriVariables);
    }

    protected <R> ResponseEntity<R> getResponse(
            RestTemplate restTemplate,
            String url,
            Class<R> responseType,
            MediaType acceptMediaTypes,
            Object... uriVariables) {
        HttpHeaders acceptHeaders = acceptMediaTypes == null ? new HttpHeaders() : createAcceptHeaders(acceptMediaTypes);
        return exchange(restTemplate, url, HttpMethod.GET, null, responseType, acceptHeaders, uriVariables);
    }

    protected <R> ResponseEntity<R> getResponse(RestTemplate restTemplate, String url, Class<R> responseType) {
        HttpHeaders acceptHeaders = new HttpHeaders();
        return exchange(restTemplate, url, HttpMethod.GET, null, responseType, acceptHeaders);
    }

    protected <R> ResponseEntity<R> deleteResponse(String url, Class<R> responseType, MediaType... acceptMediaTypes) {
        HttpHeaders acceptHeaders = acceptMediaTypes == null ? null : createAcceptHeaders(acceptMediaTypes);
        return exchange(url, HttpMethod.DELETE, null, responseType, acceptHeaders);
    }

    protected <R> ResponseEntity<R> deleteResponse(RestTemplate restTemplate, String url, Class<R> responseType, MediaType... acceptMediaTypes) {
        HttpHeaders acceptHeaders = acceptMediaTypes == null ? null : createAcceptHeaders(acceptMediaTypes);
        return exchange(restTemplate, url, HttpMethod.DELETE, null, responseType, acceptHeaders);
    }

    protected <R, B> ResponseEntity<R> exchange(
            String url,
            HttpMethod httpMethod,
            @Nullable B body,
            Class<R> responseType,
            MediaType acceptMediaTypes) {
        HttpHeaders acceptHeaders = acceptMediaTypes == null ? null : createAcceptHeaders(acceptMediaTypes);
        return exchange(url, httpMethod, body, responseType, acceptHeaders);
    }

    protected <R, B> ResponseEntity<R> exchange(
            String url,
            HttpMethod httpMethod,
            @Nullable B body,
            Class<R> responseType,
            @Nullable HttpHeaders headers,
            Object... uriVariables) {
        return exchange(restTemplate, url, httpMethod, body, responseType, headers, uriVariables);
    }

    protected <R, B> ResponseEntity<R> exchange(
            String url,
            HttpMethod httpMethod,
            @Nullable B body,
            @Nullable HttpHeaders headers,
            @Nullable MediaType defaultContentType,
            Class<R> responseType,
            Object... uriVariables) {
        return exchange(restTemplate, url, httpMethod, body, responseType, headers, defaultContentType, uriVariables);
    }

    protected <R, B> ResponseEntity<R> exchange(
            String url,
            HttpMethod httpMethod,
            @Nullable B body,
            Class<R> responseType) {
        return exchange(restTemplate, url, httpMethod, body, responseType, null);
    }

    protected <R> ResponseEntity<R> exchange(
            RestTemplate restTemplate,
            String url,
            HttpMethod httpMethod,
            Class<R> responseType) {
        return exchange(restTemplate, url, httpMethod, null, responseType, null);
    }

    protected <R, B> ResponseEntity<R> exchange(
            RestTemplate restTemplate,
            String url,
            HttpMethod httpMethod,
            @Nullable B body,
            Class<R> responseType,
            @Nullable HttpHeaders headers,
            Object... uriVariables) {
        return exchange(restTemplate, url, httpMethod, body, responseType, headers, APPLICATION_JSON, uriVariables);
    }

    protected <R, B> ResponseEntity<R> exchange(
            RestTemplate restTemplate,
            String url,
            HttpMethod httpMethod,
            @Nullable B body,
            Class<R> responseType,
            @Nullable HttpHeaders headers,
            @Nullable MediaType defaultContentType,
            Object... uriVariables) {
        if (headers == null) {
            headers = createAcceptHeaders(APPLICATION_JSON);
        } else if (!headers.containsKey(ACCEPT)) {
            headers.setAccept(Collections.singletonList(APPLICATION_JSON));
        }
        if (headers.getContentType() == null) {
            headers.setContentType(defaultContentType);
        }
        HttpEntity<B> requestEntity = new HttpEntity<>(body, headers);
        return restTemplate.exchange(url, httpMethod, requestEntity, responseType, uriVariables);
    }

    protected HttpHeaders createAcceptHeaders(MediaType... headers) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setAccept(Arrays.asList(headers));
        return httpHeaders;
    }

    protected HttpHeaders createContentTypeHeaders(MediaType contentType) {
        return createContentTypeHeaders(contentType, APPLICATION_JSON);
    }

    protected HttpHeaders createContentTypeHeaders(MediaType contentType, MediaType... accept) {
        HttpHeaders httpHeaders = createAcceptHeaders(accept);
        httpHeaders.setContentType(contentType);
        return httpHeaders;
    }

    protected void validateLocationAndETag(ResponseEntity<?> response) {
        validateLocationAndETag(response, true, true);
    }

    protected void validateLocationAndETag(HttpStatusCodeException exception) {
        validateLocationAndETag(exception, true, true);
    }

    protected void validateLocationAndETag(ResponseEntity<?> response, boolean etagAndLastModifiedAllowed, boolean locationAllowed) {
        validateLocationAndETag(response.getHeaders(), response.getStatusCode(), etagAndLastModifiedAllowed, locationAllowed);
    }

    protected void validateLocationAndETag(HttpStatusCodeException exception, boolean etagAndLastModifiedAllowed, boolean locationAllowed) {
        validateLocationAndETag(Objects.requireNonNull(exception.getResponseHeaders()), exception.getStatusCode(), etagAndLastModifiedAllowed, locationAllowed);
    }

    protected void validateLocationAndETag(HttpHeaders headers, HttpStatus statusCode, boolean etagAndLastModifiedAllowed, boolean locationAllowed) {
        URI locationUri = headers.getLocation();
        if (locationAllowed) {
            assertThat(locationUri).isNotNull();
            String decodedUrl;
            try {
                URI targetPathURI = new URI(getTargetPath());
                assertThat(locationUri.getPort()).isEqualTo(targetPathURI.getPort());
                assertThat(locationUri.getPath()).startsWith(targetPathURI.getPath());
                InetAddress targetPathInetAddress = InetAddress.getByName(targetPathURI.getHost());
                InetAddress locationInetAddress = InetAddress.getByName(locationUri.getHost());
                assertThat(targetPathInetAddress).isEqualTo(locationInetAddress);
                decodedUrl = URLDecoder.decode(locationUri.toString(), StandardCharsets.UTF_8.name());
            } catch (UnsupportedEncodingException | URISyntaxException | UnknownHostException e) {
                throw new RuntimeException(e);
            }
            ResponseEntity<String> r = getResponse(decodedUrl, String.class, MediaType.ALL);
            assertThat(r).isNotNull();
            if (r.getStatusCode() == OK) {
                assertThat(r.getBody()).isNotNull();
            } else if (r.getStatusCode() == NO_CONTENT) {
                assertThat(r.getBody()).isNull();
            } else {
                throw new RuntimeException("Unable to validate location header: " + locationUri + " Response: " + r.toString());
            }
        } else {
            assertThat(locationUri).withFailMessage("Location header '%s' should not be present!", locationUri).isNull();
        }
        String eTagString = headers.getETag();
        if (etagAndLastModifiedAllowed) {
            assertThat(eTagString).isNotNull();
            assertThat(eTagString).startsWith("\"");
            assertThat(eTagString).endsWith("\"");
            long lastModified = headers.getLastModified();
            if (statusCode.is2xxSuccessful()) {
                assertThat(lastModified).isGreaterThan(0L);
            }
            if (locationAllowed) {
                assertThat(locationUri.toString()).endsWith(eTagString.substring(1, eTagString.length() - 1));
            }
        } else {
            assertThat(eTagString).isNull();
        }
    }

    protected HttpHeaders fullRepresentationHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Prefer", "return=representation");
        headers.set(ACCEPT, APPLICATION_JSON_VALUE);
        headers.set(CONTENT_TYPE, APPLICATION_JSON_VALUE);
        return headers;
    }

    protected Folder createFolderWithSubfolder(String subFolderName, String mainFolderName) {
        return FolderBuilder.createFolder()
                .withName(mainFolderName)
                .withItems(createFolderItem())
                .withSubfolder(
                        FolderBuilder.createFolder()
                                .withName(subFolderName)
                                .withItems(createFolderItem())
                                .build()).build();
    }

    private ObjectRef createFolderItem() {
        ObjectVersionId objectVersionId = new ObjectVersionId();
        objectVersionId.setValue(UUID.randomUUID().toString());

        ObjectRef item = new ObjectRef();
        item.setId(objectVersionId);
        item.setNamespace("namespace");
        item.setType("ANY");
        return item;
    }

    protected <T> void validateLocationHeader(
            Class<T> objectClass,
            URI location,
            String folderStringToCheck,
            Function<T, String> compareStringGetter) {
        ResponseEntity<T> response = getResponse(location.toString(), objectClass);
        assertThat(response.getStatusCode()).isEqualTo(OK);
        T object = response.getBody();
        assertThat(object).isNotNull();
        assertThat(folderStringToCheck).isEqualTo(compareStringGetter.apply(object));
    }

    protected String postComposition(String ehrId, Composition medikationLoopComposition) {
        ResponseEntity<JsonNode> response = exchange(
                getTargetPath() + POST_COMPOSITION_PATH, POST, medikationLoopComposition, JsonNode.class, fullRepresentationHeaders(), ehrId);
        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        return Objects.requireNonNull(response.getBody().get("uid").get("value").asText());
    }

    protected void setCompositionUid(@javax.annotation.Nullable Composition composition, LocatableUid compositionUid) {
        if (composition != null) {
            ObjectVersionId objectVersionId = new ObjectVersionId();
            objectVersionId.setValue(compositionUid.toString());
            composition.setUid(objectVersionId);
        }
    }

    protected String getHeaderETag(ResponseEntity<?> response) {
        String eTag = response.getHeaders().getETag();
        return eTag != null ? HEADER_ETAG_PATTERN.matcher(eTag).replaceAll("") : null;
    }

    protected <D> D getBody(ResponseEntity<D> response) {
        D body = response.getBody();
        assertThat(body).isNotNull();
        return body;
    }

    protected String createRandomNumString() {
        Random random = new Random();
        return String.valueOf(random.nextInt());
    }

    protected void uploadTemplate(@Nonnull String templatePath) throws IOException {
        String templateString = IOUtils.toString(
                OpenEhrCompositionRestTest.class.getResourceAsStream(templatePath),
                StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(APPLICATION_XML);
        headers.setAccept(Collections.singletonList(APPLICATION_XML));
        try {
            ResponseEntity<String> templateResponseEntity = exchange(
                    getTargetPath() + "/definition/template/adl1.4", POST, templateString, String.class, headers);
        } catch (HttpClientErrorException e) {
            if (e.getRawStatusCode() != 409) {
                throw e;
            }
        }
    }

    protected void uploadNamedQuery(String description, String name, String query) {
        OpenEhrViewRequest request = new OpenEhrViewRequest();
        request.setDescription(description);
        request.setQ(query);
        try {
            ResponseEntity<OpenEhrViewResponse> response = exchange(
                    getTargetPath() + "/definition/query/{qualified-query-name}",
                    PUT,
                    request,
                    OpenEhrViewResponse.class,
                    null,
                    name);
        } catch (HttpClientErrorException e) {
            if (e.getRawStatusCode() != 409) {
                throw e;
            }
        }
    }

}

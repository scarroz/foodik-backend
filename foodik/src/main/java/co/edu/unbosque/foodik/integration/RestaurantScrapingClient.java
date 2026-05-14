package co.edu.unbosque.foodik.integration;

import co.edu.unbosque.foodik.domain.dto.response.RestaurantScrapedInfoResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RestaurantScrapingClient {

    private static final Logger log = LoggerFactory.getLogger(RestaurantScrapingClient.class);

    // ── Overpass: GET con ?data=<query_urlencoded> ─────────────────────────
    private static final String OVERPASS_URL        = "https://overpass-api.de/api/interpreter";
    private static final String OVERPASS_URL_MIRROR = "https://lz4.overpass-api.de/api/interpreter";

    // ── Nominatim fallback ──────────────────────────────────────────────────
    private static final String NOMINATIM_URL = "https://nominatim.openstreetmap.org/search";

    // ── Foursquare nueva Places API (dominio y versión actualizados) ────────
    private static final String FSQ_BASE          = "https://places-api.foursquare.com";
    private static final String FSQ_SEARCH        = FSQ_BASE + "/places/search";
    private static final String FSQ_DETAILS       = FSQ_BASE + "/places/";
    private static final String FSQ_API_VERSION   = "2025-06-17";
    private static final String FSQ_FOOD_CATEGORY = "13000";     // Food & Dining

    // ── Cache para evitar rate limits ───────────────────────────────────────
    private final Map<String, FoursquarePlace> fsqCache = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${application.scraping.user-agent:Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36}")
    private String userAgent;

    @Value("${application.scraping.timeout-ms:15000}")
    private int timeoutMs;

    @Value("${application.scraping.foursquare-api-key:}")
    private String foursquareApiKey;

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Busca restaurantes cercanos con estrategia multi-fuente.
     * El cliente nunca lanza excepción — siempre devuelve lista (vacía en peor caso).
     */
    public List<RestaurantScrapedInfoResponse> scrapeNearby(double lat, double lng, int radiusM) {
        log.info("scrapeNearby: lat={}, lng={}, radius={}m", lat, lng, radiusM);

        // 1. Overpass vía GET
        List<RestaurantScrapedInfoResponse> results = queryOverpass(lat, lng, radiusM);

        // 2. Nominatim si Overpass devolvió muy poco
        if (results.size() < 3) {
            log.warn("Overpass devolvió {} resultados — complementando con Nominatim", results.size());
            Set<String> seen = new HashSet<>();
            results.forEach(r -> { if (r.name() != null) seen.add(normalizeName(r.name())); });
            for (RestaurantScrapedInfoResponse r : queryNominatim(lat, lng, radiusM)) {
                if (r.name() != null && seen.add(normalizeName(r.name()))) results.add(r);
            }
        }

        // 3. Enriquecimiento Foursquare (opcional)
        if (hasFoursquareKey() && !results.isEmpty()) {
            results = enrichWithFoursquare(results, lat, lng, radiusM);
        }

        log.info("scrapeNearby total: {} restaurantes", results.size());
        return results;
    }

    /**
     * Obtiene información de un restaurante específico por nombre y ciudad.
     */
    public RestaurantScrapedInfoResponse scrapeRestaurantInfo(String name, String city) {
        log.info("scrapeRestaurantInfo: '{}' en '{}'", name, city);
        try {
            String query = String.format("""
                    [out:json][timeout:15];
                    area["name"~"%s",i]->.a;
                    (
                      node["amenity"~"restaurant|fast_food|cafe"]["name"~"%s",i](area.a);
                      way["amenity"~"restaurant|fast_food|cafe"]["name"~"%s",i](area.a);
                    );
                    out body;
                    """, escape(city), escape(name), escape(name));

            List<RestaurantScrapedInfoResponse> r = executeOverpassGet(query, OVERPASS_URL);
            if (!r.isEmpty()) return r.get(0);
        } catch (Exception e) {
            log.warn("Overpass falló para '{}': {}", name, e.getMessage());
        }
        return queryNominatimSingle(name, city);
    }

    // =========================================================================
    // OVERPASS — GET con ?data=<query_urlencoded>
    // =========================================================================


    private List<RestaurantScrapedInfoResponse> queryOverpass(double lat, double lng, int radiusM) {
        String query = String.format("""
                [out:json][timeout:25];
                (
                  node["amenity"~"^(restaurant|fast_food|cafe|bar|pub|food_court)$"](around:%d,%f,%f);
                  way["amenity"~"^(restaurant|fast_food|cafe|bar|pub|food_court)$"](around:%d,%f,%f);
                );
                out body;
                >;
                out skel qt;
                """, radiusM, lat, lng, radiusM, lat, lng);

        try {
            List<RestaurantScrapedInfoResponse> r = executeOverpassGet(query, OVERPASS_URL);
            log.info("Overpass principal: {} restaurantes", r.size());
            return r;
        } catch (Exception e) {
            log.warn("Overpass principal falló ({}), probando espejo...", e.getMessage());
            try {
                List<RestaurantScrapedInfoResponse> r = executeOverpassGet(query, OVERPASS_URL_MIRROR);
                log.info("Overpass espejo: {} restaurantes", r.size());
                return r;
            } catch (Exception e2) {
                log.error("Overpass espejo también falló: {}", e2.getMessage());
                return new ArrayList<>();
            }
        }
    }


    private List<RestaurantScrapedInfoResponse> executeOverpassGet(String query, String endpoint)
            throws Exception {
        String url = endpoint + "?data=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        log.debug("Overpass GET: {}...", url.substring(0, Math.min(url.length(), 120)));

        String body = httpGet(url, Map.of(
                "User-Agent",      userAgent,
                "Accept",          "*/*",
                "Accept-Language", "es-CO,es;q=0.9",
                "Connection",      "keep-alive"
        ));
        return parseOverpassResponse(body);
    }

    private List<RestaurantScrapedInfoResponse> parseOverpassResponse(String json) throws Exception {
        JsonNode elements = objectMapper.readTree(json).path("elements");
        List<RestaurantScrapedInfoResponse> results = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        if (!elements.isArray()) return results;

        for (JsonNode el : elements) {
            if (!el.has("tags")) continue;
            JsonNode tags = el.path("tags");

            String name = nullIfEmpty(tags.path("name").asText(null));
            if (name == null || !seen.add(normalizeName(name))) continue;

            Double elLat = el.has("lat") ? el.path("lat").asDouble() : null;
            Double elLon = el.has("lon") ? el.path("lon").asDouble() : null;

            String phone   = firstNonNull(
                    nullIfEmpty(tags.path("phone").asText(null)),
                    nullIfEmpty(tags.path("contact:phone").asText(null)));
            String website = firstNonNull(
                    nullIfEmpty(tags.path("website").asText(null)),
                    nullIfEmpty(tags.path("contact:website").asText(null)));
            String priceRange = extractOsmPrice(tags);
            String[] bounds   = normalizeCOP(priceRange);
            String osmId      = el.path("id").asText(null);
            String osmType    = el.path("type").asText("node");

            results.add(new RestaurantScrapedInfoResponse(
                    name,
                    nullIfEmpty(tags.path("description").asText(null)),
                    priceRange,
                    bounds[0], bounds[1],
                    null,
                    cleanCuisine(tags.path("cuisine").asText(null)),
                    nullIfEmpty(tags.path("opening_hours").asText(null)),
                    phone, website,
                    buildOsmAddress(tags, name),
                    osmId, elLat, elLon,
                    osmId != null ? "https://www.openstreetmap.org/" + osmType + "/" + osmId : null
            ));
        }
        return results;
    }

    // =========================================================================
    // NOMINATIM — fallback
    // =========================================================================

    private List<RestaurantScrapedInfoResponse> queryNominatim(double lat, double lng, int radiusM) {
        List<RestaurantScrapedInfoResponse> all = new ArrayList<>();
        double delta   = radiusM / 111320.0;
        String viewbox = (lng - delta) + "," + (lat + delta) + ","
                + (lng + delta) + "," + (lat - delta);

        for (String amenity : new String[]{"restaurant", "fast_food", "cafe"}) {
            try {
                String url = NOMINATIM_URL
                        + "?amenity=" + amenity
                        + "&format=json&addressdetails=1&extratags=1&namedetails=1"
                        + "&viewbox=" + viewbox + "&bounded=1&limit=10&countrycodes=co";
                log.debug("Nominatim query: {}", url);
                JsonNode arr = objectMapper.readTree(httpGet(url, Map.of("User-Agent", userAgent,
                        "Accept-Language", "es-CO,es;q=0.9")));
                if (!arr.isArray()) continue;
                log.debug("Nominatim amenity={} → {} resultados", amenity, arr.size());
                for (JsonNode node : arr) {
                    RestaurantScrapedInfoResponse r = parseNominatimNode(node);
                    if (r != null) all.add(r);
                }
            } catch (Exception e) {
                log.warn("Nominatim amenity={} falló: {}", amenity, e.getMessage());
            }
        }
        Map<String, RestaurantScrapedInfoResponse> deduped = new LinkedHashMap<>();
        for (RestaurantScrapedInfoResponse r : all)
            if (r.name() != null) deduped.putIfAbsent(normalizeName(r.name()), r);
        return new ArrayList<>(deduped.values());
    }

    private RestaurantScrapedInfoResponse queryNominatimSingle(String name, String city) {
        try {
            String q = URLEncoder.encode(name + " " + city, StandardCharsets.UTF_8);
            JsonNode arr = objectMapper.readTree(httpGet(
                    NOMINATIM_URL + "?q=" + q + "&format=json&extratags=1&limit=1&countrycodes=co",
                    Map.of("User-Agent", userAgent)));
            if (!arr.isArray() || arr.isEmpty()) return empty(name, city);
            RestaurantScrapedInfoResponse r = parseNominatimNode(arr.get(0));
            return r != null ? r : empty(name, city);
        } catch (Exception e) {
            log.warn("Nominatim single falló para '{}': {}", name, e.getMessage());
            return empty(name, city);
        }
    }

    private RestaurantScrapedInfoResponse parseNominatimNode(JsonNode node) {
        String display = node.path("display_name").asText(null);
        String name    = display != null ? display.split(",")[0].trim() : null;
        if (name == null || name.isBlank()) return null;
        JsonNode extra    = node.path("extratags");
        String priceRange = extractOsmPriceFromTag(nullIfEmpty(extra.path("price_range").asText(null)));
        String[] bounds   = normalizeCOP(priceRange);
        return new RestaurantScrapedInfoResponse(
                name, null, priceRange, bounds[0], bounds[1], null,
                cleanCuisine(extra.path("cuisine").asText(null)),
                nullIfEmpty(extra.path("opening_hours").asText(null)),
                nullIfEmpty(extra.path("phone").asText(null)),
                nullIfEmpty(extra.path("website").asText(null)),
                shortenAddress(display),
                node.path("osm_id").asText(null),
                parseDouble(node.path("lat").asText(null)),
                parseDouble(node.path("lon").asText(null)),
                "https://www.openstreetmap.org/node/" + node.path("osm_id").asText()
        );
    }

    // =========================================================================
    // FOURSQUARE nueva Places API
    //   Dominio : places-api.foursquare.com
    //   Header  : X-Places-Api-Version: 2025-06-17
    //   Auth    : Authorization: Bearer <KEY>
    //   Docs    : https://docs.foursquare.com/developer/reference/place-search
    // =========================================================================

    /**
     * FIX RATE LIMIT 429:
     * - Implementa caché local para no repetir búsquedas
     * - Si Foursquare falla, devuelve resultados sin enriquecer (no rompe el flujo)
     * - Log detallado para diagnóstico
     */
    private List<RestaurantScrapedInfoResponse> enrichWithFoursquare(
            List<RestaurantScrapedInfoResponse> results, double lat, double lng, int radiusM) {
        try {
            // 1. Búsqueda nearby
            String searchUrl = FSQ_SEARCH
                    + "?ll=" + lat + "," + lng
                    + "&radius=" + Math.min(radiusM, 100_000)
                    + "&categories=" + FSQ_FOOD_CATEGORY
                    + "&limit=50"
                    + "&fields=fsq_place_id,name,price,rating,tel,website,hours,location";

            log.debug("Foursquare search: {}", searchUrl);
            HttpResponse<String> searchResp = foursquareGet(searchUrl);

            if (!handleFoursquareStatus(searchResp.statusCode(), "search")) {
                return results;
            }

            // 2. Construir índice nombre → datos básicos + fsq_place_id (con caché)
            Map<String, FoursquarePlace> index = new LinkedHashMap<>();
            JsonNode places = objectMapper.readTree(searchResp.body()).path("results");
            if (places.isArray()) {
                for (JsonNode p : places) {
                    String n = p.path("name").asText(null);
                    if (n == null) continue;
                    String normalized = normalizeName(n);

                    // Usar caché si existe
                    if (fsqCache.containsKey(normalized)) {
                        index.put(normalized, fsqCache.get(normalized));
                        continue;
                    }

                    FoursquarePlace place = new FoursquarePlace(
                            p.path("fsq_place_id").asText(null),
                            p.path("price").isMissingNode()  ? null : p.path("price").asInt(),
                            p.path("rating").isMissingNode() ? null : p.path("rating").asDouble(),
                            nullIfEmpty(p.path("tel").asText(null)),
                            nullIfEmpty(p.path("website").asText(null)),
                            extractFsqHours(p.path("hours"))
                    );

                    fsqCache.put(normalized, place);
                    index.put(normalized, place);
                }
            }
            log.info("Foursquare indexó {} lugares para enriquecimiento", index.size());

            // 3. Merge: actualizar campos donde haya match por nombre
            List<RestaurantScrapedInfoResponse> enriched = new ArrayList<>();
            for (RestaurantScrapedInfoResponse r : results) {
                FoursquarePlace fsq = r.name() != null ? index.get(normalizeName(r.name())) : null;
                if (fsq == null) { enriched.add(r); continue; }

                String priceSymbol = fsq.price() != null ? priceToSymbol(fsq.price()) : r.priceRange();
                String[] bounds    = normalizeCOP(priceSymbol);
                String ratingStr   = fsq.rating() != null
                        ? String.format("%.1f / 10.0", fsq.rating()) : r.rating();
                String phone       = fsq.tel()     != null ? fsq.tel()     : r.phone();
                String website     = fsq.website() != null ? fsq.website() : r.website();
                String hours       = fsq.hours()   != null ? fsq.hours()   : r.openingHours();

                enriched.add(new RestaurantScrapedInfoResponse(
                        r.name(), r.description(), priceSymbol, bounds[0], bounds[1],
                        ratingStr, r.cuisine(), hours, phone, website,
                        r.address(), r.osmId(), r.latitude(), r.longitude(), r.sourceUrl()
                ));
            }
            return enriched;

        } catch (Exception e) {
            log.error("Foursquare enriquecimiento falló: {} — devolviendo resultados sin enriquecer",
                    e.getMessage());
            return results;
        }
    }

    /**
     * Obtiene detalles completos de un lugar por su fsq_place_id.
     * Endpoint: GET /places/{fsq_place_id}
     * Retorna: price, rating, tel, website, hours.display, photos, tastes, etc.
     */
    public FoursquareDetails getFoursquareDetails(String fsqPlaceId) throws Exception {
        String url = FSQ_DETAILS + fsqPlaceId
                + "?fields=fsq_place_id,name,price,rating,tel,website,hours,location,"
                + "photos,tastes,description,attributes";

        log.debug("Foursquare details: {}", url);
        HttpResponse<String> resp = foursquareGet(url);

        if (!handleFoursquareStatus(resp.statusCode(), "details/" + fsqPlaceId)) {
            return null;
        }

        JsonNode p = objectMapper.readTree(resp.body());
        return new FoursquareDetails(
                p.path("fsq_place_id").asText(null),
                p.path("name").asText(null),
                p.path("price").isMissingNode()  ? null : p.path("price").asInt(),
                p.path("rating").isMissingNode() ? null : p.path("rating").asDouble(),
                nullIfEmpty(p.path("tel").asText(null)),
                nullIfEmpty(p.path("website").asText(null)),
                extractFsqHours(p.path("hours")),
                nullIfEmpty(p.path("description").asText(null)),
                extractFsqPhotos(p.path("photos")),
                extractFsqTastes(p.path("tastes")),
                extractFsqAttributes(p.path("attributes"))
        );
    }

    // ── Foursquare HTTP helper ─────────────────────────────────────────────

    private HttpResponse<String> foursquareGet(String url) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization",      "Bearer " + foursquareApiKey)
                .header("X-Places-Api-Version", FSQ_API_VERSION)
                .header("Accept",              "application/json")
                .header("User-Agent",          userAgent)
                .timeout(Duration.ofMillis(timeoutMs + 5000L))
                .GET().build();

        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Maneja los códigos de error de Foursquare con mensajes de diagnóstico claros.
     * @return true si el request fue exitoso (HTTP 200)
     */
    private boolean handleFoursquareStatus(int status, String endpoint) {
        return switch (status) {
            case 200 -> true;
            case 401 -> {
                log.error("Foursquare 401 en '{}' — API key inválida o sin prefijo Bearer", endpoint);
                yield false;
            }
            case 403 -> {
                log.error("Foursquare 403 en '{}' — key sin permisos de Places API", endpoint);
                yield false;
            }
            case 404 -> {
                log.warn("Foursquare 404 en '{}' — recurso no encontrado", endpoint);
                yield false;
            }
            case 410 -> {
                log.error("""
                        Foursquare 410 en '{}' — endpoint deprecado.
                        Usa el dominio nuevo: places-api.foursquare.com
                        Header requerido: X-Places-Api-Version: {}
                        """, endpoint, FSQ_API_VERSION);
                yield false;
            }
            case 429 -> {
                log.warn("Foursquare 429 en '{}' — rate limit alcanzado. " +
                        "Espera 1 hora o verifica tu plan en https://developer.foursquare.com", endpoint);
                yield false;
            }
            default -> {
                log.error("Foursquare HTTP {} en '{}'", status, endpoint);
                yield false;
            }
        };
    }

    // ── Foursquare field extractors ────────────────────────────────────────

    private String extractFsqHours(JsonNode hours) {
        if (hours == null || hours.isMissingNode()) return null;
        return nullIfEmpty(hours.path("display").asText(null));
    }

    private List<String> extractFsqPhotos(JsonNode photos) {
        List<String> urls = new ArrayList<>();
        if (photos == null || !photos.isArray()) return null;
        int count = 0;
        for (JsonNode ph : photos) {
            if (count++ >= 3) break;
            String prefix = ph.path("prefix").asText(null);
            String suffix = ph.path("suffix").asText(null);
            if (prefix != null && suffix != null) urls.add(prefix + "300x300" + suffix);
        }
        return urls.isEmpty() ? null : urls;
    }

    private List<String> extractFsqTastes(JsonNode tastes) {
        if (tastes == null || !tastes.isArray()) return null;
        List<String> list = new ArrayList<>();
        for (JsonNode t : tastes) { String v = t.asText(null); if (v != null) list.add(v); }
        return list.isEmpty() ? null : list;
    }

    private Map<String, Boolean> extractFsqAttributes(JsonNode attrs) {
        if (attrs == null || attrs.isMissingNode()) return null;
        Map<String, Boolean> map = new LinkedHashMap<>();
        addAttr(map, attrs, "outdoor_seating", "outdoor_seating");
        addAttr(map, attrs, "reservations",    "reservations");
        addAttr(map, attrs, "delivery",        "delivery");
        addAttr(map, attrs, "has_parking",     "has_parking");
        addAttr(map, attrs, "atm",             "atm");
        if (attrs.has("wifi") && !attrs.path("wifi").isMissingNode()) {
            map.put("wifi", !"no".equalsIgnoreCase(attrs.path("wifi").asText("")));
        }
        return map.isEmpty() ? null : map;
    }

    private void addAttr(Map<String, Boolean> map, JsonNode attrs, String key, String label) {
        if (!attrs.path(key).isMissingNode()) map.put(label, attrs.path(key).asBoolean());
    }

    // =========================================================================
    // HTTP HELPER GENÉRICO
    // =========================================================================

    private String httpGet(String url, Map<String, String> headers)
            throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .followRedirects(HttpClient.Redirect.NORMAL).build();

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs + 15000L))
                .GET();
        headers.forEach(builder::header);

        HttpResponse<String> resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        log.debug("GET {} → HTTP {}",
                url.length() > 120 ? url.substring(0, 120) + "..." : url, resp.statusCode());

        if (resp.statusCode() != 200)
            throw new IOException("HTTP " + resp.statusCode() + " for: " + url);
        return resp.body();
    }

    // =========================================================================
    // NORMALIZACIÓN PRECIOS
    // =========================================================================

    private String extractOsmPrice(JsonNode tags) {
        String pr = extractOsmPriceFromTag(nullIfEmpty(tags.path("price_range").asText(null)));
        if (pr != null) return pr;
        String level = nullIfEmpty(tags.path("level").asText(null));
        if (level == null) return null;
        return switch (level.trim()) {
            case "1" -> "$"; case "2" -> "$$"; case "3" -> "$$$"; case "4" -> "$$$$";
            default  -> null;
        };
    }

    private String extractOsmPriceFromTag(String raw) {
        if (raw == null) return null;
        String cleaned = raw.replaceAll("[^$]", "");
        return cleaned.isEmpty() ? null : cleaned;
    }

    private String priceToSymbol(Integer price) {
        if (price == null) return null;
        return switch (price) {
            case 1 -> "$"; case 2 -> "$$"; case 3 -> "$$$"; case 4 -> "$$$$";
            default -> null;
        };
    }

    private String[] normalizeCOP(String symbol) {
        if (symbol == null) return new String[]{null, null};
        return switch (symbol) {
            case "$"    -> new String[]{"$10.000",  "$30.000"};
            case "$$"   -> new String[]{"$30.000",  "$70.000"};
            case "$$$"  -> new String[]{"$70.000",  "$150.000"};
            case "$$$$" -> new String[]{"$150.000", "+$150.000"};
            default     -> new String[]{null, null};
        };
    }

    // =========================================================================
    // STRING HELPERS
    // =========================================================================

    private String buildOsmAddress(JsonNode tags, String fallback) {
        StringBuilder sb = new StringBuilder();
        appendTag(sb, tags, "addr:street",      "");
        appendTag(sb, tags, "addr:housenumber", " ");
        appendTag(sb, tags, "addr:suburb",      ", ");
        appendTag(sb, tags, "addr:city",        ", ");
        return sb.isEmpty() ? fallback + ", Bogotá" : sb.toString().trim();
    }

    private void appendTag(StringBuilder sb, JsonNode tags, String key, String prefix) {
        String v = nullIfEmpty(tags.path(key).asText(null));
        if (v != null) { if (!sb.isEmpty()) sb.append(prefix); sb.append(v); }
    }

    private String normalizeName(String name) {
        if (name == null) return "";
        return Normalizer.normalize(name.toLowerCase().trim(), Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "").replaceAll("[^a-z0-9 ]", "").trim();
    }

    private String cleanCuisine(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return (raw.contains(";") ? raw.split(";")[0] : raw).trim().replace("_", " ");
    }

    private String shortenAddress(String full) {
        if (full == null) return null;
        String[] p = full.split(",");
        return p.length <= 2 ? full : p[0].trim() + ", " + p[1].trim() + ", Bogotá";
    }

    private String nullIfEmpty(String v) {
        return (v == null || v.isBlank() || "null".equalsIgnoreCase(v)) ? null : v;
    }

    private String firstNonNull(String... values) {
        for (String v : values) if (v != null) return v;
        return null;
    }

    private Double parseDouble(String v) {
        if (v == null || v.isBlank()) return null;
        try { return Double.parseDouble(v.trim()); } catch (NumberFormatException e) { return null; }
    }

    private String escape(String s) {
        return s.replace("\"", "\\\"").replace("\\", "\\\\");
    }

    private boolean hasFoursquareKey() {
        return foursquareApiKey != null && !foursquareApiKey.isBlank();
    }

    private RestaurantScrapedInfoResponse empty(String name, String city) {
        return new RestaurantScrapedInfoResponse(
                name, null, null, null, null, null,
                null, null, null, null, city, null, null, null, null);
    }

    // =========================================================================
    // INNER RECORDS
    // =========================================================================

    private record FoursquarePlace(
            String fsqPlaceId,
            Integer price,
            Double  rating,
            String  tel,
            String  website,
            String  hours
    ) {}

    /**
     * Datos completos de un lugar de Foursquare (endpoint /places/{id}).
     * Expuesto como public para uso desde ScrapingService si se necesita
     * detalle de un restaurante individual.
     */
    public record FoursquareDetails(
            String              fsqPlaceId,
            String              name,
            Integer             price,
            Double              rating,
            String              tel,
            String              website,
            String              hours,
            String              description,
            List<String>        photos,
            List<String>        tastes,
            Map<String, Boolean> attributes
    ) {}
}
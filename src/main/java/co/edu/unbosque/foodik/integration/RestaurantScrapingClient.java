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

    // ── Overpass ───────────────────────────────────────────────────────────
    private static final String OVERPASS_URL        = "https://overpass-api.de/api/interpreter";
    private static final String OVERPASS_URL_MIRROR = "https://lz4.overpass-api.de/api/interpreter";

    // ── Nominatim fallback ─────────────────────────────────────────────────
    private static final String NOMINATIM_URL = "https://nominatim.openstreetmap.org/search";

    // ── Foursquare ─────────────────────────────────────────────────────────
    private static final String FSQ_BASE          = "https://places-api.foursquare.com";
    private static final String FSQ_SEARCH        = FSQ_BASE + "/places/search";
    private static final String FSQ_DETAILS       = FSQ_BASE + "/places/";
    private static final String FSQ_API_VERSION   = "2025-06-17";
    private static final String FSQ_FOOD_CATEGORY = "13000";

    // ── Google Places API (New) ────────────────────────────────────────────
    private static final String GOOGLE_NEARBY_URL = "https://places.googleapis.com/v1/places:searchNearby";
    private static final String GOOGLE_FIELDS     =
            "places.displayName,places.formattedAddress,places.rating," +
            "places.priceLevel,places.internationalPhoneNumber,places.websiteUri," +
            "places.regularOpeningHours,places.editorialSummary,places.types," +
            "places.location,places.id";

    // ── Umbral de distancia para match por coordenadas ─────────────────────
    // scrapeNearby usa 80m (varios restaurantes en zona → más estricto)
    // scrapeRestaurantInfo usa 250m (búsqueda puntual → más flexible)
    private static final double NEARBY_THRESHOLD_M  = 80.0;
    private static final double SINGLE_THRESHOLD_M  = 250.0;

    // ── Cache Foursquare ───────────────────────────────────────────────────
    private final Map<String, FoursquarePlace> fsqCache = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${application.scraping.user-agent:foodik-app/1.0 (universidad proyecto)}")
    private String userAgent;

    @Value("${application.scraping.timeout-ms:15000}")
    private int timeoutMs;

    @Value("${application.scraping.foursquare-api-key:}")
    private String foursquareApiKey;

    @Value("${application.scraping.google-api-key:}")
    private String googleApiKey;

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Busca restaurantes cercanos con estrategia multi-fuente.
     * 1. Overpass  → fuente principal OSM
     * 2. Nominatim → fallback si Overpass da pocos resultados
     * 3. Google    → enriquecimiento prioritario (rating, precio, horarios, teléfono)
     * 4. Foursquare→ enriquecimiento secundario si no hay Google
     */
    public List<RestaurantScrapedInfoResponse> scrapeNearby(double lat, double lng, int radiusM) {
        log.info("scrapeNearby: lat={}, lng={}, radius={}m", lat, lng, radiusM);

        List<RestaurantScrapedInfoResponse> results = queryOverpass(lat, lng, radiusM);

        if (results.size() < 3) {
            log.warn("Overpass devolvió {} resultados — complementando con Nominatim", results.size());
            Set<String> seen = new HashSet<>();
            results.forEach(r -> { if (r.name() != null) seen.add(normalizeName(r.name())); });
            for (RestaurantScrapedInfoResponse r : queryNominatim(lat, lng, radiusM)) {
                if (r.name() != null && seen.add(normalizeName(r.name()))) results.add(r);
            }
        }

        if (hasGoogleKey() && !results.isEmpty()) {
            log.info("Enriqueciendo con Google Places API...");
            results = enrichWithGoogle(results, lat, lng, radiusM, NEARBY_THRESHOLD_M);
        }

        if (!hasGoogleKey() && hasFoursquareKey() && !results.isEmpty()) {
            log.info("Enriqueciendo con Foursquare Places API...");
            results = enrichWithFoursquare(results, lat, lng, radiusM);
        }

        log.info("scrapeNearby total: {} restaurantes", results.size());
        return results;
    }

    /**
     * Obtiene información de un restaurante específico por nombre y ciudad.
     * 1. Overpass  → búsqueda por nombre en la ciudad
     * 2. Nominatim → fallback si Overpass falla
     * 3. Google    → enriquecimiento con umbral ampliado (250m) para búsqueda puntual
     */
    public RestaurantScrapedInfoResponse scrapeRestaurantInfo(String name, String city) {
        log.info("scrapeRestaurantInfo: '{}' en '{}'", name, city);

        RestaurantScrapedInfoResponse result = null;

        // 1. Overpass
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
            if (!r.isEmpty()) result = r.get(0);
        } catch (Exception e) {
            log.warn("Overpass falló para '{}': {}", name, e.getMessage());
        }

        // 2. Nominatim si Overpass no encontró nada
        if (result == null) {
            result = queryNominatimSingle(name, city);
        }

        // 3. Google con umbral más amplio para búsqueda puntual
        if (hasGoogleKey() && result != null
                && result.latitude() != null && result.longitude() != null) {
            log.info("Enriqueciendo '{}' con Google Places...", name);
            List<RestaurantScrapedInfoResponse> enriched = enrichWithGoogle(
                    List.of(result),
                    result.latitude(),
                    result.longitude(),
                    500,
                    SINGLE_THRESHOLD_M   // 250m — más flexible para búsqueda individual
            );
            if (!enriched.isEmpty()) result = enriched.get(0);
        }

        return result;
    }

    // =========================================================================
    // OVERPASS
    // =========================================================================

    private List<RestaurantScrapedInfoResponse> queryOverpass(double lat, double lng, int radiusM) {
        // Locale.US para punto decimal en vez de coma (locale colombiano)
        String query = String.format(Locale.US, """
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
                "Accept",          "application/json",
                "Accept-Language", "es-CO,es;q=0.9"
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

            Double elLat  = el.has("lat") ? el.path("lat").asDouble() : null;
            Double elLon  = el.has("lon") ? el.path("lon").asDouble() : null;
            String phone  = firstNonNull(nullIfEmpty(tags.path("phone").asText(null)),
                                         nullIfEmpty(tags.path("contact:phone").asText(null)));
            String website= firstNonNull(nullIfEmpty(tags.path("website").asText(null)),
                                         nullIfEmpty(tags.path("contact:website").asText(null)));
            String priceRange = extractOsmPrice(tags);
            String[] bounds   = normalizeCOP(priceRange);
            String osmId      = el.path("id").asText(null);
            String osmType    = el.path("type").asText("node");

            results.add(new RestaurantScrapedInfoResponse(
                    name,
                    nullIfEmpty(tags.path("description").asText(null)),
                    priceRange, bounds[0], bounds[1], null,
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
                JsonNode arr = objectMapper.readTree(httpGet(url, Map.of(
                        "User-Agent",      userAgent,
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

    /**
     * Nominatim mejorado para búsqueda individual.
     * Estrategia en cascada: nombre+ciudad → solo nombre → vacío.
     * Prefiere resultados de clase amenity/tourism.
     */
    private RestaurantScrapedInfoResponse queryNominatimSingle(String name, String city) {
        String encodedQuery = URLEncoder.encode(name + " " + city, StandardCharsets.UTF_8);
        String encodedName  = URLEncoder.encode(name, StandardCharsets.UTF_8);

        String[] attempts = {
                "?q=" + encodedQuery + "&format=json&extratags=1&limit=5&countrycodes=co",
                "?q=" + encodedName  + "&format=json&extratags=1&limit=5&countrycodes=co"
        };

        for (String attempt : attempts) {
            try {
                String url = NOMINATIM_URL + attempt;
                log.debug("Nominatim single attempt: {}", url);
                JsonNode arr = objectMapper.readTree(
                        httpGet(url, Map.of("User-Agent", userAgent,
                                "Accept-Language", "es-CO,es;q=0.9")));
                if (!arr.isArray() || arr.isEmpty()) continue;

                // Preferir resultados de amenity / tourism
                for (JsonNode node : arr) {
                    String cls = node.path("class").asText("");
                    if ("amenity".equals(cls) || "tourism".equals(cls)) {
                        RestaurantScrapedInfoResponse r = parseNominatimNode(node);
                        if (r != null) {
                            log.info("Nominatim single encontró '{}' (class={}) con intento: {}",
                                    r.name(), cls, attempt.split("&")[0]);
                            return r;
                        }
                    }
                }
                // Último recurso: primer resultado sin filtro
                RestaurantScrapedInfoResponse r = parseNominatimNode(arr.get(0));
                if (r != null) {
                    log.info("Nominatim single encontró '{}' (sin filtro amenity) con intento: {}",
                            r.name(), attempt.split("&")[0]);
                    return r;
                }
            } catch (Exception e) {
                log.warn("Nominatim single falló en intento '{}': {}", attempt.split("&")[0], e.getMessage());
            }
        }

        log.warn("Nominatim no encontró '{}' en '{}' — devolviendo respuesta vacía", name, city);
        return empty(name, city);
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
    // GOOGLE PLACES API (New)
    //   Endpoint : https://places.googleapis.com/v1/places:searchNearby
    //   Auth     : X-Goog-Api-Key: <KEY>
    //   Docs     : https://developers.google.com/maps/documentation/places/web-service/nearby-search
    //
    // FIX PRINCIPAL: el parámetro thresholdMeters permite usar un umbral diferente
    // para scrapeNearby (80m, muchos restaurantes) vs scrapeRestaurantInfo (250m,
    // búsqueda puntual donde el match debe ser más flexible).
    // =========================================================================

    private List<RestaurantScrapedInfoResponse> enrichWithGoogle(
            List<RestaurantScrapedInfoResponse> results,
            double lat, double lng, int radiusM,
            double thresholdMeters) {
        try {
            // Body con concatenación de strings — evita coma decimal por locale colombiano
            String requestBody = "{"
                    + "\"includedTypes\": [\"restaurant\", \"cafe\", \"fast_food_restaurant\", \"bar\"],"
                    + "\"maxResultCount\": 20,"
                    + "\"locationRestriction\": {"
                    +   "\"circle\": {"
                    +     "\"center\": { \"latitude\": " + lat + ", \"longitude\": " + lng + " },"
                    +     "\"radius\": " + Math.min(radiusM, 50000)
                    +   "}"
                    + "}"
                    + "}";

            log.debug("Google Places Nearby Search: lat={}, lng={}, radius={}, threshold={}m",
                    lat, lng, radiusM, thresholdMeters);

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(timeoutMs))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(GOOGLE_NEARBY_URL))
                    .header("Content-Type",     "application/json")
                    .header("X-Goog-Api-Key",   googleApiKey)
                    .header("X-Goog-FieldMask", GOOGLE_FIELDS)
                    .header("User-Agent",        userAgent)
                    .timeout(Duration.ofMillis(timeoutMs + 5000L))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            log.debug("Google Places → HTTP {}", resp.statusCode());

            if (!handleGoogleStatus(resp.statusCode(), resp.body())) return results;

            // Índice por nombre normalizado + lista completa para match por coordenadas
            Map<String, JsonNode> googleIndex = new LinkedHashMap<>();
            List<JsonNode> googleList = new ArrayList<>();
            JsonNode places = objectMapper.readTree(resp.body()).path("places");
            if (places.isArray()) {
                for (JsonNode p : places) {
                    String n = p.path("displayName").path("text").asText(null);
                    if (n != null) googleIndex.put(normalizeName(n), p);
                    googleList.add(p);
                }
            }
            log.info("Google Places indexó {} lugares para enriquecimiento", googleIndex.size());

            // Merge: match por nombre → luego por coordenadas con thresholdMeters
            List<RestaurantScrapedInfoResponse> enriched = new ArrayList<>();
            int matchCount = 0;

            for (RestaurantScrapedInfoResponse r : results) {

                // Intento 1: nombre normalizado exacto
                JsonNode g = r.name() != null ? googleIndex.get(normalizeName(r.name())) : null;

                // Intento 2: coordenadas dentro del umbral configurable
                // FIX: se eliminó la validación de nombre en el match por coordenadas
                // porque los nombres de OSM/Nominatim y Google difieren frecuentemente.
                if (g == null && r.latitude() != null && r.longitude() != null) {
                    JsonNode closest = null;
                    double minDist   = Double.MAX_VALUE;
                    for (JsonNode candidate : googleList) {
                        double gLat = candidate.path("location").path("latitude").asDouble(Double.NaN);
                        double gLng = candidate.path("location").path("longitude").asDouble(Double.NaN);
                        if (Double.isNaN(gLat) || Double.isNaN(gLng)) continue;
                        double dist = haversineMeters(r.latitude(), r.longitude(), gLat, gLng);
                        if (dist < minDist) { minDist = dist; closest = candidate; }
                    }
                    if (closest != null && minDist <= thresholdMeters) {
                        g = closest;
                        log.debug("Google match por coordenadas: '{}' ↔ '{}' ({:.1f}m)",
                                r.name(),
                                g.path("displayName").path("text").asText("?"),
                                minDist);
                    }
                }

                if (g == null) { enriched.add(r); continue; }
                matchCount++;

                // Extraer campos de Google, usar datos OSM como fallback
                String priceSymbol = googlePriceToSymbol(g.path("priceLevel").asText(null));
                String[] bounds    = normalizeCOP(priceSymbol);
                String rating      = g.path("rating").isMissingNode() ? r.rating()
                        : String.format(Locale.US, "%.1f / 5.0", g.path("rating").asDouble());
                String phone       = firstNonNull(nullIfEmpty(g.path("internationalPhoneNumber").asText(null)), r.phone());
                String website     = firstNonNull(nullIfEmpty(g.path("websiteUri").asText(null)), r.website());
                String address     = firstNonNull(nullIfEmpty(g.path("formattedAddress").asText(null)), r.address());
                String description = firstNonNull(nullIfEmpty(g.path("editorialSummary").path("text").asText(null)), r.description());
                String hours       = firstNonNull(extractGoogleHours(g.path("regularOpeningHours")), r.openingHours());
                String cuisine     = firstNonNull(extractGoogleCuisine(g.path("types")), r.cuisine());

                enriched.add(new RestaurantScrapedInfoResponse(
                        r.name(), description,
                        priceSymbol != null ? priceSymbol : r.priceRange(),
                        bounds[0]   != null ? bounds[0]   : r.priceMin(),
                        bounds[1]   != null ? bounds[1]   : r.priceMax(),
                        rating, cuisine, hours, phone, website, address,
                        r.osmId(), r.latitude(), r.longitude(), r.sourceUrl()
                ));
            }
            log.info("Google Places enriqueció {} de {} restaurantes", matchCount, results.size());
            return enriched;

        } catch (Exception e) {
            log.error("Google Places enriquecimiento falló: {} — devolviendo sin enriquecer", e.getMessage());
            return results;
        }
    }

    // ── Google helpers ─────────────────────────────────────────────────────

    private boolean handleGoogleStatus(int status, String body) {
        return switch (status) {
            case 200 -> true;
            case 400 -> { log.error("Google Places 400 — request malformado. Body: {}", body); yield false; }
            case 401, 403 -> {
                log.error("Google Places {} — API key inválida o sin permisos de Places API (New). " +
                        "Verifica en https://console.cloud.google.com", status);
                yield false;
            }
            case 429 -> {
                log.warn("Google Places 429 — cuota diaria agotada. " +
                        "Revisa https://console.cloud.google.com/apis/dashboard");
                yield false;
            }
            default -> { log.error("Google Places HTTP {} — body: {}", status, body); yield false; }
        };
    }

    private String googlePriceToSymbol(String priceLevel) {
        if (priceLevel == null || priceLevel.isBlank()) return null;
        return switch (priceLevel) {
            case "PRICE_LEVEL_INEXPENSIVE"    -> "$";
            case "PRICE_LEVEL_MODERATE"       -> "$$";
            case "PRICE_LEVEL_EXPENSIVE"      -> "$$$";
            case "PRICE_LEVEL_VERY_EXPENSIVE" -> "$$$$";
            default -> null;
        };
    }

    private String extractGoogleHours(JsonNode hours) {
        if (hours == null || hours.isMissingNode()) return null;
        JsonNode desc = hours.path("weekdayDescriptions");
        if (!desc.isArray() || desc.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (JsonNode d : desc) { if (!sb.isEmpty()) sb.append(" | "); sb.append(d.asText()); }
        return sb.isEmpty() ? null : sb.toString();
    }

    private String extractGoogleCuisine(JsonNode types) {
        if (types == null || !types.isArray()) return null;
        List<String> skip = List.of("point_of_interest", "establishment", "food", "store",
                "restaurant", "cafe", "bar");
        for (JsonNode t : types) {
            String val = t.asText("");
            if (!skip.contains(val))
                return val.replace("_restaurant", "").replace("_", " ").trim();
        }
        return null;
    }

    private boolean hasGoogleKey() {
        return googleApiKey != null && !googleApiKey.isBlank();
    }

    /** Distancia en metros entre dos coordenadas (fórmula de Haversine). */
    private double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6_371_000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    // =========================================================================
    // FOURSQUARE
    // =========================================================================

    private List<RestaurantScrapedInfoResponse> enrichWithFoursquare(
            List<RestaurantScrapedInfoResponse> results, double lat, double lng, int radiusM) {
        try {
            String searchUrl = FSQ_SEARCH
                    + "?ll=" + lat + "," + lng
                    + "&radius=" + Math.min(radiusM, 100_000)
                    + "&categories=" + FSQ_FOOD_CATEGORY
                    + "&limit=50"
                    + "&fields=fsq_place_id,name,price,rating,tel,website,hours,location";

            log.debug("Foursquare search: {}", searchUrl);
            HttpResponse<String> searchResp = foursquareGet(searchUrl);
            if (!handleFoursquareStatus(searchResp.statusCode(), "search")) return results;

            Map<String, FoursquarePlace> index = new LinkedHashMap<>();
            JsonNode places = objectMapper.readTree(searchResp.body()).path("results");
            if (places.isArray()) {
                for (JsonNode p : places) {
                    String n = p.path("name").asText(null);
                    if (n == null) continue;
                    String normalized = normalizeName(n);
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

            List<RestaurantScrapedInfoResponse> enriched = new ArrayList<>();
            for (RestaurantScrapedInfoResponse r : results) {
                FoursquarePlace fsq = r.name() != null ? index.get(normalizeName(r.name())) : null;
                if (fsq == null) { enriched.add(r); continue; }
                String priceSymbol = fsq.price() != null ? priceToSymbol(fsq.price()) : r.priceRange();
                String[] bounds    = normalizeCOP(priceSymbol);
                String ratingStr   = fsq.rating() != null
                        ? String.format(Locale.US, "%.1f / 10.0", fsq.rating()) : r.rating();
                enriched.add(new RestaurantScrapedInfoResponse(
                        r.name(), r.description(), priceSymbol, bounds[0], bounds[1],
                        ratingStr, r.cuisine(),
                        fsq.hours()   != null ? fsq.hours()   : r.openingHours(),
                        fsq.tel()     != null ? fsq.tel()     : r.phone(),
                        fsq.website() != null ? fsq.website() : r.website(),
                        r.address(), r.osmId(), r.latitude(), r.longitude(), r.sourceUrl()
                ));
            }
            return enriched;
        } catch (Exception e) {
            log.error("Foursquare enriquecimiento falló: {} — devolviendo sin enriquecer", e.getMessage());
            return results;
        }
    }

    public FoursquareDetails getFoursquareDetails(String fsqPlaceId) throws Exception {
        String url = FSQ_DETAILS + fsqPlaceId
                + "?fields=fsq_place_id,name,price,rating,tel,website,hours,location,"
                + "photos,tastes,description,attributes";
        log.debug("Foursquare details: {}", url);
        HttpResponse<String> resp = foursquareGet(url);
        if (!handleFoursquareStatus(resp.statusCode(), "details/" + fsqPlaceId)) return null;
        JsonNode p = objectMapper.readTree(resp.body());
        return new FoursquareDetails(
                p.path("fsq_place_id").asText(null), p.path("name").asText(null),
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

    private HttpResponse<String> foursquareGet(String url) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization",        "Bearer " + foursquareApiKey)
                .header("X-Places-Api-Version", FSQ_API_VERSION)
                .header("Accept",               "application/json")
                .header("User-Agent",           userAgent)
                .timeout(Duration.ofMillis(timeoutMs + 5000L))
                .GET().build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private boolean handleFoursquareStatus(int status, String endpoint) {
        return switch (status) {
            case 200 -> true;
            case 401 -> { log.error("Foursquare 401 en '{}' — API key inválida", endpoint); yield false; }
            case 403 -> { log.error("Foursquare 403 en '{}' — sin permisos", endpoint); yield false; }
            case 404 -> { log.warn("Foursquare 404 en '{}' — no encontrado", endpoint); yield false; }
            case 410 -> { log.error("Foursquare 410 en '{}' — endpoint deprecado", endpoint); yield false; }
            case 429 -> {
                log.warn("Foursquare 429 en '{}' — rate limit alcanzado. " +
                        "Espera 1 hora o verifica tu plan en https://developer.foursquare.com", endpoint);
                yield false;
            }
            default -> { log.error("Foursquare HTTP {} en '{}'", status, endpoint); yield false; }
        };
    }

    private String extractFsqHours(JsonNode hours) {
        if (hours == null || hours.isMissingNode()) return null;
        return nullIfEmpty(hours.path("display").asText(null));
    }

    private List<String> extractFsqPhotos(JsonNode photos) {
        if (photos == null || !photos.isArray()) return null;
        List<String> urls = new ArrayList<>();
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
        if (attrs.has("wifi") && !attrs.path("wifi").isMissingNode())
            map.put("wifi", !"no".equalsIgnoreCase(attrs.path("wifi").asText("")));
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
            String  fsqPlaceId,
            Integer price,
            Double  rating,
            String  tel,
            String  website,
            String  hours
    ) {}

    public record FoursquareDetails(
            String               fsqPlaceId,
            String               name,
            Integer              price,
            Double               rating,
            String               tel,
            String               website,
            String               hours,
            String               description,
            List<String>         photos,
            List<String>         tastes,
            Map<String, Boolean> attributes
    ) {}
}

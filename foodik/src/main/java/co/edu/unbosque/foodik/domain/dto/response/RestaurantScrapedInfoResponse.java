package co.edu.unbosque.foodik.domain.dto.response;

/**
 * Respuesta enriquecida de scraping.
 *
 * priceRange  — símbolo normalizado: "$" (económico), "$$" (moderado), "$$$" (caro), "$$$$" (lujoso)
 * priceMin    — precio mínimo estimado en COP (puede ser null)
 * priceMax    — precio máximo estimado en COP (puede ser null)
 * cuisine     — tipo de cocina según OSM (italian, colombian, etc.)
 * openingHours — horario según OSM
 * phone       — teléfono si está en OSM
 * website     — sitio web si está en OSM
 * osmId       — ID del nodo/way en OpenStreetMap
 * latitude    — latitud exacta del restaurante
 * longitude   — longitud exacta del restaurante
 */
public record RestaurantScrapedInfoResponse(
        String name,
        String description,
        String priceRange,
        String priceMin,
        String priceMax,
        String rating,
        String cuisine,
        String openingHours,
        String phone,
        String website,
        String address,
        String osmId,
        Double latitude,
        Double longitude,
        String sourceUrl
) {}

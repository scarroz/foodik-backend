package co.edu.unbosque.foodik.service.impl;
import co.edu.unbosque.foodik.domain.dto.request.*;
import co.edu.unbosque.foodik.domain.dto.response.RestaurantResponse;
import co.edu.unbosque.foodik.domain.entity.Restaurant;
import co.edu.unbosque.foodik.domain.entity.User;
import co.edu.unbosque.foodik.domain.enums.RestaurantCategory;
import co.edu.unbosque.foodik.exception.ResourceNotFoundException;
import co.edu.unbosque.foodik.exception.UnauthorizedException;
import co.edu.unbosque.foodik.repository.RestaurantRepository;
import co.edu.unbosque.foodik.repository.UserRepository;
import co.edu.unbosque.foodik.service.RestaurantService;
import co.edu.unbosque.foodik.util.HaversineUtil;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;
@Service
public class RestaurantServiceImpl implements RestaurantService {
    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final HaversineUtil haversineUtil;
    public RestaurantServiceImpl(RestaurantRepository r, UserRepository u, HaversineUtil h) {
        this.restaurantRepository = r; this.userRepository = u; this.haversineUtil = h;
    }
    @Override @Transactional(readOnly = true)
    public Page<RestaurantResponse> findNearby(double lat, double lng, double radiusKm, RestaurantCategory category, Pageable pageable) {
        double[] bbox = haversineUtil.boundingBox(lat, lng, radiusKm);
        List<Restaurant> candidates = restaurantRepository.findInBoundingBox(bbox[0], bbox[1], bbox[2], bbox[3], category);
        List<RestaurantResponse> filtered = candidates.stream()
                .map(r -> { double d = haversineUtil.calculateDistance(lat, lng, r.getLatitude(), r.getLongitude());
                    return d <= radiusKm ? toResponse(r, Math.round(d * 100.0) / 100.0) : null; })
                .filter(r -> r != null)
                .sorted((a, b) -> Double.compare(a.distanceKm(), b.distanceKm()))
                .toList();
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        List<RestaurantResponse> page = start >= filtered.size() ? List.of() : filtered.subList(start, end);
        return new PageImpl<>(page, pageable, filtered.size());
    }
    @Override @Transactional(readOnly = true)
    public RestaurantResponse findById(UUID id) {
        return toResponse(restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", id)), null);
    }
    @Override @Transactional
    public RestaurantResponse create(CreateRestaurantRequest request, String ownerEmail) {
        User owner = userRepository.findByEmail(ownerEmail).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Restaurant r = new Restaurant();
        r.setName(request.name()); r.setDescription(request.description()); r.setAddress(request.address());
        r.setLatitude(request.latitude()); r.setLongitude(request.longitude());
        r.setPhone(request.phone()); r.setEmail(request.email()); r.setImageUrl(request.imageUrl());
        r.setCategory(request.category()); r.setOpeningHours(request.openingHours()); r.setOwner(owner);
        return toResponse(restaurantRepository.save(r), null);
    }
    @Override @Transactional
    public RestaurantResponse update(UUID id, UpdateRestaurantRequest request, String ownerEmail) {
        Restaurant r = getOwned(id, ownerEmail);
        if (request.name() != null) r.setName(request.name());
        if (request.description() != null) r.setDescription(request.description());
        if (request.address() != null) r.setAddress(request.address());
        if (request.latitude() != null) r.setLatitude(request.latitude());
        if (request.longitude() != null) r.setLongitude(request.longitude());
        if (request.phone() != null) r.setPhone(request.phone());
        if (request.email() != null) r.setEmail(request.email());
        if (request.imageUrl() != null) r.setImageUrl(request.imageUrl());
        if (request.category() != null) r.setCategory(request.category());
        if (request.openingHours() != null) r.setOpeningHours(request.openingHours());
        if (request.isActive() != null) r.setIsActive(request.isActive());
        return toResponse(restaurantRepository.save(r), null);
    }
    @Override @Transactional
    public void delete(UUID id, String ownerEmail) {
        Restaurant r = getOwned(id, ownerEmail);
        r.setIsActive(false); restaurantRepository.save(r);
    }
    private Restaurant getOwned(UUID id, String email) {
        Restaurant r = restaurantRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Restaurant", id));
        if (!r.getOwner().getEmail().equals(email)) throw new UnauthorizedException("You don't own this restaurant");
        return r;
    }
    private RestaurantResponse toResponse(Restaurant r, Double dist) {
        return new RestaurantResponse(r.getId(), r.getName(), r.getDescription(), r.getAddress(),
                r.getLatitude(), r.getLongitude(), r.getPhone(), r.getEmail(), r.getImageUrl(),
                r.getCategory(), r.getOpeningHours(), r.getIsActive(), dist);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RestaurantResponse> getByOwner(String email) {
        User owner = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return restaurantRepository.findByOwner(owner).stream()
                .map(r -> toResponse(r, null))
                .toList();
    }
}

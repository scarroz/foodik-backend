package co.edu.unbosque.foodik.service.impl;
import co.edu.unbosque.foodik.domain.dto.request.CreateDiscountRequest;
import co.edu.unbosque.foodik.domain.dto.response.DiscountResponse;
import co.edu.unbosque.foodik.domain.entity.Discount;
import co.edu.unbosque.foodik.domain.entity.Restaurant;
import co.edu.unbosque.foodik.exception.ResourceNotFoundException;
import co.edu.unbosque.foodik.exception.UnauthorizedException;
import co.edu.unbosque.foodik.repository.DiscountRepository;
import co.edu.unbosque.foodik.repository.RestaurantRepository;
import co.edu.unbosque.foodik.service.DiscountService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
@Service
public class DiscountServiceImpl implements DiscountService {
    private final DiscountRepository discountRepository;
    private final RestaurantRepository restaurantRepository;
    public DiscountServiceImpl(DiscountRepository d, RestaurantRepository r) { this.discountRepository = d; this.restaurantRepository = r; }
    @Override @Transactional(readOnly = true)
    public List<DiscountResponse> findActiveByRestaurant(UUID restaurantId) {
        return discountRepository.findActiveByRestaurant(restaurantId, LocalDateTime.now()).stream().map(this::toResponse).toList();
    }
    @Override @Transactional
    public DiscountResponse create(UUID restaurantId, CreateDiscountRequest request, String adminEmail) {
        Restaurant restaurant = getOwned(restaurantId, adminEmail);
        Discount d = new Discount();
        d.setRestaurant(restaurant); d.setTitle(request.title()); d.setDescription(request.description());
        d.setDiscountType(request.discountType()); d.setDiscountValue(request.discountValue());
        d.setValidFrom(request.validFrom()); d.setValidTo(request.validTo());
        return toResponse(discountRepository.save(d));
    }
    @Override @Transactional
    public void delete(UUID discountId, String adminEmail) {
        Discount d = discountRepository.findById(discountId).orElseThrow(() -> new ResourceNotFoundException("Discount", discountId));
        if (!d.getRestaurant().getOwner().getEmail().equals(adminEmail)) throw new UnauthorizedException("Access denied");
        d.setIsActive(false); discountRepository.save(d);
    }
    private Restaurant getOwned(UUID id, String email) {
        Restaurant r = restaurantRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Restaurant", id));
        if (!r.getOwner().getEmail().equals(email)) throw new UnauthorizedException("Access denied");
        return r;
    }
    private DiscountResponse toResponse(Discount d) {
        return new DiscountResponse(d.getId(), d.getRestaurant().getId(), d.getTitle(),
                d.getDescription(), d.getDiscountType(), d.getDiscountValue(),
                d.getValidFrom(), d.getValidTo(), d.getIsActive());
    }
}
